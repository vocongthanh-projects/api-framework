package vn.ots.qc.api.restassured;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.auth.AuthMaterial;
import vn.ots.qc.api.core.auth.CredentialManager;
import vn.ots.qc.api.core.config.FrameworkConfig;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.api.core.http.ApiRequest;
import vn.ots.qc.api.core.http.ApiResponse;
import vn.ots.qc.api.core.http.MultipartPart;
import vn.ots.qc.api.core.retry.RetryPolicy;
import vn.ots.qc.api.core.security.Redactor;
import vn.ots.qc.api.core.trace.ExchangeTrace;

public class RestAssuredApiClientRetryTest {
  private HttpServer server;
  private final AtomicInteger calls = new AtomicInteger();

  @BeforeMethod
  public void startServer() throws IOException {
    calls.set(0);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/temporary", this::handleTemporaryFailure);
    server.createContext("/protected", this::handleProtected);
    server.createContext("/upload", this::handleUpload);
    server.createContext("/binary", this::handleBinary);
    server.start();
  }

  @AfterMethod(alwaysRun = true)
  public void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  public void retriesGetWithoutActuallySleeping() {
    List<ExchangeTrace> traces = new ArrayList<>();
    AtomicInteger sleeps = new AtomicInteger();
    FrameworkConfig config = config();
    RestAssuredApiClient client =
        new RestAssuredApiClient(
            config,
            new CredentialManager(Map.of()),
            new RetryPolicy(config.retry()),
            new Redactor(),
            traces::add,
            Clock.systemUTC(),
            ignored -> sleeps.incrementAndGet());

    ApiResponse response = client.execute(ApiRequest.get("test", "/temporary").build());

    Assert.assertEquals(response.statusCode(), 200);
    Assert.assertEquals(response.attempts(), 2);
    Assert.assertEquals(calls.get(), 2);
    Assert.assertEquals(sleeps.get(), 1);
    Assert.assertEquals(traces.stream().map(ExchangeTrace::statusCode).toList(), List.of(503, 200));
  }

  @Test
  public void doesNotRetryPostByDefault() {
    AtomicInteger sleeps = new AtomicInteger();
    FrameworkConfig config = config();
    RestAssuredApiClient client =
        new RestAssuredApiClient(
            config,
            new CredentialManager(Map.of()),
            new RetryPolicy(config.retry()),
            new Redactor(),
            ignored -> {},
            Clock.systemUTC(),
            ignored -> sleeps.incrementAndGet());

    ApiResponse response = client.execute(ApiRequest.post("test", "/temporary").build());

    Assert.assertEquals(response.statusCode(), 503);
    Assert.assertEquals(response.attempts(), 1);
    Assert.assertEquals(calls.get(), 1);
    Assert.assertEquals(sleeps.get(), 0);
  }

  @Test
  public void invalidatesCredentialsAndRetriesOnceAfterUnauthorized() {
    AtomicInteger authentications = new AtomicInteger();
    List<ExchangeTrace> traces = new ArrayList<>();
    FrameworkConfig config = authenticatedConfig();
    CredentialManager credentials =
        new CredentialManager(
            Map.of(
                "bearer",
                actor ->
                    AuthMaterial.bearer(
                        "token-" + authentications.incrementAndGet(),
                        Instant.now().plusSeconds(300))));
    RestAssuredApiClient client =
        new RestAssuredApiClient(
            config,
            credentials,
            new RetryPolicy(config.retry()),
            new Redactor(),
            traces::add,
            Clock.systemUTC(),
            ignored -> {});

    ApiResponse response = client.execute(ApiRequest.get("test", "/protected").build());

    Assert.assertEquals(response.statusCode(), 200);
    Assert.assertEquals(response.attempts(), 2);
    Assert.assertEquals(calls.get(), 2);
    Assert.assertEquals(authentications.get(), 2);
    Assert.assertEquals(traces.stream().map(ExchangeTrace::statusCode).toList(), List.of(401, 200));
    Assert.assertTrue(
        traces.stream()
            .allMatch(trace -> "<redacted>".equals(trace.requestHeaders().get("Authorization"))));
  }

