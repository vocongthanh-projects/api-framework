# API Consumer Template

Starter project độc lập để tạo repository kiểm thử API mới bằng Java 17, Maven và TestNG. Template dùng các artifact từ API Framework; không chứa source framework hay nghiệp vụ của một sản phẩm cụ thể. Có ví dụ test data CSV và một kiểm tra JDBC tùy chọn; phần DB mặc định không chạy.

## Phân chia trách nhiệm

- **API Framework**: HTTP transport, config có kiểu, credential hooks, retry, redaction, TestNG lifecycle, trace và cleanup.
- **Consumer template**: Maven/CI skeleton, BaseProjectApiTest mẫu, SampleApiClient và smoke test offline.
- **Consumer sản phẩm**: base URL, auth, DTO, endpoint/client, dữ liệu/SQL theo sản phẩm, assertions và kịch bản nghiệp vụ.

Smoke test của template tự mở JDK HTTP server trong process. Nó không cần Internet hoặc API sản phẩm và không chứng minh endpoint thật hoạt động.

## Utilities kiểm thử dùng chung

Các helper không gắn với sản phẩm đã nằm trong dependency `framework-fixtures`; không cần tạo thư mục `utils` rồi chép lại framework code vào consumer:

~~~java
String email = UniqueData.email("qa", "example.test");
String state = Eventually.within(Duration.ofSeconds(10))
        .pollEvery(Duration.ofMillis(250))
        .until(client::getState, "READY"::equals, "job completion");
resources().register("record", id, () -> client.delete(id));
~~~

`UniqueData` tạo dữ liệu định danh không trùng, `Eventually` poll theo điều kiện với timeout, và `ResourceLedger` thu dọn tài nguyên theo thứ tự ngược lúc tạo. Import các lớp từ `vn.ots.qc.api.fixture`. Helper riêng như mapper CSV theo schema, query SQL, payload builder hay auth của sản phẩm nên đặt trong package chuyên trách của consumer (`data`, `database`, `auth`, `client`), thay vì một `Utils`/`Common` class lớn.

## Trước khi bắt đầu

1. Trong GitLab project Framework API, xác nhận pipeline trên branch phát hành xanh.
2. Mở Deploy → Package Registry và xác nhận các artifact cần dùng có version 1.0.2-SNAPSHOT.
3. Nếu package chưa xuất hiện, chờ/điều tra deploy job trước khi tạo consumer dựa vào version đó.

Push source framework thành công không đồng nghĩa package đã được publish.

## Tạo repository sản phẩm từ template

Ví dụ tạo một consumer project:

1. Tạo repository GitLab rỗng cho consumer project. Nên tạo repo không có README/license để tránh phải xử lý hai lịch sử ban đầu.
2. Clone repo sản phẩm:

~~~bash
git clone "$CONSUMER_GIT_URL"
cd my-api-test
~~~

Trước đó, đặt CONSUMER_GIT_URL bằng Clone URL lấy từ GitLab hoặc thay biến bằng URL thật. Không paste nguyên chuỗi placeholder.

3. Scaffold vào repository mới đang rỗng. Script tự copy cả file ẩn như `.agents/`, `.cursor/`, `.github/`, `.gitignore` và `.gitlab-ci.yml`, đồng thời bỏ thư mục build `target/`:

~~~bash
cd /path/to/api-framework
./scripts/create-consumer-project.sh /path/to/my-api-test --package-root vn.ots.qc.api.consumer
~~~

Không nên đổi package bằng cách chỉ di chuyển thư mục `template` thủ công vì sẽ để lại các thư mục cha rỗng. Script có tùy chọn `--package-root` để đổi package và dọn namespace mẫu ngay khi khởi tạo. Nếu copy thủ công, hãy chuyển toàn bộ package root thay vì chỉ chuyển thư mục `template`.

Template giữ `.agents/AGENTS.md` và `.agents/rules/` làm bộ quy chuẩn gốc. `AGENTS.md` ở root cùng `.cursor/rules/api-testing.mdc` và `.github/copilot-instructions.md` hướng các trợ lý tương ứng đọc bộ quy chuẩn đó; Antigravity tự nạp rules trong `.agents/`. Mở đầu phiên làm việc với công cụ khác, yêu cầu trợ lý đọc `.agents/AGENTS.md` nếu phiên bản hoặc cấu hình hiện tại không tự nạp file.

