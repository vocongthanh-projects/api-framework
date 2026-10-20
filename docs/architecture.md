# Architecture

## Dependency direction

```text
consumer tests
  -> consumer scenarios / fixtures / assertions
  -> consumer domain clients
  -> framework ApiClient
  -> RestAssured or JDK async transport
  -> backend services
```

Framework modules never import consumer code. `framework-core` has no dependency on RestAssured or TestNG.
This keeps the public API portable and prevents each project from forking its own request executor.

## Request pipeline

1. Resolve the named service and its base URI.
2. Resolve credentials by auth profile and actor alias.
3. Merge service, auth and request headers.
4. Create a fresh request specification for the attempt.
5. Execute and capture a redacted exchange trace; multipart and binary payloads use metadata only.
6. Refresh credentials once after `401`.
7. Apply safe retry policy where eligible.
8. Return an implementation-neutral `ApiResponse`.

Request-specific headers override service and auth headers to support explicit negative tests. Consumers must
use that capability carefully and must never insert credentials directly in source.

## State and parallelism

- Credentials are cached by `(auth profile, actor alias)` in a concurrent map.
- Server-set cookies are kept in an in-memory jar keyed by `(service, auth profile, actor alias)`;
  cookie domain, path, secure and expiry rules are applied by the JDK cookie store. Clear a single
  jar with `ApiClient.clearSession(service, actor)` or all jars with `clearSessions()`.
- `TestContext` and `ResourceLedger` are stored per executing thread and created for each test invocation.
- `TestContextHolder` is thread-local only inside the TestNG integration and is always removed after a test.
- The transport accepts a `TraceSink`; `CurrentTestTraceSink` is the explicit TestNG-to-transport bridge.
- Resource cleanup state belongs to the individual test context.
- No mutable global domain state is allowed.

The consumer template keeps tests sequential by default. Set `-Dapi.test.parallel=classes` to enable bounded
class-level parallelism (four workers by default). The same switch accepts `methods`, but use it only when each
test invocation owns its data and does not mutate shared test-instance or static state. Shared tenant data and
destructive workflows should remain serial or use isolated data per invocation. Trace reports include a unique
invocation identifier so parallel and repeated executions do not overwrite one another.

## Extension points

- Implement `AuthProvider` for Keycloak, encrypted login, cookies or API keys.
- Add service default headers through `ServiceConfig`.
- Wrap `ApiClient` in consumer domain clients.
- `framework-allure` is an optional TestNG adapter that attaches each invocation's redacted API trace to Allure.
- `framework-http-jdk` implements both `ApiClient` and `AsyncApiClient`; `executeAsync` uses JDK `HttpClient.sendAsync` and preserves the same retry, credential, cookie and trace behavior.
- `framework-contract` validates response JSON against consumer-owned JSON Schemas and reports failing instance paths without echoing response bodies.
- `RetryMode.IDEMPOTENT` requires both an endpoint guarantee and an `Idempotency-Key` header.
- `MultipartPart` keeps files transport-neutral; `ApiResponse.bytes()` and `saveTo()` preserve binary output.
