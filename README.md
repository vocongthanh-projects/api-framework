# API Framework

Framework Java 17 dùng chung cho API test automation. Framework cung cấp transport, config có kiểu, authentication hooks, retry an toàn, redaction, test lifecycle, cleanup và trace. Endpoint, DTO, credential, test data và nghiệp vụ thuộc về repository consumer.

## Modules và ví dụ

| Module | Trách nhiệm |
|---|---|
| framework-core | Config, request/response, actor, credential, retry, redaction và test context |
| framework-http-restassured | RestAssured transport dùng chung |
| framework-http-jdk | JDK HTTP transport có API `CompletableFuture` non-blocking |
| framework-fixtures | Cleanup LIFO, eventual-consistency polling và test data unique |
| framework-testng | TestNG lifecycle, runtime wiring và trace |
| framework-sql | JDBC client trung lập vendor cho SQL assertions |
| framework-nosql | MongoDB client và query loader cho NoSQL assertions |
| framework-allure | Listener tùy chọn đính kèm API trace vào từng kết quả Allure/TestNG |
| framework-contract | JSON Schema validation cho response JSON và schema tham chiếu |
| starter-example | Contract example trong reactor; không phải template cho sản phẩm mới |
| template-consumer-api/ | Starter project độc lập để tạo repository consumer mới |

Template không kế thừa Maven parent của framework. Dự án consumer khai báo các framework modules dưới dạng dependency.

### Utilities dùng chung và utilities theo sản phẩm

Framework đã có các tiện ích kiểm thử dùng chung trong `framework-fixtures`:

- `UniqueData.value(...)` / `UniqueData.email(...)` tạo dữ liệu định danh duy nhất để tránh va chạm giữa test chạy song song.
- `Eventually.within(...).pollEvery(...).until(...)` chờ điều kiện cuối cùng đúng thay cho `Thread.sleep` cố định.
- `ResourceLedger` đăng ký cleanup và chạy theo thứ tự LIFO; `BaseApiTest` cấp ledger theo từng test.

Template chạy tuần tự mặc định. Có thể bật TestNG class parallelism giới hạn bốn worker bằng
`-Dapi.test.parallel=classes`; chỉ bật sau khi các test đã cô lập dữ liệu. `BaseApiTest` giữ context và cleanup
ledger theo thread, còn trace có ID riêng cho mỗi invocation. Xem [hướng dẫn template](template-consumer-api/README.md)
để biết cách bật parallel và giới hạn worker.

Ví dụ:

~~~java
String email = UniqueData.email("qa", "example.test");
String status = Eventually.within(Duration.ofSeconds(10))
        .pollEvery(Duration.ofMillis(250))
        .until(client::getStatus, "READY"::equals, "job completion");
resources().register("user", userId, () -> client.deleteUser(userId));
~~~

Không có module `utils` tổng hợp có chủ ý: tiện ích chỉ nên vào framework khi có hợp đồng ổn định, không gắn với schema/API cụ thể và được nhiều consumer dùng. CSV parsing, DTO mapping, câu SQL, JDBC driver, JSON fixtures theo sản phẩm, auth đặc thù và builders nghiệp vụ thuộc consumer; module SQL chỉ cung cấp API kết nối/chạy query chung.

`framework-allure` là dependency tùy chọn cho consumer cần báo cáo Allure. Module đính kèm trace JSON đã được redaction vào từng kết quả TestNG; report JSON độc lập trong `target/api-traces` vẫn được ghi như trước.

`framework-http-jdk` cung cấp transport async cho consumer cần gửi request không chặn thread. Nó dùng cùng `ApiRequest`/`ApiResponse`, retry, auth, cookie jar và trace contracts; cấu hình TLS luôn cần certificate hợp lệ. Có thể gọi `executeAsync(request)` để giữ luồng async hoặc `execute(request)` khi cần API đồng bộ.

~~~java
JdkAsyncApiClient client = new JdkAsyncApiClient(config, credentials, new CurrentTestTraceSink());
CompletableFuture<ApiResponse> pending = client.executeAsync(ApiRequest.get("hrm", "/health").build());
~~~