4. Đổi groupId và artifactId trong pom.xml theo namespace của sản phẩm. Dùng `--package-root` khi scaffold để Java package khớp namespace dự án. Không đổi groupId/artifactId/version của các dependency framework `vn.ots.qc`.
5. Kiểm tra remote và file trước khi commit:

~~~bash
git remote -v
git status
git diff --check
~~~

6. Commit source khởi tạo và push lên branch chính mà GitLab repo sử dụng:

~~~bash
git add .
git diff --cached
git commit -m "chore: initialize API consumer from template"
git push -u origin main
~~~

Nếu project dùng develop làm branch chính, thay main bằng develop. Kiểm tra git remote -v trước push để chắc remote là repository consumer chứ không phải Framework API.

## Cài Framework từ GitLab Maven Package Registry

### GitLab CI

Template pipeline đọc các biến sau:

- API_FRAMEWORK_PROJECT_ID: ID project Framework API trên GitLab instance đang dùng.
- API_FRAMEWORK_VERSION: optional; mặc định 1.0.2-SNAPSHOT.
- CI_JOB_TOKEN: GitLab tự cấp cho job, không tạo hoặc commit token này.

Trong consumer project, mở Settings → CI/CD → Variables và đặt `API_FRAMEWORK_PROJECT_ID` bằng ID của project Framework API. Sau đó tại Framework API mở Settings → CI/CD → Job token permissions/allowlist và cho phép job token từ consumer project đọc package.

Template .gitlab-ci.yml tạo Maven settings dùng server ID api-framework-registry, username gitlab-ci-token và CI_JOB_TOKEN. Không cần đưa Personal Access Token vào CI variables. Nếu job lỗi 401/403, kiểm tra allowlist và quyền package trước khi đổi POM.

### Local Registry access

Maven settings local phải có server ID api-framework-registry, khớp repository id trong pom.xml. Credentials lưu trong ~/.m2/settings.xml, không trong repo:

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

Dùng token có quyền đọc package theo policy của GitLab tổ chức. Để tải qua project Registry, truyền URL khi chạy Maven:

~~~bash
./mvnw -U \
  -Dapi.framework.version=1.0.2-SNAPSHOT \
  -Dapi.framework.repository.url="https://<GITLAB_HOST>/api/v4/projects/<PROJECT_ID>/packages/maven" \
  clean verify
~~~

Thay host theo GitLab instance. Self-managed GitLab hiện có thể chỉ cung cấp HTTP trong một số môi trường; HTTP không mã hóa token trên đường truyền. Ưu tiên HTTPS hoặc dùng cách cài local bên dưới trong môi trường phù hợp. Không đặt token vào command line hoặc URL.

### Local framework development

Nếu đang làm việc trên checkout framework cùng máy, có thể cài toàn bộ reactor vào Maven local repository:

~~~bash
cd /path/to/api-framework
./mvnw clean install
cd /path/to/my-api-test
./mvnw clean verify
~~~

Cách này không kiểm chứng quyền đọc Package Registry. Dùng phần Registry access nếu cần xác nhận dự án mới tải được artifact đã publish.

## Cấu hình sản phẩm, auth và viết test

Template cung cấp `ConsumerConfig` để đọc theo thứ tự: system properties, environment variables, `config-local.properties`, rồi `config.properties`. Tên biến môi trường tương ứng với key dạng chữ hoa, ví dụ `auth.login-url` → `AUTH_LOGIN_URL`; environment/system property có ưu tiên cao nhất. Mẫu smoke test vẫn chạy server in-process và dùng service profile `none` nên không cần endpoint hay secret.

### Auth/session có sẵn trong consumer

`ConfiguredAuthProvider` là adapter mẫu nối cấu hình dự án với `AuthProvider` của framework. Hỗ trợ bốn kiểu phổ biến:

