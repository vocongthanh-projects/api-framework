package vn.ots.qc.consumer.template.base;

import vn.ots.qc.consumer.template.client.SampleApiClient;
import com.sun.net.httpserver.HttpServer;
import vn.ots.qc.api.core.auth.CredentialManager;
import vn.ots.qc.api.core.config.FrameworkConfig;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.api.restassured.RestAssuredApiClient;
import vn.ots.qc.api.testng.ApiTestRuntime;
import vn.ots.qc.api.testng.BaseApiTest;
import vn.ots.qc.api.testng.CurrentTestTraceSink;

import java.net.InetSocketAddress;
import java.util.Map;

/** Base wiring for consumer tests. The sample server keeps the template smoke test offline. */
public abstract class BaseProjectApiTest extends BaseApiTest {
    private HttpServer sampleServer;
    private SampleApiClient sampleClient;

    @Override
    protected void beforeApiClass() throws Exception {
        sampleServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sampleServer.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"ok\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        sampleServer.createContext("/items", exchange -> {
            int status = exchange.getRequestMethod().equalsIgnoreCase("POST") ? 201 : 200;
            byte[] body = status == 201
                    ? "{\"id\":\"sample-item\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : "{\"deleted\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        sampleServer.start();
    }

    @Override
    protected ApiTestRuntime createRuntime() {
        FrameworkConfig config = FrameworkConfig.builder()
                .environment("template")
                .service(ServiceConfig.builder("sample", "http://127.0.0.1:" + sampleServer.getAddress().getPort()).build())
                .build();
        CredentialManager credentials = new CredentialManager(Map.of());
        RestAssuredApiClient client = new RestAssuredApiClient(config, credentials, new CurrentTestTraceSink());
        sampleClient = new SampleApiClient(client);
        return new ApiTestRuntime(config, credentials, client);
    }

    protected final SampleApiClient sampleClient() {
        return sampleClient;
    }

    @Override
    protected void afterApiClass() {
        if (sampleServer != null) sampleServer.stop(0);
    }
}
