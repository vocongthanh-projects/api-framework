package vn.ots.qc.api.core.http;

public enum RetryMode {
    /** Retry only HTTP methods that are safe by definition. */
    DEFAULT,
    /** Disable retry even for safe methods. Useful for negative and timing-sensitive tests. */
    NEVER,
    /** Allow retry for a mutating request; ApiRequest requires an Idempotency-Key header. */
    IDEMPOTENT
}