| `auth.mode` | Cách hoạt động | Giá trị cần cấu hình |
|---|---|---|
| `none` | Không gửi thông tin xác thực; mặc định của template | Không cần |
| `bearer-token` | Gửi `Authorization: Bearer ...` | `AUTH_TOKEN` |
| `cookie` | Gửi cookie session đã có | `AUTH_COOKIE_NAME`, `AUTH_COOKIE_VALUE` |
| `json-bearer-login` | POST JSON username/password, đọc token từ JSON response | `AUTH_LOGIN_URL`, `AUTH_USERNAME`, `AUTH_PASSWORD`; tùy chỉnh username/password field, token JSON Pointer và expiry JSON Pointer |
| `json-cookie-login` | POST JSON username/password, lấy cookie từ `Set-Cookie` response | `AUTH_LOGIN_URL`, `AUTH_USERNAME`, `AUTH_PASSWORD`, `AUTH_COOKIE_NAME`; tùy chỉnh tên field đăng nhập nếu cần |

Đặt `auth.profile=api` trong config. Base template gọi `ConsumerRuntimeFactory.createTemplateRuntime(...)`: service `sample` dùng server in-process như trước; khi `api.base-url` đã được thay placeholder, factory tự đăng ký thêm service `api` với auth mode/profile tương ứng. Smoke test offline vẫn gọi `sample`; test sản phẩm gọi service `api`, nên auth phổ biến chỉ cần thay cấu hình và viết endpoint test. Factory `createConfiguredService(environment, serviceName, traceSink)` cũng có sẵn nếu dự án muốn chỉ dựng service thật. Framework cache kết quả trong bộ nhớ theo profile/actor, dùng lại giữa các request và xóa cache khi runtime đóng. Khi nhận 401 framework có thể invalidate credential rồi gọi provider thêm một lần theo retry policy. Token/cookie không được ghi xuống file hay repo.

Ví dụ bật bearer login trong `config-local.properties` (file này được gitignore), còn thông tin bí mật truyền qua environment:

~~~properties
auth.mode=json-bearer-login
auth.profile=api
auth.login-url=https://replace-me-with-api-url.example/auth/login
auth.username-field=username
auth.password-field=password
auth.token-json-pointer=/access_token
auth.expires-in-json-pointer=/expires_in
~~~

~~~bash
export AUTH_USERNAME='<test-user>'
export AUTH_PASSWORD='<secret>'
./mvnw test
~~~

Giá trị JSON Pointer mặc định `/access_token` và `/expires_in` khớp phản hồi OAuth-style thông dụng; thay chúng theo response thật. Login mẫu gửi JSON POST và hỗ trợ một cặp username/password. OAuth client credentials, refresh-token flow riêng, CSRF, MFA, redirect login, custom signing hoặc session cookie cần protocol đặc thù thì triển khai `AuthProvider` riêng trong consumer, thay `ConfiguredAuthProvider` hoặc đăng ký thêm profile. Session cookie chỉ tự được đọc ở lần đăng nhập; framework gửi lại cookie đã cache nhưng hiện không duy trì cookie jar từ mọi response.

Trong consumer project:

- Thay BaseProjectApiTest mẫu đang dựng HTTP server in-process bằng runtime dự án thật; giữ `ConsumerRuntimeFactory` để auth profile được nối vào `CredentialManager`.
- Dùng RestAssuredApiClient làm transport qua framework.
- Viết domain client riêng, ví dụ AuthClient hoặc UserClient; client chứa endpoint và request mapping của sản phẩm.
- Viết DTO và test scenario/assertions thuộc sản phẩm.
- Lưu secret ở protected/masked CI variables hoặc secret manager. File config-local.properties được gitignore; không commit username/password/token.
- Nếu test tạo resource, đăng ký cleanup qua resources() để BaseApiTest dọn kể cả khi test fail.

### Test data từ CSV

`src/test/resources/testdata/sample-items.csv` minh họa dữ liệu dạng bảng và `CsvDataProvider` chuyển mỗi dòng thành một lần chạy TestNG. `CsvDataDrivenApiTest` dùng các dòng đó khi gọi sample API. Thay CSV, cột và DataProvider theo trường hợp của sản phẩm. CSV phù hợp với các giá trị phẳng; payload lồng nhau hoặc nhiều cấu trúc thường dễ quản lý hơn dưới dạng JSON/DTO. Không lưu password, token hay dữ liệu cá nhân thật trong test data đã commit.

