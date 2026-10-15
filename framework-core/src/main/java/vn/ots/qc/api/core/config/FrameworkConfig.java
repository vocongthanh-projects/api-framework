package vn.ots.qc.api.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FrameworkConfig {
  private final String environment;
  private final Map<String, ServiceConfig> services;
  private final RetryConfig retry;

  private FrameworkConfig(Builder builder) {
    if (builder.environment == null || builder.environment.isBlank()) {
      throw new IllegalStateException("Environment is required");
    }
    this.environment = builder.environment.trim();
    this.services = Map.copyOf(builder.services);
    this.retry = java.util.Objects.requireNonNull(builder.retry, "retry");
    if (services.isEmpty())
      throw new IllegalStateException("At least one service must be configured");
  }

  public String environment() {
    return environment;
  }

  public RetryConfig retry() {
    return retry;
  }

  public Map<String, ServiceConfig> services() {
    return services;
  }

  public ServiceConfig service(String name) {
    ServiceConfig service = services.get(name);
    if (service == null) throw new IllegalArgumentException("Unknown service: " + name);
    return service;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String environment = "local";
    private final Map<String, ServiceConfig> services = new LinkedHashMap<>();
    private RetryConfig retry = RetryConfig.defaults();

    public Builder environment(String value) {
      this.environment = value;
      return this;
    }

    public Builder service(ServiceConfig value) {
      java.util.Objects.requireNonNull(value, "service");
      if (this.services.putIfAbsent(value.name(), value) != null) {
        throw new IllegalArgumentException("Duplicate service: " + value.name());
      }
      return this;
    }

    public Builder retry(RetryConfig value) {
      this.retry = value;
      return this;
    }

    public FrameworkConfig build() {
      return new FrameworkConfig(this);
    }
  }
}
