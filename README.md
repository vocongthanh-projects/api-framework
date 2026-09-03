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

## How to import into your project

Add the GitLab Package Registry and framework dependencies to your consumer project's `pom.xml`:

```xml
<repositories>
    <repository>
        <id>gitlab-maven</id>
        <url>http://10.0.0.40/api/v4/groups/qc-quality-control/-/packages/maven</url>
    </repository>
</repositories>

<dependencies>
    <!-- Core TestNG Lifecycle & Tracing -->
    <dependency>
        <groupId>vn.ots.qc</groupId>
        <artifactId>framework-testng</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- HTTP Transport with auto-retry, secret redaction, auto-401 recovery -->
    <dependency>
        <groupId>vn.ots.qc</groupId>
        <artifactId>framework-http-restassured</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Fixtures: eventual-consistency polling, LIFO cleanup, unique data -->
    <dependency>
        <groupId>vn.ots.qc</groupId>
        <artifactId>framework-fixtures</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Authentication for GitLab Package Registry (If private)
If pulling packages from GitLab requires authentication, configure your Personal Access Token in `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>gitlab-maven</id>
            <configuration>
                <httpHeaders>
                    <property>
                        <name>Private-Token</name>
                        <value>${env.GITLAB_PRIVATE_TOKEN}</value>
                    </property>
                </httpHeaders>
            </configuration>
        </server>
    </servers>
</settings>
```

### Versioning & Release Strategy
- **Continuous Integration (`develop` branch):** Automatically publishes `1.0.0-SNAPSHOT` for local verification and development.
- **Production Releases (Git Tags):** Pushing a Git tag (e.g. `git tag v1.0.0 && git push origin v1.0.0`) triggers the pipeline to dynamically bump the reactor version to `1.0.0` before deploying to the Package Registry.
- **Consumer Recommendation:** Pin a concrete release version (e.g. `1.0.0` or `1.1.0`) instead of `SNAPSHOT` in production consumer repositories (`api_hrm`, `api-map`) to ensure immutable, reliable test execution.

## Consumer usage & Step-by-step Guide

### 1. Define Project Base Test
Extend `BaseApiTest` and implement `createRuntime()`. Wire `FrameworkConfig`, `CredentialManager`, and your domain clients once:

```java
public abstract class BaseProjectApiTest extends BaseApiTest {
    protected UserClient userClient;

    @Override
    protected ApiTestRuntime createRuntime() {
        FrameworkConfig config = FrameworkConfig.builder()
                .service(ServiceConfig.builder("userService", Config.getBaseUrl())
                        .authProfile("admin")
                        .header("Content-Type", "application/json")
                        .build())
                .build();

        CredentialManager credentials = new CredentialManager(Map.of(
                "admin", actor -> AuthMaterial.bearer(login(actor), expiresAt)
        ));

        ApiClient api = new RestAssuredApiClient(config, credentials, new CurrentTestTraceSink());
        userClient = new UserClient(api);

        return new ApiTestRuntime(config, credentials, api);
    }
}
```

### 2. Create Domain Clients
Wrap `ApiClient` inside domain-oriented clients. Clients only handle serialization, method, and endpoints:

```java
public final class UserClient {
    private final ApiClient api;

    public UserClient(ApiClient api) { this.api = api; }

    public ApiResponse getUser(Actor actor, String userId) {
        return api.execute(ApiRequest.get("userService", "/api/v1/users/" + userId)
                .actor(actor)
                .build());
    }

    public ApiResponse createUser(Actor actor, UserRequest body) {
        return api.execute(ApiRequest.post("userService", "/api/v1/users")
                .actor(actor)
                .body(body)
                .build());
    }
}
```

### 3. Write Test Classes
Tests inherit `runtime()`, `api()`, `context()`, and domain clients:

```java
public class UserApiTest extends BaseProjectApiTest {

    @Test
    public void testGetUserSuccess() {
        Actor admin = Actor.of("admin-user", "admin");
        context().actor(admin);

        ApiResponse response = userClient.getUser(admin, "user-123");
        response.requireStatus(200);

        Assert.assertEquals(response.json().path("data.status").asText(), "ACTIVE");
    }
}
```

### 4. Running & Viewing Traces
- **Run all tests:**
  ```bash
  ./mvnw test
  ```
- **Run with custom environment URL:**
  ```bash
  ./mvnw test -Dbase.url="http://qc-gtelmaps-maps.gtelots.dev"
  ```
- **Run specific test group or class:**
  ```bash
  ./mvnw test -Dgroups="smoke"
  ./mvnw test -Dtest="UserApiTest"
  ```
- **Redacted JSON traces** for every request/response are automatically captured in `target/api-traces/`.

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
