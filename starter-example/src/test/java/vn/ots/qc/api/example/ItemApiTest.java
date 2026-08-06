package vn.ots.qc.api.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.core.auth.AuthMaterial;
import vn.ots.qc.api.core.auth.CredentialManager;
import vn.ots.qc.api.core.config.FrameworkConfig;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.api.restassured.RestAssuredApiClient;
import vn.ots.qc.api.testng.ApiTestRuntime;
import vn.ots.qc.api.testng.BaseApiTest;
import vn.ots.qc.api.testng.CurrentTestTraceSink;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;

public class ItemApiTest extends BaseApiTest {
    private HttpServer server;
    private ItemClient items;

    @Override
    protected void beforeApiClass() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items", this::handleItems);
        server.start();
    }

    @Override
    protected ApiTestRuntime createRuntime() {
        FrameworkConfig config = FrameworkConfig.builder()
                .environment("test")
                .service(ServiceConfig.builder("catalog", "http://127.0.0.1:" + server.getAddress().getPort())
                        .authProfile("test-auth")
                        .build())
                .build();
        CredentialManager credentials = new CredentialManager(Map.of(
                "test-auth", actor -> AuthMaterial.bearer(UUID.randomUUID().toString(), Instant.now().plusSeconds(300))));
        RestAssuredApiClient api = new RestAssuredApiClient(config, credentials, new CurrentTestTraceSink());
        items = new ItemClient(api);
        return new ApiTestRuntime(config, credentials, api);
    }

    @Override
    protected void afterApiClass() {
        if (server != null) server.stop(0);
    }

    @Test(groups = "smoke")
    public void createsAnItemAndCapturesTheExchange() {
        Actor visitor = Actor.anonymous();
        context().actor(visitor);

        items.create(visitor, "example-item").requireStatus(201);

        Assert.assertEquals(context().traces().size(), 1);
        Assert.assertEquals(context().traces().get(0).method(), "POST");
        Assert.assertEquals(context().traces().get(0).requestHeaders().get("Authorization"), "<redacted>");
    }

    private void handleItems(HttpExchange exchange) throws IOException {
        int status;
        String body;
        if (exchange.getRequestMethod().equals("POST")) {
            status = 201;
            body = "{\"id\":\"item-1\"}";
        } else {
            status = 200;
            body = "{\"items\":[]}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
