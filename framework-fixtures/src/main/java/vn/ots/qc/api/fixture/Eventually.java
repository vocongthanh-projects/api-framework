package vn.ots.qc.api.fixture;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Eventually {
  private final Duration timeout;
  private final Duration pollInterval;
  private final Clock clock;
  private final PollSleeper sleeper;

  private Eventually(Duration timeout, Duration pollInterval, Clock clock, PollSleeper sleeper) {
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Timeout must be positive");
    }
    this.timeout = timeout;
    this.pollInterval = pollInterval;
    this.clock = Objects.requireNonNull(clock);
    this.sleeper = Objects.requireNonNull(sleeper);
  }

  public static Eventually within(Duration timeout) {
    return new Eventually(
        timeout, Duration.ofSeconds(1), Clock.systemUTC(), PollSleeper.threadSleep());
  }

  public Eventually pollEvery(Duration interval) {
    if (interval.isNegative() || interval.isZero())
      throw new IllegalArgumentException("Poll interval must be positive");
    return new Eventually(timeout, interval, clock, sleeper);
  }

  static Eventually testing(
      Duration timeout, Duration pollInterval, Clock clock, PollSleeper sleeper) {
    if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
      throw new IllegalArgumentException("Poll interval must be positive");
    }
    return new Eventually(timeout, pollInterval, clock, sleeper);
  }

  public <T> T until(Supplier<T> supplier, Predicate<T> condition, String description) {
    Objects.requireNonNull(supplier);
    Objects.requireNonNull(condition);
    Instant deadline = clock.instant().plus(timeout);
    T lastValue = null;
    Throwable lastError = null;
    int polls = 0;
    do {
      polls++;
      try {
        lastValue = supplier.get();
        lastError = null;
        if (condition.test(lastValue)) return lastValue;
      } catch (RuntimeException error) {
        lastError = error;
      }
      try {
        sleeper.sleep(pollInterval);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Polling interrupted: " + description, e);
      }
    } while (clock.instant().isBefore(deadline));

    AssertionError timeoutError =
        new AssertionError(
            "Timed out waiting for "
                + description
                + " after "
                + polls
                + " polls. Last value: "
                + lastValue);
    if (lastError != null) timeoutError.addSuppressed(lastError);
    throw timeoutError;
  }
}
