# API Framework

Engine Java 17 có thể tái sử dụng cho API test automation. Tập trung hóa cấu hình, authentication,
thực thi HTTP, retry an toàn, redact secret, context theo từng test, dọn fixture, và trace request.
Endpoint domain và assertion nghiệp vụ nằm ở repo consumer riêng của bạn.

## Module

| Module | Trách nhiệm |
|---|---|
| `framework-core` | Config có kiểu, API request/response, actor, credential, retry policy, redaction, và test context |
| `framework-http-restassured` | Triển khai RestAssured duy nhất và điểm thoát ra mạng |
| `framework-fixtures` | Dọn resource kiểu LIFO, polling eventual-consistency, và test data unique |
| `framework-testng` | Lifecycle TestNG, wiring runtime dùng chung, và trace JSON redact tạm thời |
| `starter-example` | Consumer tối giản với 1 fake API local |

## Build

```bash
./mvnw clean verify
```

Build không gọi API bên ngoài nào. `starter-example` chạy trên 1 HTTP server in-process.

## File multipart và binary

```java
ApiResponse response = api.execute(ApiRequest.post("storage", "/files")
    .multipart(MultipartPart.text("purpose", "contract"))
    .multipart(MultipartPart.file("file", Path.of("document.pdf"), "application/pdf"))
    .build());

response.requireStatus(201).saveTo(Path.of("target/downloads/result.pdf"));
```

Trace multipart chỉ chứa tên phần, loại, và số byte. Tên file và nội dung file bị loại trừ. Response binary giữ nguyên byte-exact và được biểu diễn bằng metadata trong output trace.

## Cách import vào dự án của bạn

Repo này không publish lên Maven Central hay registry công khai nào. Cách dùng nhanh nhất là
clone và install vào local Maven repository của bạn:

```bash
git clone https://github.com/vocongthanh247/api-framework.git
cd api-framework
./mvnw clean install
```

Sau đó thêm dependency vào `pom.xml` của dự án consumer:

```xml
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

### Chiến lược versioning & release
- **Continuous Integration (branch `develop`):** Tự động publish `1.0.0-SNAPSHOT` để kiểm thử và phát triển local.
- **Production Releases (Git Tag):** Push 1 Git tag (vd. `git tag v1.0.0 && git push origin v1.0.0`) sẽ kích hoạt pipeline bump version reactor lên `1.0.0` trước khi deploy.
- **Khuyến nghị cho consumer:** Pin đúng 1 version release cụ thể (vd. `1.0.0` hoặc `1.1.0`) thay vì `SNAPSHOT` ở môi trường production để đảm bảo test chạy bất biến, đáng tin cậy.

### Cấu trúc dự án consumer

`starter-example` trong repo này là 1 ví dụ thật, build được — hãy copy theo layout đó:

```text
your-app-repo/
├── src/main/java/com/yourcompany/
│   └── clients/            # domain client bọc ApiClient, vd. UserClient
├── src/test/java/com/yourcompany/
│   ├── base/                 # BaseProjectApiTest (extends BaseApiTest)
│   └── tests/                 # class test TestNG
└── pom.xml
```

## Hướng dẫn dùng cho consumer, từng bước

### 1. Định nghĩa Base Test của dự án
Extend `BaseApiTest` và implement `createRuntime()`. Wire `FrameworkConfig`, `CredentialManager`, và domain client của bạn 1 lần duy nhất:

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

### 2. Tạo Domain Client
Bọc `ApiClient` bên trong các client theo domain. Client chỉ xử lý serialization, method, và endpoint:

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

### 3. Viết Test Class
Test kế thừa `runtime()`, `api()`, `context()`, và domain client:

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

### 4. Chạy & xem Trace
- **Chạy tất cả test:**
  ```bash
  ./mvnw test
  ```
- **Chạy với URL môi trường tùy chỉnh:**
  ```bash
  ./mvnw test -Dbase.url="https://your-application.example"
  ```
- **Chạy 1 group hoặc class test cụ thể:**
  ```bash
  ./mvnw test -Dgroups="smoke"
  ./mvnw test -Dtest="UserApiTest"
  ```
- **Trace JSON đã redact** cho mọi request/response được tự động thu vào `target/api-traces/`.

Xem thêm [architecture](docs/architecture.md), [configuration](docs/configuration.md),
[flow preview](docs/framework-flow-preview.md), [test design](docs/test-design.md)
và [migration](docs/migration.md).

[Quality audit](docs/quality-audit.md) đã publish, đối chiếu implementation và bằng chứng kiểm thử với rubric chung 10/10 của framework.

## Quy tắc không thỏa hiệp

- Không tên dự án hay domain nào trong module framework.
- Không secret trong properties, source, log, hay report.
- TLS verification bật mặc định.
- POST/PATCH/DELETE không được retry trừ khi được đánh dấu tường minh là idempotent.
- Mọi request đều đi qua `ApiClient`.
- Mọi resource được tạo ra đều được đăng ký để dọn dẹp.
- CI phải fail khi test fail.
- URL request tuyệt đối và credential nhúng trong service URI bị từ chối trước khi thực thi transport.
- JSON, text kiểu form/query, Bearer token, và exception message được redact trước khi trace.
