package vn.ots.qc.api.testng;

import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeClass;
import vn.ots.qc.api.core.context.TestContext;
import vn.ots.qc.api.core.context.TestContextHolder;
import vn.ots.qc.api.fixture.ResourceLedger;

import java.lang.reflect.Method;
import java.nio.file.Path;

public abstract class BaseApiTest {
    public static final String CONTEXT_ATTRIBUTE = "api.framework.context";
    private TestContext context;
    private ResourceLedger resources;
    private ApiTestRuntime runtime;

    @BeforeClass(alwaysRun = true)
    public final void initializeApiRuntime() throws Exception {
        beforeApiClass();
        runtime = java.util.Objects.requireNonNull(createRuntime(), "createRuntime returned null");
    }

    @BeforeMethod(alwaysRun = true)
    public final void initializeApiTestContext(Method method) {
        String testId = getClass().getName() + "#" + method.getName();
        context = new TestContext(testId);
        resources = new ResourceLedger();
        TestContextHolder.set(context);
        beforeApiTest();
    }

    @AfterMethod(alwaysRun = true)
    public final void finalizeApiTestContext(ITestResult result) {
        Throwable cleanupError = null;
        try { resources.close(); }
        catch (Throwable error) { cleanupError = error; }

        if (cleanupError != null) {
            if (result.getThrowable() != null) result.getThrowable().addSuppressed(cleanupError);
            else {
                result.setThrowable(cleanupError);
                result.setStatus(ITestResult.FAILURE);
            }
        }

        result.setAttribute(CONTEXT_ATTRIBUTE, context);
        String status = switch (result.getStatus()) {
            case ITestResult.SUCCESS -> "PASSED";
            case ITestResult.SKIP -> "SKIPPED";
            default -> "FAILED";
        };
        try {
            new TraceReportWriter(reportDirectory()).write(context, status, result.getThrowable());
        } finally {
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
    protected void beforeApiClass() throws Exception { }
    protected void afterApiClass() throws Exception { }
    protected void beforeApiTest() { }
    protected final TestContext context() { return context; }
    protected final ResourceLedger resources() { return resources; }
    protected final ApiTestRuntime runtime() { return runtime; }
    protected final vn.ots.qc.api.core.http.ApiClient api() { return runtime.client(); }

    protected Path reportDirectory() {
        return Path.of(System.getProperty("api.trace.dir", "target/api-traces"));
    }
}
