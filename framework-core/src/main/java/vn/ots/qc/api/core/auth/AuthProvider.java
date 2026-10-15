package vn.ots.qc.api.core.auth;

@FunctionalInterface
public interface AuthProvider {
  AuthMaterial authenticate(Actor actor);
}
