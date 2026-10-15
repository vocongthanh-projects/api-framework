package vn.ots.qc.api.restassured;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import vn.ots.qc.api.core.http.MultipartPart;
import vn.ots.qc.api.core.retry.RetryPolicy;
import vn.ots.qc.api.core.security.Redactor;
import vn.ots.qc.api.core.trace.ExchangeTrace;
import vn.ots.qc.api.core.trace.TraceSink;

public final class RestAssuredApiClient implements ApiClient {
  private static final Logger log = LoggerFactory.getLogger(RestAssuredApiClient.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FrameworkConfig config;
  private final CredentialManager credentials;
  private final RetryPolicy retryPolicy;
  private final Redactor redactor;
  private final TraceSink traceSink;
  private final Clock clock;
  private final Sleeper sleeper;

  public RestAssuredApiClient(FrameworkConfig config, CredentialManager credentials) {
    this(config, credentials, TraceSink.noop());
  }

  public RestAssuredApiClient(
      FrameworkConfig config, CredentialManager credentials, TraceSink traceSink) {
    this(
        config,
        credentials,
        new RetryPolicy(config.retry()),
        new Redactor(),
        traceSink,
        Clock.systemUTC(),
        Sleeper.threadSleep());
  }

  RestAssuredApiClient(
      FrameworkConfig config,
      CredentialManager credentials,
      RetryPolicy retryPolicy,
      Redactor redactor,
      TraceSink traceSink,
      Clock clock,
      Sleeper sleeper) {
    this.config = config;
    this.credentials = credentials;
    this.retryPolicy = retryPolicy;
    this.redactor = redactor;
    this.traceSink = traceSink == null ? TraceSink.noop() : traceSink;
    this.clock = clock;
    this.sleeper = sleeper;
  }

  @Override
  public ApiResponse execute(ApiRequest request) {
    ServiceConfig service = config.service(request.service());
    Instant executionStarted = clock.instant();
    int attempt = 0;
    boolean refreshedAfterUnauthorized = false;

    while (true) {
      attempt++;
      Instant attemptStarted = clock.instant();
      Map<String, String> requestHeaders = new LinkedHashMap<>(service.defaultHeaders());
      String requestBody = serialize(request);
      try {
        Actor actor = request.actor();
        AuthMaterial auth = credentials.get(service.authProfile(), actor);
        requestHeaders.putAll(auth.headers());
        requestHeaders.putAll(request.headers());

        Response raw =
            dispatch(buildSpecification(service, request, auth, requestHeaders), request);
        byte[] responseBytes = raw.asByteArray();
        Duration attemptDuration = Duration.between(attemptStarted, clock.instant());
        record(
            request,
            requestHeaders,
            requestBody,
            attempt,
            raw.statusCode(),
            attemptDuration,
            traceResponse(raw.contentType(), responseBytes),
            null);

        if (raw.statusCode() == 401
            && !refreshedAfterUnauthorized
            && attempt < retryPolicy.config().maxAttempts()
            && !service.authProfile().equals("none")) {
          refreshedAfterUnauthorized = true;
          credentials.invalidate(service.authProfile(), actor);
          continue;
        }

        Duration elapsed = Duration.between(executionStarted, clock.instant());
        if (retryPolicy.retryStatus(raw.statusCode())
            && retryPolicy.canRetry(request, attempt, elapsed)) {
          sleep(retryAfter(raw, retryPolicy.delayFor(attempt)));
          continue;
        }

        return response(
            raw, responseBytes, Duration.between(executionStarted, clock.instant()), attempt);
      } catch (RuntimeException error) {
        Duration attemptDuration = Duration.between(attemptStarted, clock.instant());
        record(
            request,
            requestHeaders,
            requestBody,
            attempt,
            null,
            attemptDuration,
            "",
            safeError(error));
        Duration elapsed = Duration.between(executionStarted, clock.instant());
        if (!retryPolicy.retryException(error)
            || !retryPolicy.canRetry(request, attempt, elapsed)) {
          throw new ApiTransportException(
              "Request failed: " + request.method() + " " + request.service() + request.path(),
              error);
        }
        sleep(retryPolicy.delayFor(attempt));
      }
    }
  }

  private RequestSpecification buildSpecification(
      ServiceConfig service, ApiRequest request, AuthMaterial auth, Map<String, String> headers) {
    HttpClientConfig http =
        HttpClientConfig.httpClientConfig()
            .setParam(
                "http.connection.timeout", Math.toIntExact(service.connectTimeout().toMillis()))
            .setParam("http.socket.timeout", Math.toIntExact(service.readTimeout().toMillis()));
    RequestSpecification specification =
        io.restassured.RestAssured.given()
            .config(RestAssuredConfig.config().httpClient(http))
            .baseUri(service.baseUri().toString())
            .headers(headers)
            .cookies(auth.cookies())
            .cookies(request.cookies())
            .queryParams(request.queryParams())
            .pathParams(request.pathParams());
    if (service.relaxedTls()) specification.relaxedHTTPSValidation();
    if (!request.formParams().isEmpty()) specification.formParams(request.formParams());
    for (MultipartPart part : request.multipartParts()) {
      specification =
          switch (part.kind()) {
            case TEXT -> specification.multiPart(part.name(), part.text(), part.contentType());
            case BYTES ->
                specification.multiPart(
                    part.name(), part.filename(), part.bytes(), part.contentType());
            case FILE ->
                specification.multiPart(part.name(), part.file().toFile(), part.contentType());
          };
    }
    if (request.body() != null) specification.body(request.body());
    return specification;
  }

  private Response dispatch(RequestSpecification specification, ApiRequest request) {
    return switch (request.method()) {
      case GET -> specification.get(request.path());
      case POST -> specification.post(request.path());
      case PUT -> specification.put(request.path());
      case PATCH -> specification.patch(request.path());
      case DELETE -> specification.delete(request.path());
      case HEAD -> specification.head(request.path());
      case OPTIONS -> specification.options(request.path());
    };
  }

  private ApiResponse response(Response raw, byte[] body, Duration duration, int attempts) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (Header header : raw.headers()) {
      headers
          .computeIfAbsent(header.getName(), ignored -> new ArrayList<>())
          .add(header.getValue());
    }
    return new ApiResponse(raw.statusCode(), headers, body, duration, attempts);
  }

