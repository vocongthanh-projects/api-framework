# Architecture

## Dependency direction

```text
consumer tests
  -> consumer scenarios / fixtures / assertions
  -> consumer domain clients
  -> framework ApiClient
  -> RestAssured transport
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
- `TestContext` is created per test method.
- `TestContextHolder` is thread-local only inside the TestNG integration and is always removed after a test.
- The transport accepts a `TraceSink`; `CurrentTestTraceSink` is the explicit TestNG-to-transport bridge.
- Resource cleanup state belongs to the individual test context.
- No mutable global domain state is allowed.

The first safe parallel mode for migrated projects is TestNG `parallel="classes"`. Method-level parallelism
requires every method to own all actors and resources and is not the default.

## Extension points

- Implement `AuthProvider` for Keycloak, encrypted login, cookies or API keys.
- Add service default headers through `ServiceConfig`.
- Wrap `ApiClient` in consumer domain clients.
- Add an Allure bridge only after the first consumer pilot defines the report requirements.
- `RetryMode.IDEMPOTENT` requires both an endpoint guarantee and an `Idempotency-Key` header.
- `MultipartPart` keeps files transport-neutral; `ApiResponse.bytes()` and `saveTo()` preserve binary output.
