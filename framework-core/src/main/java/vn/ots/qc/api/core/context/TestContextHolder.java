package vn.ots.qc.api.core.context;

public final class TestContextHolder {
  private static final ThreadLocal<TestContext> CURRENT = new ThreadLocal<>();

  private TestContextHolder() {}

  public static void set(TestContext context) {
    CURRENT.set(context);
  }

  public static TestContext get() {
    TestContext context = CURRENT.get();
    if (context == null) throw new IllegalStateException("No TestContext is active on this thread");
    return context;
  }

  public static TestContext getOrNull() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
