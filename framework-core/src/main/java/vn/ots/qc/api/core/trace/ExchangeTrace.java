package vn.ots.qc.api.core.trace;

import java.time.Instant;
import java.util.Map;

public record ExchangeTrace(
    Instant timestamp,
    String service,
    String actor,
    String method,
    String path,
    int attempt,
    Integer statusCode,
    long durationMillis,
    Map<String, String> requestHeaders,
    String requestBody,
    String responseBody,
    String error) {

  public ExchangeTrace {
    requestHeaders = Map.copyOf(requestHeaders == null ? Map.of() : requestHeaders);
  }
}
