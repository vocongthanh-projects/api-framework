package vn.ots.qc.api.core.trace;

@FunctionalInterface
public interface TraceSink {
    void record(ExchangeTrace trace);

    static TraceSink noop() { return ignored -> {}; }
}

