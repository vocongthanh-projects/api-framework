package vn.ots.qc.api.testng;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.Test;
import org.testng.internal.TestResult;
import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.core.auth.AuthMaterial;
import vn.ots.qc.api.core.auth.CredentialManager;
import vn.ots.qc.api.core.config.FrameworkConfig;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.api.core.context.TestContextHolder;
import vn.ots.qc.api.core.http.ApiResponse;
import vn.ots.qc.api.core.trace.ExchangeTrace;

public class BaseApiTestLifecycleTest {
  @Test
  public void ownsContextCleanupTraceAndRuntimeLifecycle() throws Exception {
    Path reports = Path.of("target", "lifecycle-test-traces");
    LifecycleFixture fixture = new LifecycleFixture(reports);
    Method method = LifecycleFixture.class.getDeclaredMethod("sample");
    ITestResult result = TestResult.newEmptyTestResult();
    result.setStatus(ITestResult.SUCCESS);

    fixture.initializeApiRuntime();
    fixture.cacheCredential();
    fixture.initializeApiTestContext(method);
    Assert.assertNotNull(TestContextHolder.getOrNull());
    fixture.captureTrace();
    fixture.registerCleanup("parent");
    fixture.registerCleanup("child");

    fixture.finalizeApiTestContext(result);

    Assert.assertNull(TestContextHolder.getOrNull());
    Assert.assertEquals(fixture.cleanupOrder, List.of("child", "parent"));
    var completedContext =
        (vn.ots.qc.api.core.context.TestContext) result.getAttribute(BaseApiTest.CONTEXT_ATTRIBUTE);
    Assert.assertNotNull(completedContext);
    Assert.assertTrue(Files.exists(reports.resolve(safe(completedContext.testId()) + ".json")));

    fixture.finalizeApiRuntime();
    fixture.cacheCredential();
    Assert.assertEquals(
        fixture.authentications.get(), 2, "runtime close must clear cached credentials");
    Assert.assertTrue(fixture.afterClassCalled);
  }

  @Test
  public void suppressesCleanupFailureOntoExistingTestFailure() throws Exception {
    LifecycleFixture fixture = new LifecycleFixture(Path.of("target", "lifecycle-test-traces"));
    ITestResult result = TestResult.newEmptyTestResult();
    AssertionError testFailure = new AssertionError("test failed");
    result.setStatus(ITestResult.FAILURE);
    result.setThrowable(testFailure);

    fixture.initializeApiRuntime();
    fixture.initializeApiTestContext(LifecycleFixture.class.getDeclaredMethod("sample"));
    fixture
        .resources()
        .register(
            "resource",
            "broken",
            () -> {
              throw new IllegalStateException("cleanup failed");
            });
    fixture.finalizeApiTestContext(result);
    fixture.finalizeApiRuntime();

    Assert.assertSame(result.getThrowable(), testFailure);
    Assert.assertEquals(testFailure.getSuppressed().length, 1);
    Assert.assertTrue(
        testFailure
            .getSuppressed()[0]
            .getMessage()
            .contains("Cleanup failed for: resource:broken"));
    Assert.assertNull(TestContextHolder.getOrNull());
  }

  private static String safe(String value) {
    return value.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static final class LifecycleFixture extends BaseApiTest {
    private final Path reports;
    private final List<String> cleanupOrder = new ArrayList<>();
    private final AtomicInteger authentications = new AtomicInteger();
    private CredentialManager credentials;
    private boolean afterClassCalled;

    private LifecycleFixture(Path reports) {
      this.reports = reports;
    }

    @Override
    protected ApiTestRuntime createRuntime() {
      FrameworkConfig config =
          FrameworkConfig.builder()
              .service(
                  ServiceConfig.builder("test", "http://localhost").authProfile("auth").build())
              .build();
      credentials =
          new CredentialManager(
              Map.of(
                  "auth",
                  actor -> {
                    authentications.incrementAndGet();
                    return AuthMaterial.bearer("token", Instant.now().plusSeconds(300));
                  }));
      return new ApiTestRuntime(
          config, credentials, request -> new ApiResponse(200, Map.of(), "{}", Duration.ZERO, 1));
    }

    @Override
    protected void afterApiClass() {
      afterClassCalled = true;
    }

    @Override
    protected Path reportDirectory() {
      return reports;
    }

    void sample() {}

    void cacheCredential() {
      credentials.get("auth", Actor.of("employee", "auth"));
    }

    void registerCleanup(String name) {
      resources().register("resource", name, () -> cleanupOrder.add(name));
    }

    void captureTrace() {
      new CurrentTestTraceSink()
          .record(
              new ExchangeTrace(
                  Instant.now(),
                  "test",
                  "employee",
                  "GET",
                  "/",
                  1,
                  200,
                  1,
                  Map.of(),
                  "",
                  "{}",
                  null));
      Assert.assertEquals(context().traces().size(), 1);
    }
  }
}