`framework-contract` hỗ trợ kiểm tra response theo JSON Schema Draft 2020-12 mặc định, đồng thời tôn trọng dialect khai báo trong schema. Schema được consumer quản lý và nạp qua URI để có thể dùng `$ref` giữa các schema; mọi schema tham chiếu phải nằm trong map đã truyền vào và không có tải schema qua mạng. Lỗi chỉ hiển thị JSON path và quy tắc vi phạm, không in toàn bộ response.

## Build và kiểm thử framework

Yêu cầu JDK 17. Tại root framework:

~~~bash
./mvnw clean verify
~~~

Bộ verify chạy offline, không gọi API sản phẩm. Starter example dùng HTTP server in-process. Pipeline Framework API chạy validate, test rồi deploy.

## Tạo dự án consumer mới

**Khuyến nghị dùng template-consumer-api**, không copy starter-example. Các bước đầy đủ, lệnh clone/copy, config Maven, CI variables, smoke test và xử lý lỗi nằm trong [README của template](template-consumer-api/README.md).

Tóm tắt:

1. Chờ pipeline của Framework API trên develop xanh và xác nhận Package Registry đã có version cần dùng. Source push thành công chưa có nghĩa package đã publish.
2. Tạo repository GitLab rỗng cho sản phẩm, clone về máy.
3. Scaffold project bằng script (script từ chối destination không rỗng, copy cả file/thư mục ẩn và bỏ Maven `target/`):

~~~bash
./scripts/create-consumer-project.sh /path/to/new-api-test --package-root vn.ots.qc.api.newproject
~~~

Template đã chứa `.agents/`, `AGENTS.md`, `.cursor/` và `.github/copilot-instructions.md`; các file này được giữ lại trong project mới.
4. Đổi Maven coordinates của consumer; không đổi framework GAV nếu không có lý do.
5. Cấu hình quyền đọc Maven package trong local Maven settings hoặc GitLab CI Job Token.
6. Viết client, config và test nghiệp vụ trong consumer repository; giữ framework modules ở vai trò dependency.
7. Chạy verify, commit/push đúng remote rồi xác nhận consumer pipeline.

Project ID của Framework Package Registry được truyền qua biến CI `API_FRAMEWORK_PROJECT_ID`, không hard-code vào POM. Lấy ID từ cấu hình GitLab của instance đang dùng. Đưa consumer project vào job-token allowlist của Framework API để CI consumer đọc được package.

## Dùng Framework API trực tiếp

Nếu dự án đã có Maven structure riêng, có thể khai báo project-scoped Maven repository cùng các modules cần dùng:

~~~xml
<repositories>
    <repository>
        <id>api-framework-registry</id>
        <url>https://your-gitlab-host/api/v4/projects/<PROJECT_ID>/packages/maven</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>vn.ots.qc</groupId>
        <artifactId>framework-core</artifactId>
        <version>1.0.2-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>vn.ots.qc</groupId>
        <artifactId>framework-fixtures</artifactId>
        <version>1.0.2-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>vn.ots.qc</groupId>
        <artifactId>framework-testng</artifactId>
        <version>1.0.2-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>vn.ots.qc</groupId>
        <artifactId>framework-http-restassured</artifactId>
        <version>1.0.2-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
~~~

Dùng Registry qua Maven khi muốn kiểm chứng việc tải package độc lập. Local Maven server ID phải khớp repository ID là api-framework-registry. Lưu credentials ngoài source, trong ~/.m2/settings.xml hoặc secret manager; token chỉ nên có quyền đọc package:

~~~xml
<settings>
  <servers>
    <server>
      <id>api-framework-registry</id>
      <username>YOUR_GITLAB_USERNAME_OR_TOKEN_USER</username>
      <password>YOUR_READ_PACKAGE_TOKEN</password>
    </server>
  </servers>
</settings>
~~~

Có thể truyền repository URL khi build nếu consumer POM dùng profile của template:

~~~bash
./mvnw -U \
  -Dapi.framework.version=1.0.2-SNAPSHOT \
  -Dapi.framework.repository.url="https://<GITLAB_HOST>/api/v4/projects/<PROJECT_ID>/packages/maven" \
  clean verify
~~~

Nếu đang phát triển Framework API local, thay vì đọc package Registry có thể cài reactor vào local Maven repository rồi build template:

~~~bash
cd /path/to/api-framework
./mvnw clean install
cd template-consumer-api
./mvnw clean verify
~~~

GitLab self-managed trong môi trường hiện tại đang dùng HTTP ở một số endpoint. HTTP không mã hóa credentials trên đường truyền. Ưu tiên HTTPS khi server hỗ trợ; CI dùng CI_JOB_TOKEN và không lưu token trong POM hoặc source.

## Cấu hình và viết test consumer

`template-consumer-api` hiện có `ConsumerConfig`, `ConfiguredAuthProvider`, `ConsumerRuntimeFactory`, `BaseProjectApiTest`, `SampleApiClient` và smoke test offline. Config loader của **template** đọc system properties, environment variables, `config-local.properties`, rồi `src/test/resources/config.properties`; các key như `auth.mode` map thành `AUTH_MODE`. Framework core không tự đọc cấu hình sản phẩm.

Auth adapter của template hỗ trợ token Bearer/cookie tĩnh và login JSON cơ bản. Consumer có giao thức khác như GConnect OAuth cần `AuthProvider` riêng trong consumer. Token và cookie jar được giữ trong bộ nhớ theo service/profile/actor trong vòng đời runtime; không ghi xuống đĩa và được xóa khi runtime đóng. Có thể xóa session riêng bằng `api().clearSession("api", actor)` hoặc xóa toàn bộ bằng `api().clearSessions()`. Xem [auth/session trong README template](template-consumer-api/README.md#authsession-có-sẵn-trong-consumer).

Domain client như UserClient bọc `ApiClient` và giữ method/path/serialization của sản phẩm; test scenario gọi client rồi assert response. Resource test tạo ra đăng ký qua `resources()` để framework cleanup kể cả khi assertion fail. Không gọi RestAssured trực tiếp trong test và không commit URL có credential, password, token hoặc response nhạy cảm.

Template có ví dụ CSV/TestNG DataProvider và SQL/JDBC tùy chọn, mặc định không chạy. `framework-sql` cung cấp JDBC client dùng chung; SQL, JDBC driver và assertions theo schema thuộc consumer. NoSQL module hiện triển khai MongoDB với find/count/command; JSON query placeholder được escape theo ngữ cảnh và tham số thiếu báo lỗi. Xem mục [CSV và SQL/JDBC](template-consumer-api/README.md#test-data-từ-csv).

### Quy chuẩn cho trợ lý AI

Template đóng gói `.agents/AGENTS.md` và các rule trong `.agents/rules/` làm nguồn chuẩn. `AGENTS.md` ở root hướng Cursor/Antigravity/Codex tới bộ quy chuẩn; `.cursor/rules/` và `.github/copilot-instructions.md` là điểm vào theo convention của từng công cụ, yêu cầu đọc cùng nguồn `.agents/`. Khi scaffold bằng script hoặc lệnh copy `template-consumer-api/.`, các file ẩn này được giữ nguyên.

Xem [configuration](docs/configuration.md), [architecture](docs/architecture.md), [test design](docs/test-design.md), [CI/CD](docs/ci-cd.md), [migration](docs/migration.md) và [quality audit](docs/quality-audit.md).

## Versioning và release

- Push/merge lên develop kích hoạt pipeline; snapshot hiện trong source là 1.0.2-SNAPSHOT.
- Pipeline deploy dùng project ID và API URL do GitLab CI cung cấp, xác thực bằng CI_JOB_TOKEN qua ci_settings.xml.
- Tag release vX.Y.Z kích hoạt pipeline đổi version reactor thành X.Y.Z rồi deploy.
- Dùng release version đã kiểm tra trên Registry cho consumer cần build lặp lại ổn định. Snapshot là version phát triển có thể thay đổi.

## Quy tắc an toàn

- Không đưa nghiệp vụ sản phẩm hoặc credentials vào framework.
- TLS verification bật mặc định.
- Mọi HTTP request đi qua ApiClient.
- POST/PATCH/DELETE không retry nếu chưa được xác nhận idempotent.
- Mọi resource test tạo ra cần cleanup.
- CI phải fail khi Java verify/test fail.
- Trace phải redacted trước khi lưu artifact.
