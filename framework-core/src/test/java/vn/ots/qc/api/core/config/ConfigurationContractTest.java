package vn.ots.qc.api.core.config;

import static org.testng.Assert.expectThrows;

import java.time.Duration;
import org.testng.annotations.Test;
import vn.ots.qc.api.core.http.ApiRequest;

public final class ConfigurationContractTest {
  @Test
  public void rejectsCredentialsEmbeddedInServiceUri() {
    expectThrows(
        IllegalArgumentException.class,
        () -> ServiceConfig.builder("accounts", "https://user:secret@example.test").build());
  }

  @Test
  public void rejectsInvalidRetryBounds() {
    expectThrows(
        IllegalArgumentException.class,
        () ->
            new RetryConfig(
                3,
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                java.util.Set.of(503)));
  }

  @Test
  public void rejectsAbsoluteRequestPaths() {
    expectThrows(
        IllegalArgumentException.class,
        () -> ApiRequest.get("accounts", "https://untrusted.example/path").build());
  }
}
