package vn.ots.qc.api.testng;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import vn.ots.qc.api.core.context.TestContext;
import vn.ots.qc.api.core.context.TestContextHolder;
import vn.ots.qc.api.fixture.ResourceLedger;

public abstract class BaseApiTest {
  public static final String CONTEXT_ATTRIBUTE = "api.framework.context";
  private final ThreadLocal<ResourceLedger> resources = new ThreadLocal<>();
  private ApiTestRuntime runtime;

  @BeforeClass(alwaysRun = true)
  public final void initializeApiRuntime() throws Exception {
    beforeApiClass();
    runtime = java.util.Objects.requireNonNull(createRuntime(), "createRuntime returned null");
  }

  @BeforeMethod(alwaysRun = true)
  public final void initializeApiTestContext(Method method) {
    String testId = UUID.randomUUID() + "#" + getClass().getName() + "#" + method.getName();
    TestContextHolder.set(new TestContext(testId));
    resources.set(new ResourceLedger());
    beforeApiTest();
  }

  @AfterMethod(alwaysRun = true)
  public final void finalizeApiTestContext(ITestResult result) {
    Throwable cleanupError = null;
    try {
      currentResources().close();
    } catch (Throwable error) {
      cleanupError = error;
    }

    if (cleanupError != null) {
      if (result.getThrowable() != null) result.getThrowable().addSuppressed(cleanupError);
      else {
        result.setThrowable(cleanupError);
        result.setStatus(ITestResult.FAILURE);
      }
    }

    TestContext context = TestContextHolder.get();
    result.setAttribute(CONTEXT_ATTRIBUTE, context);
    String status =
        switch (result.getStatus()) {
          case ITestResult.SUCCESS -> "PASSED";
          case ITestResult.SKIP -> "SKIPPED";
          default -> "FAILED";
        };
    try {
      new TraceReportWriter(reportDirectory()).write(context, status, result.getThrowable());
    } finally {
      resources.remove();
      TestContextHolder.clear();
    }
  }

  @AfterClass(alwaysRun = true)
  public final void finalizeApiRuntime() throws Exception {
    try {
      if (runtime != null) runtime.close();
    } finally {
      afterApiClass();
    }
  }

  protected abstract ApiTestRuntime createRuntime();

  protected void beforeApiClass() throws Exception {}

  protected void afterApiClass() throws Exception {}

  protected void beforeApiTest() {}

  protected final TestContext context() {
    return TestContextHolder.get();
  }

  protected final ResourceLedger resources() {
    return currentResources();
  }

  protected final ApiTestRuntime runtime() {
    return runtime;
  }

  protected final vn.ots.qc.api.core.http.ApiClient api() {
    return runtime.client();
  }

  private ResourceLedger currentResources() {
    ResourceLedger current = resources.get();
    if (current == null) throw new IllegalStateException("No API test is active on this thread");
    return current;
  }

  protected Path reportDirectory() {
    return Path.of(System.getProperty("api.trace.dir", "target/api-traces"));
  }
}
