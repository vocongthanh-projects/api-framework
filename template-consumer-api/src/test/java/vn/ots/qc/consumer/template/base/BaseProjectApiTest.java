package vn.ots.qc.consumer.template.base;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.api.testng.ApiTestRuntime;
import vn.ots.qc.api.testng.BaseApiTest;
import vn.ots.qc.api.testng.CurrentTestTraceSink;
import vn.ots.qc.consumer.template.auth.ConsumerRuntimeFactory;
import vn.ots.qc.consumer.template.portals.sample.modules.items.client.items.SampleApiClient;

/** Base wiring for consumer tests. The sample server keeps the template smoke test offline. */
public abstract class BaseProjectApiTest extends BaseApiTest {
  private HttpServer sampleServer;
  private SampleApiClient sampleClient;

  @Override
  protected void beforeApiClass() throws Exception {
    sampleServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    sampleServer.createContext(
        "/health",
        exchange -> {
          byte[] body = "{\"status\":\"ok\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    sampleServer.createContext(
        "/items",
        exchange -> {
          int status = exchange.getRequestMethod().equalsIgnoreCase("POST") ? 201 : 200;
          byte[] body =
              status == 201
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
    ApiTestRuntime runtime =
        ConsumerRuntimeFactory.createTemplateRuntime(
            "template",
            ServiceConfig.builder(
                    "sample", "http://127.0.0.1:" + sampleServer.getAddress().getPort())
                .build(),
            new CurrentTestTraceSink());
    sampleClient = new SampleApiClient(runtime.client());
    return runtime;
  }

  protected final SampleApiClient sampleClient() {
    return sampleClient;
  }

  @Override
  protected void afterApiClass() {
    if (sampleServer != null) sampleServer.stop(0);
  }
}
