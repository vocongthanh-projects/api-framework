package vn.ots.qc.api.core.auth;

import java.time.Instant;
import java.util.Map;

public record AuthMaterial(
    Map<String, String> headers, Map<String, String> cookies, Instant expiresAt) {
  public AuthMaterial {
    headers = Map.copyOf(headers == null ? Map.of() : headers);
    cookies = Map.copyOf(cookies == null ? Map.of() : cookies);
  }

  public static AuthMaterial none() {
    return new AuthMaterial(Map.of(), Map.of(), null);
  }

  public static AuthMaterial bearer(String token, Instant expiresAt) {
    if (token == null || token.isBlank())
      throw new IllegalArgumentException("Bearer token is required");
    return new AuthMaterial(Map.of("Authorization", "Bearer " + token), Map.of(), expiresAt);
  }

  public boolean isUsableAt(Instant now, java.time.Duration refreshSkew) {
    return expiresAt == null || expiresAt.isAfter(now.plus(refreshSkew));
  }
}
