package vn.ots.qc.api.fixture;

import java.time.Duration;

@FunctionalInterface
interface PollSleeper {
    void sleep(Duration duration) throws InterruptedException;

    static PollSleeper threadSleep() {
        return duration -> Thread.sleep(duration.toMillis());
    }
}
