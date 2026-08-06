package vn.ots.qc.api.fixture;

@FunctionalInterface
public interface CheckedRunnable {
    void run() throws Exception;
}