  private Duration retryAfter(Response response, Duration fallback) {
    String raw = response.header("Retry-After");
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

  private void sleep(Duration duration) {
    try {
      sleeper.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiTransportException("Request retry interrupted", e);
    }
  }

  private void record(
      ApiRequest request,
      Map<String, String> headers,
      String requestBody,
      int attempt,
      Integer status,
      Duration duration,
      String responseBody,
      String error) {
    traceSink.record(
        new ExchangeTrace(
            clock.instant(),
            request.service(),
            request.actor() == null ? "anonymous" : request.actor().alias(),
            request.method().name(),
            request.path(),
            attempt,
            status,
            duration.toMillis(),
            redactor.headers(headers),
            redactor.body(requestBody),
            responseBody,
            error));
    log.debug(
        "{} {}{} -> {} (attempt {}, {} ms)",
        request.method(),
        request.service(),
        request.path(),
        status == null ? "ERROR" : status,
        attempt,
        duration.toMillis());
  }

  private String serialize(ApiRequest request) {
    if (!request.multipartParts().isEmpty()) {
      return "<multipart "
          + request.multipartParts().stream()
              .map(MultipartPart::traceMetadata)
              .collect(java.util.stream.Collectors.joining(", "))
          + ">";
    }
    Object body = request.body();
    if (body == null) return "";
    if (body instanceof String string) return redactor.body(string);
    try {
      return redactor.body(MAPPER.writeValueAsString(body));
    } catch (JsonProcessingException e) {
      return "<unserializable:" + body.getClass().getSimpleName() + ">";
    }
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
    return redactor.body(new String(body, java.nio.charset.StandardCharsets.UTF_8));
  }

  private String safeError(Throwable error) {
    return redactor.text(
        error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : ": " + error.getMessage()));
  }
}
