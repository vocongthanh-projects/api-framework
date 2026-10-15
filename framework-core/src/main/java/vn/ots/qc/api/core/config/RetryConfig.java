package vn.ots.qc.api.core.config;

import java.time.Duration;
import java.util.Set;

public record RetryConfig(
    int maxAttempts,
    Duration initialDelay,
    Duration maxDelay,
    Duration maxElapsed,
    Set<Integer> retryableStatuses) {

  public RetryConfig {
    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
    initialDelay = initialDelay == null ? Duration.ofMillis(500) : initialDelay;
    maxDelay = maxDelay == null ? Duration.ofSeconds(5) : maxDelay;
    maxElapsed = maxElapsed == null ? Duration.ofSeconds(30) : maxElapsed;
    retryableStatuses =
        Set.copyOf(
            retryableStatuses == null ? Set.of(408, 425, 429, 502, 503, 504) : retryableStatuses);
    if (initialDelay.isNegative() || maxDelay.isNegative() || maxElapsed.isNegative()) {
      throw new IllegalArgumentException("Retry durations must not be negative");
    }
    if (initialDelay.compareTo(maxDelay) > 0) {
      throw new IllegalArgumentException("initialDelay must not exceed maxDelay");
    }
    if (retryableStatuses.stream().anyMatch(status -> status < 100 || status > 599)) {
      throw new IllegalArgumentException("Retry status codes must be between 100 and 599");
    }
  }

  public static RetryConfig defaults() {
    return new RetryConfig(
        3,
        Duration.ofMillis(500),
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        Set.of(408, 425, 429, 502, 503, 504));
  }
}
