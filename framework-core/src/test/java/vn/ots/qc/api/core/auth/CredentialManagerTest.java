package vn.ots.qc.api.core.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CredentialManagerTest {
  @Test
  public void cachesCredentialsPerActorAndProfile() {
    AtomicInteger logins = new AtomicInteger();
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    CredentialManager manager =
        new CredentialManager(
            Map.of(
                "employee",
                actor ->
                    AuthMaterial.bearer(
                        "token-" + actor.alias() + "-" + logins.incrementAndGet(),
                        clock.instant().plusSeconds(300))),
            clock,
            Duration.ofSeconds(15));

    manager.get("employee", Actor.of("alice", "employee"));
    manager.get("employee", Actor.of("alice", "employee"));
    manager.get("employee", Actor.of("bob", "employee"));

    Assert.assertEquals(logins.get(), 2);
  }

  @Test
  public void reauthenticatesAfterExplicitInvalidation() {
    AtomicInteger logins = new AtomicInteger();
    CredentialManager manager =
        new CredentialManager(
            Map.of(
                "admin",
                actor -> {
                  logins.incrementAndGet();
                  return AuthMaterial.bearer("token", Instant.now().plusSeconds(300));
                }));
    Actor admin = Actor.of("admin", "admin");
    manager.get("admin", admin);
    manager.invalidate("admin", admin);
    manager.get("admin", admin);
    Assert.assertEquals(logins.get(), 2);
  }
}
