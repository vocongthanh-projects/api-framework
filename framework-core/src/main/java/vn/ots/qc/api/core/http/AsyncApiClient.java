package vn.ots.qc.api.core.http;

import java.util.concurrent.CompletableFuture;
import vn.ots.qc.api.core.auth.Actor;

/** API transport contract for callers that need a non-blocking request operation. */
public interface AsyncApiClient {
  CompletableFuture<ApiResponse> executeAsync(ApiRequest request);

  default void clearSession(String service, Actor actor) {}

  default void clearSessions() {}
}