  @Test
  public void refreshesCredentialsOnlyOnceWhenUnauthorizedPersists() {
    AtomicInteger authentications = new AtomicInteger();
    FrameworkConfig config = authenticatedConfig();
    CredentialManager credentials =
        new CredentialManager(
            Map.of(
                "bearer",
                actor ->
                    AuthMaterial.bearer(
                        "invalid-" + authentications.incrementAndGet(),
                        Instant.now().plusSeconds(300))));
    RestAssuredApiClient client =
        new RestAssuredApiClient(
            config,
            credentials,
            new RetryPolicy(config.retry()),
            new Redactor(),
            ignored -> {},
            Clock.systemUTC(),
            ignored -> {});

    ApiResponse response = client.execute(ApiRequest.get("test", "/protected").build());

    Assert.assertEquals(response.statusCode(), 401);
    Assert.assertEquals(response.attempts(), 2);
    Assert.assertEquals(calls.get(), 2);
    Assert.assertEquals(authentications.get(), 2);
  }

  @Test
  public void sendsMultipartWithoutTracingFileContent() {
    List<ExchangeTrace> traces = new ArrayList<>();
    RestAssuredApiClient client =
        new RestAssuredApiClient(config(), new CredentialManager(Map.of()), traces::add);

    ApiResponse response =
        client.execute(
            ApiRequest.post("test", "/upload")
                .multipart(MultipartPart.text("description", "public description"))
                .multipart(
                    MultipartPart.bytes(
                        "file",
                        "secret-name.txt",
                        "top-secret-content".getBytes(StandardCharsets.UTF_8),
                        "text/plain"))
                .build());

    Assert.assertEquals(response.statusCode(), 201);
    Assert.assertFalse(traces.get(0).requestBody().contains("top-secret-content"));
    Assert.assertFalse(traces.get(0).requestBody().contains("secret-name.txt"));
    Assert.assertTrue(traces.get(0).requestBody().contains("file{bytes,text/plain"));
  }

  @Test
  public void preservesBinaryResponseAndKeepsItOutOfTrace() {
    List<ExchangeTrace> traces = new ArrayList<>();
    RestAssuredApiClient client =
        new RestAssuredApiClient(config(), new CredentialManager(Map.of()), traces::add);

    ApiResponse response = client.execute(ApiRequest.get("test", "/binary").build());

    Assert.assertEquals(response.bytes(), new byte[] {0, 1, 2, (byte) 255});
    Assert.assertEquals(
        traces.get(0).responseBody(), "<binary content-type=application/octet-stream bytes=4>");
  }

  private FrameworkConfig config() {
    return FrameworkConfig.builder()
        .service(
            ServiceConfig.builder("test", "http://127.0.0.1:" + server.getAddress().getPort())
                .build())
        .build();
  }

  private FrameworkConfig authenticatedConfig() {
    return FrameworkConfig.builder()
        .service(
            ServiceConfig.builder("test", "http://127.0.0.1:" + server.getAddress().getPort())
                .authProfile("bearer")
                .build())
        .build();
  }

  private void handleTemporaryFailure(HttpExchange exchange) throws IOException {
    int status = calls.incrementAndGet() == 1 ? 503 : 200;
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private void handleProtected(HttpExchange exchange) throws IOException {
    calls.incrementAndGet();
    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
    int status = "Bearer token-2".equals(authorization) ? 200 : 401;
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private void handleUpload(HttpExchange exchange) throws IOException {
    byte[] request = exchange.getRequestBody().readAllBytes();
    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    int status =
        contentType != null && contentType.startsWith("multipart/form-data") && request.length > 0
            ? 201
            : 400;
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private void handleBinary(HttpExchange exchange) throws IOException {
    byte[] body = {0, 1, 2, (byte) 255};
    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
