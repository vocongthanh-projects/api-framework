package vn.ots.qc.api.fixture;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

public class EventuallyTest {
    @Test
    public void returnsValueAfterPollingWithoutRealSleep() {
        MutableClock clock = new MutableClock();
        AtomicInteger calls = new AtomicInteger();
        Eventually eventually = Eventually.testing(Duration.ofSeconds(5), Duration.ofSeconds(1), clock,
                clock::advance);

        int result = eventually.until(calls::incrementAndGet, value -> value == 3, "third value");

        Assert.assertEquals(result, 3);
        Assert.assertEquals(calls.get(), 3);
        Assert.assertEquals(clock.instant(), Instant.EPOCH.plusSeconds(2));
    }

    @Test
    public void reportsLastErrorOnTimeout() {
        MutableClock clock = new MutableClock();
        Eventually eventually = Eventually.testing(Duration.ofSeconds(2), Duration.ofSeconds(1), clock,
                clock::advance);

        AssertionError error = Assert.expectThrows(AssertionError.class,
                () -> eventually.until(() -> { throw new IllegalStateException("not ready"); },
                        ignored -> true, "service readiness"));

        Assert.assertTrue(error.getMessage().contains("service readiness"));
        Assert.assertEquals(error.getSuppressed().length, 1);
        Assert.assertEquals(error.getSuppressed()[0].getMessage(), "not ready");
    }

    @Test
    public void rejectsNonPositiveDurations() {
        Assert.expectThrows(IllegalArgumentException.class, () -> Eventually.within(Duration.ZERO));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> Eventually.within(Duration.ofSeconds(1)).pollEvery(Duration.ZERO));
    }

    @Test
    public void preservesInterruptStatus() {
        Eventually eventually = Eventually.testing(Duration.ofSeconds(1), Duration.ofMillis(1),
                Clock.systemUTC(), ignored -> { throw new InterruptedException("stop"); });
        try {
            IllegalStateException error = Assert.expectThrows(IllegalStateException.class,
                    () -> eventually.until(() -> false, Boolean::booleanValue, "interrupt"));
            Assert.assertTrue(error.getMessage().contains("Polling interrupted"));
            Assert.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current = Instant.EPOCH;

        void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
