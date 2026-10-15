package vn.ots.qc.api.core.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.ots.qc.api.core.auth.Actor;

public final class ApiRequest {
  private final HttpMethod method;
  private final String service;
  private final String path;
  private final Actor actor;
  private final Object body;
  private final Map<String, Object> queryParams;
  private final Map<String, Object> pathParams;
  private final Map<String, String> headers;
  private final Map<String, String> cookies;
  private final Map<String, Object> formParams;
  private final List<MultipartPart> multipartParts;
  private final RetryMode retryMode;

  private ApiRequest(Builder builder) {
    this.method = java.util.Objects.requireNonNull(builder.method, "method");
    this.service = require(builder.service, "service");
    this.path = require(builder.path, "path");
    if (this.path.matches("(?i)^https?://.*")) {
      throw new IllegalArgumentException("Request path must be relative to the configured service");
    }
    this.actor = builder.actor;
    this.body = builder.body;
    this.queryParams = Map.copyOf(builder.queryParams);
    this.pathParams = Map.copyOf(builder.pathParams);
    this.headers = Map.copyOf(builder.headers);
    this.cookies = Map.copyOf(builder.cookies);
    this.formParams = Map.copyOf(builder.formParams);
    this.multipartParts = List.copyOf(builder.multipartParts);
    this.retryMode = builder.retryMode;
    if (!multipartParts.isEmpty() && (body != null || !formParams.isEmpty())) {
      throw new IllegalArgumentException(
          "Multipart request cannot also contain body or form parameters");
    }
    if (retryMode == RetryMode.IDEMPOTENT
        && headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("Idempotency-Key"))) {
      throw new IllegalArgumentException("RetryMode.IDEMPOTENT requires an Idempotency-Key header");
    }
  }

  private String require(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required");
    return value;
  }

  public HttpMethod method() {
    return method;
  }

  public String service() {
    return service;
  }

  public String path() {
    return path;
  }

  public Actor actor() {
    return actor;
  }

  public Object body() {
    return body;
  }

  public Map<String, Object> queryParams() {
    return queryParams;
  }

  public Map<String, Object> pathParams() {
    return pathParams;
  }

  public Map<String, String> headers() {
    return headers;
  }

  public Map<String, String> cookies() {
    return cookies;
  }

  public Map<String, Object> formParams() {
    return formParams;
  }

  public List<MultipartPart> multipartParts() {
    return multipartParts;
  }

  public RetryMode retryMode() {
    return retryMode;
  }

  public static Builder request(HttpMethod method, String service, String path) {
    return new Builder(method, service, path);
  }

  public static Builder get(String service, String path) {
    return request(HttpMethod.GET, service, path);
  }

  public static Builder post(String service, String path) {
    return request(HttpMethod.POST, service, path);
  }

  public static Builder put(String service, String path) {
    return request(HttpMethod.PUT, service, path);
  }

  public static Builder patch(String service, String path) {
    return request(HttpMethod.PATCH, service, path);
  }

  public static Builder delete(String service, String path) {
    return request(HttpMethod.DELETE, service, path);
  }

  public static final class Builder {
    private final HttpMethod method;
    private final String service;
    private final String path;
    private Actor actor;
    private Object body;
    private final Map<String, Object> queryParams = new LinkedHashMap<>();
    private final Map<String, Object> pathParams = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final Map<String, Object> formParams = new LinkedHashMap<>();
    private final List<MultipartPart> multipartParts = new ArrayList<>();
    private RetryMode retryMode = RetryMode.DEFAULT;

    private Builder(HttpMethod method, String service, String path) {
      this.method = method;
      this.service = service;
      this.path = path;
    }

    public Builder actor(Actor value) {
      this.actor = value;
      return this;
    }

    public Builder body(Object value) {
      this.body = value;
      return this;
    }

    public Builder queryParam(String name, Object value) {
      queryParams.put(name, value);
      return this;
    }

    public Builder pathParam(String name, Object value) {
      pathParams.put(name, value);
      return this;
    }

    public Builder header(String name, String value) {
      headers.put(name, value);
      return this;
    }

    public Builder cookie(String name, String value) {
      cookies.put(name, value);
      return this;
    }

    public Builder formParam(String name, Object value) {
      formParams.put(name, value);
      return this;
    }

    public Builder multipart(MultipartPart part) {
      multipartParts.add(java.util.Objects.requireNonNull(part, "multipart part"));
      return this;
    }

    public Builder retryMode(RetryMode value) {
      retryMode = value;
      return this;
    }

    public ApiRequest build() {
      return new ApiRequest(this);
    }
  }
}
