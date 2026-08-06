package vn.ots.qc.api.restassured;

import java.time.Duration;

@FunctionalInterface
interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;

    static Sleeper threadSleep() { return duration -> Thread.sleep(duration.toMillis()); }
}
