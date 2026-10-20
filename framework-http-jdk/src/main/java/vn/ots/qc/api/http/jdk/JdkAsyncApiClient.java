package vn.ots.qc.api.http.jdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.core.auth.AuthMaterial;
import vn.ots.qc.api.core.auth.CredentialManager;
import vn.ots.qc.api.core.config.FrameworkConfig;
import vn.ots.qc.api.core.config.ServiceConfig;
import vn.ots.qc.api.core.http.ApiClient;
import vn.ots.qc.api.core.http.ApiRequest;
import vn.ots.qc.api.core.http.ApiResponse;
import vn.ots.qc.api.core.http.AsyncApiClient;
import vn.ots.qc.api.core.http.MultipartPart;
import vn.ots.qc.api.core.retry.RetryPolicy;
import vn.ots.qc.api.core.security.Redactor;
import vn.ots.qc.api.core.trace.ExchangeTrace;
import vn.ots.qc.api.core.trace.TraceSink;

/** JDK HTTP client transport with native non-blocking execution via {@code sendAsync}. */
public final class JdkAsyncApiClient implements ApiClient, AsyncApiClient {
  private static final Logger log = LoggerFactory.getLogger(JdkAsyncApiClient.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FrameworkConfig config;
  private final CredentialManager credentials;
  private final RetryPolicy retryPolicy;
  private final Redactor redactor;
  private final TraceSink traceSink;
  private final Clock clock;
  private final Executor executor;
  private final ConcurrentMap<String, HttpClient> clients = new ConcurrentHashMap<>();
  private final ConcurrentMap<SessionKey, CookieManager> cookieSessions = new ConcurrentHashMap<>();

  public JdkAsyncApiClient(
      FrameworkConfig config, CredentialManager credentials, TraceSink traceSink) {
    this(config, credentials, traceSink, Clock.systemUTC(), ForkJoinPool.commonPool());
  }

  public JdkAsyncApiClient(
      FrameworkConfig config,
      CredentialManager credentials,
      TraceSink traceSink,
      Clock clock,
      Executor executor) {
    this.config = java.util.Objects.requireNonNull(config, "config");
    this.credentials = java.util.Objects.requireNonNull(credentials, "credentials");
    this.retryPolicy = new RetryPolicy(config.retry());
    this.redactor = new Redactor();
    this.traceSink = traceSink == null ? TraceSink.noop() : traceSink;
    this.clock = java.util.Objects.requireNonNull(clock, "clock");
    this.executor = java.util.Objects.requireNonNull(executor, "executor");
  }

  @Override
  public ApiResponse execute(ApiRequest request) {
    return executeAsync(request).join();
  }

  @Override
  public CompletableFuture<ApiResponse> executeAsync(ApiRequest request) {
    java.util.Objects.requireNonNull(request, "request");
    ServiceConfig service = config.service(request.service());
    if (service.relaxedTls()) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException(
              "The JDK async transport requires valid TLS certificates; relaxedTls is not supported"));
    }
    RequestState state = new RequestState(request, service, clock.instant(), serialize(request));
    return attempt(state);
  }

  @Override
  public void clearSession(String serviceName, Actor actor) {
    ServiceConfig service = config.service(serviceName);
    cookieSessions.remove(sessionKey(serviceName, service, actor));
  }

  @Override
  public void clearSessions() {
    cookieSessions.clear();
  }

  private CompletableFuture<ApiResponse> attempt(RequestState state) {
    int attempt = ++state.attempts;
    Instant attemptStarted = clock.instant();
    return CompletableFuture.supplyAsync(
            () -> credentials.get(state.service.authProfile(), state.request.actor()), executor)
        .thenCompose(auth -> send(state, auth))
        .handle(
            (response, failure) -> {
              if (failure != null) {
                Throwable error = unwrap(failure);
                record(state, attempt, attemptStarted, null, "", safeError(error));
                Duration elapsed = Duration.between(state.startedAt, clock.instant());
                if (retryPolicy.retryException(error)
                    && retryPolicy.canRetry(state.request, attempt, elapsed)) {
                  return delay(retryPolicy.delayFor(attempt))
                      .thenCompose(ignored -> attempt(state));
                }
                return CompletableFuture.<ApiResponse>failedFuture(
                    new JdkApiTransportException(
                        "Request failed: "
                            + state.request.method()
                            + " "
                            + state.request.service()
                            + state.request.path(),
                        error));
              }

              ApiResponse apiResponse = response.apiResponse;
              record(
                  state,
                  attempt,
                  attemptStarted,
                  apiResponse.statusCode(),
                  traceResponse(response.contentType, apiResponse.bytes()),
                  null);

              if (apiResponse.statusCode() == 401
                  && !state.refreshedAfterUnauthorized
                  && attempt < retryPolicy.config().maxAttempts()
                  && !state.service.authProfile().equals("none")) {
                state.refreshedAfterUnauthorized = true;
                credentials.invalidate(state.service.authProfile(), state.request.actor());
                return attempt(state);
              }

              Duration elapsed = Duration.between(state.startedAt, clock.instant());
              if (retryPolicy.retryStatus(apiResponse.statusCode())
                  && retryPolicy.canRetry(state.request, attempt, elapsed)) {
                return delay(retryAfter(response, retryPolicy.delayFor(attempt)))
                    .thenCompose(ignored -> attempt(state));
              }
              return CompletableFuture.completedFuture(apiResponse);
            })
        .thenCompose(future -> future);
  }

  private CompletableFuture<WireResponse> send(RequestState state, AuthMaterial auth) {
    try {
      HttpRequest request = buildRequest(state, auth);
      return client(state.service)
          .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
          .thenApply(
              response -> {
                persistCookies(state, response);
                Map<String, List<String>> headers = new LinkedHashMap<>(response.headers().map());
                ApiResponse apiResponse =
                    new ApiResponse(
                        response.statusCode(),
                        headers,
                        response.body(),
                        Duration.between(state.startedAt, clock.instant()),
                        state.attempts);
                return new WireResponse(
                    apiResponse, response.headers().firstValue("Content-Type").orElse(""));
              });
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
  }

  private HttpRequest buildRequest(RequestState state, AuthMaterial auth) {
    ApiRequest apiRequest = state.request;
    URI uri = uri(state.service, apiRequest);
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(state.service.readTimeout());
    Map<String, String> headers = new LinkedHashMap<>(state.service.defaultHeaders());
    headers.putAll(auth.headers());
    headers.putAll(apiRequest.headers());
    Map<String, String> cookies = new LinkedHashMap<>(auth.cookies());
    cookies.putAll(sessionCookies(state, uri));
    cookies.putAll(apiRequest.cookies());
    if (!cookies.isEmpty()) {
      headers.put(
          "Cookie",
          cookies.entrySet().stream()
              .map(e -> e.getKey() + "=" + e.getValue())
              .collect(java.util.stream.Collectors.joining("; ")));
    }
    state.lastRequestHeaders = Map.copyOf(headers);
    headers.forEach(builder::header);

    Body body = body(apiRequest);
    if (!body.contentType.isBlank()
        && headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("Content-Type"))) {
      builder.header("Content-Type", body.contentType);
    }
    return builder.method(apiRequest.method().name(), body.publisher).build();
  }

  private Body body(ApiRequest request) {
    if (!request.multipartParts().isEmpty()) return multipart(request.multipartParts());
    if (!request.formParams().isEmpty()) {
      return new Body(
          "application/x-www-form-urlencoded; charset=UTF-8",
          HttpRequest.BodyPublishers.ofString(form(request.formParams()), StandardCharsets.UTF_8));
    }
    if (request.body() == null) return new Body("", HttpRequest.BodyPublishers.noBody());
    try {
      String json =
          request.body() instanceof String value
              ? value
              : MAPPER.writeValueAsString(request.body());
      return new Body("application/json; charset=UTF-8", HttpRequest.BodyPublishers.ofString(json));
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Cannot serialize request body", error);
    }
  }

  private Body multipart(List<MultipartPart> parts) {
    String boundary = "api-framework-" + java.util.UUID.randomUUID();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      for (MultipartPart part : parts) {
        write(bytes, "--" + boundary + "\r\n");
        String disposition = "Content-Disposition: form-data; name=\"" + quoted(part.name()) + "\"";
        if (!part.filename().isBlank()) disposition += "; filename=\"" + part.filename() + "\"";
        write(bytes, disposition + "\r\n");
        write(bytes, "Content-Type: " + part.contentType() + "\r\n\r\n");
        switch (part.kind()) {
          case TEXT -> write(bytes, part.text());
          case BYTES -> bytes.write(part.bytes());
          case FILE -> bytes.write(Files.readAllBytes(part.file()));
        }
        write(bytes, "\r\n");
      }
      write(bytes, "--" + boundary + "--\r\n");
    } catch (IOException error) {
      throw new IllegalArgumentException("Cannot read multipart content", error);
    }
    return new Body(
        "multipart/form-data; boundary=" + boundary,
        HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()));
  }

  private void write(ByteArrayOutputStream output, String value) throws IOException {
    output.write(value.getBytes(StandardCharsets.UTF_8));
  }

  private String quoted(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "_").replace("\n", "_");
  }

  private URI uri(ServiceConfig service, ApiRequest request) {
    String path = request.path();
    for (Map.Entry<String, Object> parameter : request.pathParams().entrySet()) {
      path =
          path.replace(
              "{" + parameter.getKey() + "}", encodePath(String.valueOf(parameter.getValue())));
    }
    while (path.startsWith("/")) path = path.substring(1);
    String base = service.baseUri().toString();
    if (!base.endsWith("/")) base += "/";
    StringBuilder result = new StringBuilder(base).append(path);
    String query = query(request.queryParams());
    if (!query.isBlank()) result.append(result.indexOf("?") >= 0 ? "&" : "?").append(query);
    return URI.create(result.toString());
  }

  private String query(Map<String, Object> values) {
    List<String> pairs = new ArrayList<>();
    values.forEach(
        (key, value) -> {
          if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> pairs.add(encode(key) + "=" + encode(String.valueOf(item))));
          } else if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
              pairs.add(
                  encode(key)
                      + "="
                      + encode(String.valueOf(java.lang.reflect.Array.get(value, index))));
            }
          } else if (value != null) {
            pairs.add(encode(key) + "=" + encode(String.valueOf(value)));
          }
        });
    return String.join("&", pairs);
  }

  private String form(Map<String, Object> values) {
    return query(values);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String encodePath(String value) {
    return encode(value).replace("+", "%20");
  }

  private HttpClient client(ServiceConfig service) {
    return clients.computeIfAbsent(
        service.name(),
        ignored ->
            HttpClient.newBuilder()
                .connectTimeout(service.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor)
                .build());
  }

  private Map<String, String> sessionCookies(RequestState state, URI uri) {
    CookieManager manager =
        cookieSessions.get(sessionKey(state.service.name(), state.service, state.request.actor()));
    if (manager == null) return Map.of();
    Map<String, String> cookies = new LinkedHashMap<>();
    for (HttpCookie cookie : manager.getCookieStore().get(uri))
      cookies.put(cookie.getName(), cookie.getValue());
    return cookies;
  }

  private void persistCookies(RequestState state, HttpResponse<?> response) {
    Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    response.headers().map().forEach(headers::put);
    CookieManager manager =
        cookieSessions.computeIfAbsent(
            sessionKey(state.service.name(), state.service, state.request.actor()),
            ignored -> new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER));
    try {
      manager.put(uri(state.service, state.request), headers);
    } catch (IOException error) {
      log.debug("Could not update session cookies for service {}", state.service.name(), error);
    }
  }

  private SessionKey sessionKey(String serviceName, ServiceConfig service, Actor actor) {
    String alias = actor == null ? "anonymous" : actor.alias();
    String profile =
        actor == null || actor.authProfile().equals("none")
            ? service.authProfile()
            : actor.authProfile();
    return new SessionKey(serviceName, profile, alias);
  }

  private void record(
      RequestState state,
      int attempt,
      Instant attemptStarted,
      Integer status,
      String responseBody,
      String error) {
    traceSink.record(
        new ExchangeTrace(
            clock.instant(),
            state.service.name(),
            state.request.actor() == null ? "anonymous" : state.request.actor().alias(),
            state.request.method().name(),
            state.request.path(),
            attempt,
            status,
            Duration.between(attemptStarted, clock.instant()).toMillis(),
            redactor.headers(requestHeaders(state)),
            state.requestBody,
            responseBody,
            error));
  }

  private Map<String, String> requestHeaders(RequestState state) {
    return state.lastRequestHeaders;
  }

  private String traceResponse(String contentType, byte[] body) {
    String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
    boolean textual =
        type.startsWith("text/")
            || type.contains("json")
            || type.contains("xml")
            || type.contains("x-www-form-urlencoded")
            || type.contains("javascript");
    if (!textual) {
      return "<binary content-type="
          + (type.isBlank() ? "unknown" : type)
          + " bytes="
          + body.length
          + ">";
    }
    return redactor.body(new String(body, StandardCharsets.UTF_8));
  }

  private String serialize(ApiRequest request) {
    if (!request.multipartParts().isEmpty()) {
      return "<multipart "
          + request.multipartParts().stream()
              .map(MultipartPart::traceMetadata)
              .collect(java.util.stream.Collectors.joining(", "))
          + ">";
    }
    if (!request.formParams().isEmpty()) return redactor.text(form(request.formParams()));
    if (request.body() == null) return "";
    try {
      String body =
          request.body() instanceof String value
              ? value
              : MAPPER.writeValueAsString(request.body());
      return redactor.body(body);
    } catch (JsonProcessingException error) {
      return "<unserializable:" + request.body().getClass().getSimpleName() + ">";
    }
  }

  private Duration retryAfter(WireResponse response, Duration fallback) {
    String raw =
        response.apiResponse.headers().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase("Retry-After"))
            .flatMap(entry -> entry.getValue().stream())
            .findFirst()
            .orElse(null);
    if (raw == null || raw.isBlank()) return fallback;
    try {
      Duration requested = Duration.ofSeconds(Long.parseLong(raw.trim()));
      return requested.compareTo(retryPolicy.config().maxDelay()) > 0
          ? retryPolicy.config().maxDelay()
          : requested;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private CompletableFuture<Void> delay(Duration duration) {
    return CompletableFuture.runAsync(
        () -> {},
        CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS, executor));
  }

  private Throwable unwrap(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
        && current.getCause() != null) current = current.getCause();
    return current;
  }

  private String safeError(Throwable error) {
    return redactor.text(
        error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : ": " + error.getMessage()));
  }

  private record Body(String contentType, HttpRequest.BodyPublisher publisher) {}

  private record WireResponse(ApiResponse apiResponse, String contentType) {}

  private record SessionKey(String service, String authProfile, String actorAlias) {}

  private static final class RequestState {
    private final ApiRequest request;
    private final ServiceConfig service;
    private final Instant startedAt;
    private final String requestBody;
    private int attempts;
    private boolean refreshedAfterUnauthorized;

    private Map<String, String> lastRequestHeaders = Map.of();

    private RequestState(
        ApiRequest request, ServiceConfig service, Instant startedAt, String requestBody) {
      this.request = request;
      this.service = service;
      this.startedAt = startedAt;
      this.requestBody = requestBody;
    }
  }
}
