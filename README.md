# API Framework

Reusable Java 17 engine for API test automation. It centralizes configuration, authentication,
HTTP execution, safe retries, secret redaction, per-test context, fixture cleanup and request tracing.
Domain endpoints and business assertions stay in consumer repositories such as `api_hrm` and `api-map`.

## Modules

| Module | Responsibility |
|---|---|
| `framework-core` | Typed config, request/response API, actors, credentials, retry policy, redaction and test context |
| `framework-http-restassured` | The only RestAssured implementation and network exit point |
| `framework-fixtures` | LIFO resource cleanup, eventual-consistency polling and unique test data |
| `framework-testng` | TestNG lifecycle, shared runtime wiring and temporary redacted JSON traces |
| `starter-example` | Minimal consumer with a local fake API |

## Build

```bash
./mvnw clean verify
```

The build performs no external API calls. `starter-example` runs against an in-process HTTP server.

## Multipart and binary files

```java
ApiResponse response = api.execute(ApiRequest.post("storage", "/files")
    .multipart(MultipartPart.text("purpose", "contract"))
    .multipart(MultipartPart.file("file", Path.of("document.pdf"), "application/pdf"))
    .build());

response.requireStatus(201).saveTo(Path.of("target/downloads/result.pdf"));
```

Multipart traces contain only part name, type and byte count. Filenames and file contents are excluded. Binary responses remain byte-exact and are represented by metadata in trace output.

## Consumer usage

Add the transport and TestNG integration dependencies, build `FrameworkConfig`, register auth providers,
then expose domain-oriented clients. Tests must never call `RestAssured.given()` directly.

```java
FrameworkConfig config = FrameworkConfig.builder()
    .service(ServiceConfig.builder("hrm", System.getenv("HRM_BASE_URL"))
        .authProfile("admin")
        .header("X-Tenant-Code", System.getenv("TENANT_CODE"))
        .build())
    .build();

CredentialManager credentials = new CredentialManager(Map.of(
    "admin", actor -> AuthMaterial.bearer(login(actor), expiresAt)
));

ApiClient api = new RestAssuredApiClient(config, credentials, new CurrentTestTraceSink());
ApiTestRuntime runtime = new ApiTestRuntime(config, credentials, api);
```

The explicit trace sink keeps the HTTP transport independent from TestNG. Outside TestNG, pass another
`TraceSink` implementation or use the two-argument constructor when tracing is not required.

Define this wiring once in a project-specific base class by implementing `createRuntime()`. All consumer test
classes inherit `runtime()` and `api()`; they do not rebuild configuration or credentials per test.

See [architecture](docs/architecture.md), [configuration](docs/configuration.md),
[flow preview](docs/framework-flow-preview.md), [test design](docs/test-design.md)
and [migration](docs/migration.md).

The published [quality audit](docs/quality-audit.md) maps the implementation and verification evidence to the shared 10/10 framework rubric.

## Non-negotiable rules

- No project or domain names in framework modules.
- No secrets in properties, source, logs or reports.
- TLS verification is enabled by default.
- POST/PATCH/DELETE are not retried unless explicitly marked idempotent.
- Every request goes through `ApiClient`.
- Every created resource is registered for cleanup.
- CI must fail when tests fail.
- Absolute request URLs and credentials embedded in service URIs are rejected before transport execution.
- JSON, form/query-like text, Bearer tokens and exception messages are redacted before tracing.
