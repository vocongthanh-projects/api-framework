package vn.ots.qc.api.http.jdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.auth.AuthMaterial;
import vn.ots.qc.api.core.auth.CredentialManager;
import vn.ots.qc.api.core.config.FrameworkConfig;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.api.core.http.ApiRequest;
import vn.ots.qc.api.core.http.MultipartPart;
import vn.ots.qc.api.core.trace.ExchangeTrace;

public class JdkAsyncApiClientTest {
  private HttpServer server;
  private final AtomicInteger retryCalls = new AtomicInteger();
  private final AtomicReference<String> capturedRequest = new AtomicReference<>("");

  @BeforeMethod
  public void startServer() throws IOException {
    retryCalls.set(0);
    capturedRequest.set("");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/temporary", this::temporary);
    server.createContext("/session/start", this::sessionStart);
    server.createContext("/session/check", this::sessionCheck);
    server.createContext("/protected", this::protectedEndpoint);
    server.createContext("/echo", this::echo);
    server.start();
  }

  @AfterMethod(alwaysRun = true)
  public void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  public void executesAsyncAndRetriesSafeGet() {
    List<ExchangeTrace> traces = new ArrayList<>();
    JdkAsyncApiClient client =
        new JdkAsyncApiClient(config(), new CredentialManager(Map.of()), traces::add);

    var response = client.executeAsync(ApiRequest.get("test", "/temporary").build()).join();

    Assert.assertEquals(response.statusCode(), 200);
    Assert.assertEquals(response.attempts(), 2);
    Assert.assertEquals(retryCalls.get(), 2);
    Assert.assertEquals(traces.stream().map(ExchangeTrace::statusCode).toList(), List.of(503, 200));
  }

  @Test
  public void storesAndResendsCookiesAndRedactsTrace() {
    List<ExchangeTrace> traces = new ArrayList<>();
    JdkAsyncApiClient client =
        new JdkAsyncApiClient(config(), new CredentialManager(Map.of()), traces::add);

    Assert.assertEquals(
        client.executeAsync(ApiRequest.get("test", "/session/start").build()).join().statusCode(),
        200);
    Assert.assertEquals(
        client.executeAsync(ApiRequest.get("test", "/session/check").build()).join().statusCode(),
        200);
    Assert.assertEquals(traces.get(1).requestHeaders().get("Cookie"), "<redacted>");

    client.clearSession("test", null);
    Assert.assertEquals(
        client.executeAsync(ApiRequest.get("test", "/session/check").build()).join().statusCode(),
        401);
  }

  @Test
  public void refreshesCredentialsOnceAfterUnauthorized() {
    AtomicInteger authentications = new AtomicInteger();
    FrameworkConfig config = authenticatedConfig();
    CredentialManager credentials =
        new CredentialManager(
            Map.of(
                "bearer",
                actor ->
                    AuthMaterial.bearer(
                        "token-" + authentications.incrementAndGet(),
                        Instant.now().plusSeconds(300))));
    JdkAsyncApiClient client = new JdkAsyncApiClient(config, credentials, ignored -> {});

    var response = client.executeAsync(ApiRequest.get("test", "/protected").build()).join();

    Assert.assertEquals(response.statusCode(), 200);
    Assert.assertEquals(response.attempts(), 2);
    Assert.assertEquals(authentications.get(), 2);
  }

  @Test
  public void encodesPathQueryJsonFormAndMultipartRequests() {
    JdkAsyncApiClient client =
        new JdkAsyncApiClient(config(), new CredentialManager(Map.of()), ignored -> {});

    var jsonResponse =
        client
            .executeAsync(
                ApiRequest.post("test", "/echo/{item}")
                    .pathParam("item", "a b")
                    .queryParam("tag", List.of("one", "two words"))
                    .body(Map.of("ok", true))
                    .build())
            .join();
    Assert.assertEquals(jsonResponse.statusCode(), 200);
    Assert.assertTrue(capturedRequest.get().contains("/echo/a%20b?tag=one&tag=two+words"));
    Assert.assertTrue(capturedRequest.get().contains("application/json"));
    Assert.assertTrue(capturedRequest.get().endsWith("{\"ok\":true}"));

    client
        .executeAsync(
            ApiRequest.post("test", "/echo")
                .formParam("name", "hello world")
                .formParam("active", true)
                .build())
        .join();
    Assert.assertTrue(capturedRequest.get().contains("application/x-www-form-urlencoded"));
    Assert.assertTrue(capturedRequest.get().endsWith("name=hello+world&active=true"));

    client
        .executeAsync(
            ApiRequest.post("test", "/echo")
                .multipart(MultipartPart.text("description", "sample"))
                .multipart(
                    MultipartPart.bytes(
                        "upload", "sample.bin", new byte[] {1, 2}, "application/octet-stream"))
                .build())
        .join();
    Assert.assertTrue(
        capturedRequest.get().contains("multipart/form-data; boundary=api-framework-"));
    Assert.assertTrue(capturedRequest.get().contains("name=\"description\""));
    Assert.assertTrue(capturedRequest.get().contains("sample"));
    Assert.assertTrue(capturedRequest.get().contains("filename=\"sample.bin\""));
  }

  private FrameworkConfig config() {
    return FrameworkConfig.builder()
        .service(ServiceConfig.builder("test", baseUrl()).build())
        .build();
  }

  private FrameworkConfig authenticatedConfig() {
    return FrameworkConfig.builder()
        .service(ServiceConfig.builder("test", baseUrl()).authProfile("bearer").build())
        .build();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private void temporary(HttpExchange exchange) throws IOException {
    int status = retryCalls.incrementAndGet() == 1 ? 503 : 200;
    respond(exchange, status);
  }

  private void sessionStart(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().add("Set-Cookie", "session-id=secret-value; Path=/; HttpOnly");
    respond(exchange, 200);
  }

  private void sessionCheck(HttpExchange exchange) throws IOException {
    String cookie = exchange.getRequestHeaders().getFirst("Cookie");
    respond(exchange, cookie != null && cookie.contains("session-id=secret-value") ? 200 : 401);
  }

  private void protectedEndpoint(HttpExchange exchange) throws IOException {
    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
    respond(exchange, "Bearer token-2".equals(authorization) ? 200 : 401);
  }

  private void echo(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    capturedRequest.set(
        exchange.getRequestURI().toASCIIString()
            + "\n"
            + exchange.getRequestHeaders().getFirst("Content-Type")
            + "\n"
            + body);
    respond(exchange, 200);
  }

  private void respond(HttpExchange exchange, int status) throws IOException {
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
