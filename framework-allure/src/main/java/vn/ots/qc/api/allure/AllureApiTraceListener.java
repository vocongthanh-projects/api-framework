package vn.ots.qc.api.allure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import vn.ots.qc.api.core.context.TestContext;
import vn.ots.qc.api.core.trace.ExchangeTrace;
import vn.ots.qc.api.testng.BaseApiTest;

/** Attaches the already-redacted API exchange trace to each Allure TestNG result. */
public final class AllureApiTraceListener implements ITestListener {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public void onTestSuccess(ITestResult result) {
    attach(result);
  }

  @Override
  public void onTestFailure(ITestResult result) {
    attach(result);
  }

  @Override
  public void onTestSkipped(ITestResult result) {
    attach(result);
  }

  @Override
  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    attach(result);
  }

  @Override
  public void onStart(ITestContext context) {}

  @Override
  public void onFinish(ITestContext context) {}

  private void attach(ITestResult result) {
    Object attribute = result.getAttribute(BaseApiTest.CONTEXT_ATTRIBUTE);
    if (!(attribute instanceof TestContext context)) return;
    try {
      Allure.addAttachment("API exchange trace", "application/json", serialize(context), "json");
    } catch (JsonProcessingException error) {
      Allure.addAttachment("API exchange trace error", "text/plain", error.getMessage(), "txt");
    }
  }

  private String serialize(TestContext context) throws JsonProcessingException {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("testId", context.testId());
    report.put("startedAt", context.startedAt().toString());
    report.put("actors", context.actors().keySet());
    report.put("exchanges", context.traces().stream().map(this::exchange).toList());
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report);
  }

  private Map<String, Object> exchange(ExchangeTrace trace) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("timestamp", trace.timestamp().toString());
    value.put("service", trace.service());
    value.put("actor", trace.actor());
    value.put("method", trace.method());
    value.put("path", trace.path());
    value.put("attempt", trace.attempt());
    value.put("statusCode", trace.statusCode());
    value.put("durationMillis", trace.durationMillis());
    value.put("requestHeaders", trace.requestHeaders());
    value.put("requestBody", trace.requestBody());
    value.put("responseBody", trace.responseBody());
    value.put("error", trace.error());
    return value;
  }
}
