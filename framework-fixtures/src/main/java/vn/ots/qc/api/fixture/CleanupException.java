package vn.ots.qc.api.fixture;

import java.util.List;

public final class CleanupException extends RuntimeException {
  private final List<String> failedResources;

  CleanupException(List<String> failedResources, List<Throwable> causes) {
    super("Cleanup failed for: " + String.join(", ", failedResources));
    this.failedResources = List.copyOf(failedResources);
    causes.forEach(this::addSuppressed);
  }

  public List<String> failedResources() {
    return failedResources;
  }
}
