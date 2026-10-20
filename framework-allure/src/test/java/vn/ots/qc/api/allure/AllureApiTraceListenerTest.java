package vn.ots.qc.api.allure;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.AllureResultsWriter;
import io.qameta.allure.model.TestResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.context.TestContext;
import vn.ots.qc.api.core.trace.ExchangeTrace;
import vn.ots.qc.api.testng.BaseApiTest;

public class AllureApiTraceListenerTest {
  @Test
  public void attachesTraceJsonToAllureResultWithoutSecrets() {
    MemoryResultsWriter writer = new MemoryResultsWriter();
    Allure.setLifecycle(new AllureLifecycle(writer));
    String uuid = "trace-test-uuid";
    Allure.getLifecycle().scheduleTestCase(uuid, new TestResult().setUuid(uuid).setName("trace"));
    Allure.getLifecycle().startTestCase(uuid);

    TestContext context = new TestContext("test-id");
    context.record(
        new ExchangeTrace(
            Instant.parse("2025-01-01T00:00:00Z"),
            "orders",
            "anonymous",
            "GET",
            "/orders/1",
            1,
            200,
            8,
            Map.of("Authorization", "<redacted>"),
            "",
            "{\"id\":1}",
            null));
    org.testng.internal.TestResult result = org.testng.internal.TestResult.newEmptyTestResult();
    result.setAttribute(BaseApiTest.CONTEXT_ATTRIBUTE, context);

    new AllureApiTraceListener().onTestSuccess(result);
    Allure.getLifecycle().stopTestCase(uuid);
    Allure.getLifecycle().writeTestCase(uuid);
    Allure.setLifecycle(new AllureLifecycle());

    Assert.assertEquals(writer.attachments.size(), 1);
    Assert.assertEquals(writer.testResult.getAttachments().size(), 1);
    Assert.assertEquals(writer.testResult.getAttachments().get(0).getName(), "API exchange trace");
    Assert.assertEquals(writer.testResult.getAttachments().get(0).getType(), "application/json");
    String json = writer.attachments.values().iterator().next();
    Assert.assertTrue(json.contains("/orders/1"));
    Assert.assertTrue(json.contains("<redacted>"));
    Assert.assertFalse(json.contains("secret-token"));
  }

  private static final class MemoryResultsWriter implements AllureResultsWriter {
    private final Map<String, String> attachments = new HashMap<>();
    private TestResult testResult;

    @Override
    public void write(TestResult result) {
      testResult = result;
    }

    @Override
    public void write(io.qameta.allure.model.TestResultContainer container) {}

    @Override
    public void write(String source, InputStream attachment) {
      try {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        attachment.transferTo(content);
        attachments.put(source, content.toString(StandardCharsets.UTF_8));
      } catch (IOException error) {
        throw new AssertionError(error);
      }
    }
  }
}