Chạy các smoke test mặc định (không cần database):

~~~bash
./mvnw clean verify
~~~

### SQL/JDBC tùy chọn

Database testing không thuộc framework core vì schema, SQL và JDBC driver gắn với sản phẩm. Module `framework-sql` cung cấp `SqlClient` trung lập vendor, dùng prepared statement và mở/đóng một connection cho mỗi thao tác; consumer thêm driver tương ứng. `DatabaseSmokeTest` chạy câu read-only trong `src/test/resources/sql/database-smoke.sql` và bị loại khỏi build mặc định. Client phù hợp với vài truy vấn kiểm chứng; suite có tải lớn hoặc chạy song song nên consumer cân nhắc cung cấp `DataSource`/connection pool riêng.

Để bật cho một consumer có quyền dùng test database:

1. Thêm JDBC driver đúng loại DB vào `pom.xml` của consumer theo phiên bản được tổ chức phê duyệt. Template không chọn driver thay dự án.
2. Cấu hình `DB_JDBC_URL`, `DB_USERNAME`, `DB_PASSWORD` trong environment local; trên CI dùng protected/masked variables hoặc secret manager.
3. Chạy riêng nhóm database:

~~~bash
DB_JDBC_URL='jdbc:<vendor>://<host>:<port>/<database>' \
DB_USERNAME='<test-user>' DB_PASSWORD='<secret>' \
./mvnw -Pdatabase-tests test
~~~

Profile chỉ chọn TestNG group `database`; nó không tạo database, cấp credentials hay tự thêm vendor JDBC driver. Thay `SELECT 1` bằng truy vấn read-only của sản phẩm để xác nhận dữ liệu sau API call. Giới hạn DB account vào test environment và quyền tối thiểu cần thiết; không chạy SQL thay đổi/xóa dữ liệu trên môi trường dùng chung nếu chưa có thiết kế setup/cleanup rõ ràng. SQL files và DB assertions theo schema luôn nằm trong consumer.

Trong GitLab CI, có thể thêm job riêng chạy `./mvnw -Pdatabase-tests test` với protected variables và runner có network access tới test DB. Không bật profile này cho pipeline mặc định nếu project không cấu hình database.

Trong repository API Framework, xem docs/configuration.md, docs/architecture.md và docs/test-design.md để biết thêm chi tiết về cấu hình và thiết kế test.

## Chạy và đọc kết quả

Chạy smoke test offline của template:

~~~bash
./mvnw clean verify
~~~

Chạy riêng smoke:

~~~bash
./mvnw -Dtest=SmokeApiTest test
~~~

Khi thêm test sản phẩm thật, các test đó có thể cần endpoint và credential riêng. Surefire XML nằm ở target/surefire-reports/; trace redacted ở target/api-traces/.

Template chạy tuần tự mặc định. Sau khi test đã độc lập dữ liệu, bật parallel theo class với giới hạn bốn worker:

~~~bash
./mvnw -Dapi.test.parallel=classes -Dapi.test.thread-count=4 clean verify
~~~

Có thể dùng `-Dapi.test.parallel=methods` khi từng lần chạy tự sở hữu dữ liệu và không sửa state dùng chung trong test class. Giữ các luồng destructive hoặc dùng chung tenant ở chế độ tuần tự. `BaseApiTest` tạo context, cleanup ledger và trace ID riêng cho mỗi lần gọi test.

## Dependency/version và phát hành

- Template hiện khai báo framework version 1.0.2-SNAPSHOT.
- develop của API Framework publish snapshot; tag vX.Y.Z phát hành version ổn định X.Y.Z.
- Với pipeline sản phẩm cần tái lập ổn định, chọn version release đã xuất hiện trong Package Registry thay vì SNAPSHOT.
- Khi cập nhật snapshot, chạy Maven với -U để yêu cầu kiểm tra metadata mới; không dùng -U như cách sửa lỗi quyền truy cập.
