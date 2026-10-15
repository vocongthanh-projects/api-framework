package vn.ots.qc.api.core.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe credentials cached by auth profile and actor alias. */
public final class CredentialManager {
  private final Map<String, AuthProvider> providers;
  private final ConcurrentHashMap<Key, AuthMaterial> cache = new ConcurrentHashMap<>();
  private final Clock clock;
  private final Duration refreshSkew;

  public CredentialManager(Map<String, AuthProvider> providers) {
    this(providers, Clock.systemUTC(), Duration.ofSeconds(15));
  }

  public CredentialManager(Map<String, AuthProvider> providers, Clock clock, Duration refreshSkew) {
    this.providers = Map.copyOf(providers == null ? Map.of() : providers);
    this.clock = Objects.requireNonNull(clock);
    this.refreshSkew = Objects.requireNonNull(refreshSkew);
  }

  public AuthMaterial get(String profile, Actor actor) {
    if (profile == null || profile.equals("none")) return AuthMaterial.none();
    Actor effectiveActor = actor == null ? Actor.of("default", profile) : actor;
    String effectiveProfile =
        effectiveActor.authProfile().equals("none") ? profile : effectiveActor.authProfile();
    AuthProvider provider = providers.get(effectiveProfile);
    if (provider == null)
      throw new IllegalStateException(
          "No auth provider registered for profile: " + effectiveProfile);
    Key key = new Key(effectiveProfile, effectiveActor.alias());
    return cache.compute(
        key,
        (ignored, current) ->
            current != null && current.isUsableAt(clock.instant(), refreshSkew)
                ? current
                : Objects.requireNonNull(
                    provider.authenticate(effectiveActor), "Auth provider returned null"));
  }

  public void invalidate(String profile, Actor actor) {
    if (profile == null || profile.equals("none")) return;
    Actor effectiveActor = actor == null ? Actor.of("default", profile) : actor;
    String effectiveProfile =
        effectiveActor.authProfile().equals("none") ? profile : effectiveActor.authProfile();
    cache.remove(new Key(effectiveProfile, effectiveActor.alias()));
  }

  public void clear() {
    cache.clear();
  }

  private record Key(String profile, String actorAlias) {}
}
