# API Consumer Template

Starter project độc lập để tạo repository kiểm thử API mới bằng Java 17, Maven và TestNG. Template dùng các artifact từ API Framework; không chứa source framework hay nghiệp vụ của một sản phẩm cụ thể. Có ví dụ test data CSV và một kiểm tra JDBC tùy chọn; phần DB mặc định không chạy.

## Phân chia trách nhiệm

- **API Framework**: HTTP transport, config có kiểu, credential hooks, retry, redaction, TestNG lifecycle, trace và cleanup.
- **Consumer template**: Maven/CI skeleton, BaseProjectApiTest mẫu, SampleApiClient và smoke test offline.
- **Consumer sản phẩm**: base URL, auth, DTO, endpoint/client, dữ liệu/SQL theo sản phẩm, assertions và kịch bản nghiệp vụ.

Smoke test của template tự mở JDK HTTP server trong process. Nó không cần Internet hoặc API sản phẩm và không chứng minh endpoint thật hoạt động.

## Trước khi bắt đầu

1. Trong GitLab project Framework API (ID 1263), xác nhận pipeline trên develop xanh.
2. Mở Deploy → Package Registry và xác nhận các artifact cần dùng có version 1.0.1-SNAPSHOT.
3. Nếu package chưa xuất hiện, chờ/điều tra deploy job trước khi tạo consumer dựa vào version đó.

Push source framework thành công không đồng nghĩa package đã được publish.

## Tạo repository sản phẩm từ template

Ví dụ tạo api-g-connect:

1. Tạo repository GitLab rỗng cho api-g-connect. Nên tạo repo không có README/license để tránh phải xử lý hai lịch sử ban đầu.
2. Clone repo sản phẩm:

~~~bash
git clone "$API_G_CONNECT_GIT_URL"
cd api-g-connect
~~~

Trước đó, đặt API_G_CONNECT_GIT_URL bằng Clone URL lấy từ GitLab hoặc thay biến bằng URL thật. Không paste nguyên chuỗi placeholder.

3. Copy toàn bộ template vào root của repo mới. Dấu chấm cuối giúp copy cả file ẩn như .gitignore và .gitlab-ci.yml:

~~~bash
cp -R /Users/thanhvc/Workspace/api-framework/template-consumer-api/. .
~~~

4. Đổi groupId và artifactId trong pom.xml theo namespace của sản phẩm. Không đổi groupId/artifactId/version của các dependency vn.ots.qc.
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

Nếu project dùng develop làm branch chính, thay main bằng develop. Kiểm tra git remote -v trước push để chắc remote là api-g-connect chứ không phải Framework API.

## Cài Framework từ GitLab Maven Package Registry

### GitLab CI

Template pipeline đọc các biến sau:

- API_FRAMEWORK_PROJECT_ID: ID project Framework API; giá trị hiện tại là 1263.
- API_FRAMEWORK_VERSION: optional; mặc định 1.0.1-SNAPSHOT.
- CI_JOB_TOKEN: GitLab tự cấp cho job, không tạo hoặc commit token này.

Trong consumer project, mở Settings → CI/CD → Variables và tạo API_FRAMEWORK_PROJECT_ID=1263. Sau đó tại Framework API mở Settings → CI/CD → Job token permissions/allowlist và cho phép job token từ project api-g-connect đọc package.

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
  -Dapi.framework.version=1.0.1-SNAPSHOT \
  -Dapi.framework.repository.url="https://<GITLAB_HOST>/api/v4/projects/1263/packages/maven" \
  clean verify
~~~

Thay host theo GitLab instance. Self-managed GitLab hiện có thể chỉ cung cấp HTTP trong một số môi trường; HTTP không mã hóa token trên đường truyền. Ưu tiên HTTPS hoặc dùng cách cài local bên dưới trong môi trường phù hợp. Không đặt token vào command line hoặc URL.

### Local framework development

Nếu đang làm việc trên checkout framework cùng máy, có thể cài toàn bộ reactor vào Maven local repository:

~~~bash
cd /Users/thanhvc/Workspace/api-framework
./mvnw clean install
cd /PATH/TO/api-g-connect
./mvnw clean verify
~~~

Cách này không kiểm chứng quyền đọc Package Registry. Dùng phần Registry access nếu cần xác nhận dự án mới tải được artifact đã publish.

## Cấu hình sản phẩm và viết test

Các file config.properties và config-local.properties.example chỉ là ví dụ placeholder. Framework không tự đọc các key tùy ý như api.base-url. Consumer cần đọc config từ environment/config provider rồi dựng FrameworkConfig và CredentialManager trong BaseProjectApiTest.createRuntime().

Trong api-g-connect:

- Thay BaseProjectApiTest mẫu đang dựng HTTP server in-process bằng runtime dự án thật.
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

Database testing không thuộc framework core vì schema, SQL và JDBC driver gắn với sản phẩm. Template chỉ đưa ví dụ JDBC tối giản phía consumer: `JdbcQueryClient` dùng prepared statement và mở/đóng một connection cho mỗi query; `DatabaseSmokeTest` chạy câu read-only trong `src/test/resources/sql/database-smoke.sql`. Bài này bị loại khỏi build mặc định. Ví dụ này hợp với vài truy vấn kiểm chứng; nếu suite tạo nhiều truy vấn hoặc chạy song song, consumer nên cấp một `DataSource`/connection pool riêng. JDBC khuyến nghị `DataSource` khi phù hợp với kiến trúc ứng dụng.

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

## Dependency/version và phát hành

- Template hiện khai báo framework version 1.0.1-SNAPSHOT.
- develop của API Framework publish snapshot; tag vX.Y.Z phát hành version ổn định X.Y.Z.
- Với pipeline sản phẩm cần tái lập ổn định, chọn version release đã xuất hiện trong Package Registry thay vì SNAPSHOT.
- Khi cập nhật snapshot, chạy Maven với -U để yêu cầu kiểm tra metadata mới; không dùng -U như cách sửa lỗi quyền truy cập.
