package vn.ots.qc.api.core.http;

import vn.ots.qc.api.core.auth.Actor;

@FunctionalInterface
public interface ApiClient {
  ApiResponse execute(ApiRequest request);

  /** Clears server-managed session state for one service and actor, when supported. */
  default void clearSession(String service, Actor actor) {}

  /** Clears all server-managed session state, when supported. */
  default void clearSessions() {}
}
