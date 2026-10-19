package vn.ots.qc.api.core.retry;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import vn.ots.qc.api.core.config.RetryConfig;
import vn.ots.qc.api.core.http.ApiRequest;

public final class RetryPolicy {
  private final RetryConfig config;

  public RetryPolicy(RetryConfig config) {
    this.config = config;
  }

  public RetryConfig config() {
    return config;
  }

  public boolean canRetry(ApiRequest request, int completedAttempts, Duration elapsed) {
    boolean retryAllowed =
        switch (request.retryMode()) {
          case NEVER -> false;
          case DEFAULT -> request.method().safeToRetry();
          case IDEMPOTENT -> true; // ApiRequest enforces Idempotency-Key for this mode.
        };
    if (!retryAllowed) return false;
    return completedAttempts < config.maxAttempts() && elapsed.compareTo(config.maxElapsed()) < 0;
  }

  public boolean retryStatus(int status) {
    return config.retryableStatuses().contains(status);
  }

  public boolean retryException(Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (current instanceof ConnectException
          || current instanceof SocketTimeoutException
          || current instanceof HttpTimeoutException
          || current instanceof SocketException) {
        return true;
      }
    }
    return false;
  }

  public Duration delayFor(int completedAttempts) {
    long base = config.initialDelay().toMillis();
    long ceiling = config.maxDelay().toMillis();
    long exponential;
    try {
      exponential = Math.multiplyExact(base, 1L << Math.min(completedAttempts - 1, 20));
    } catch (ArithmeticException e) {
      exponential = ceiling;
    }
    long bounded = Math.min(exponential, ceiling);
    long jitter = bounded <= 1 ? 0 : ThreadLocalRandom.current().nextLong(Math.max(1, bounded / 4));
    return Duration.ofMillis(Math.min(ceiling, bounded + jitter));
  }
}
