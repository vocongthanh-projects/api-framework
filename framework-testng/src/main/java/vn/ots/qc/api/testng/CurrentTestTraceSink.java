package vn.ots.qc.api.testng;

import vn.ots.qc.api.core.context.TestContext;
import vn.ots.qc.api.core.context.TestContextHolder;
import vn.ots.qc.api.core.trace.ExchangeTrace;
import vn.ots.qc.api.core.trace.TraceSink;

/** Bridges transport traces into the context owned by the current TestNG test method. */
public final class CurrentTestTraceSink implements TraceSink {
    @Override
    public void record(ExchangeTrace trace) {
        TestContext context = TestContextHolder.getOrNull();
        if (context != null) context.record(trace);
    }
}
