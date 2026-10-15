# Quy chuẩn Kiểm thử API & Kiến trúc Dự án (API Testing Standards)

Quy tắc bắt buộc cho cả Kỹ sư kiểm thử và Trợ lý AI khi phát triển / bảo trì test suite:

---

## 1. NGUYÊN TẮC TỔ CHỨC TEST CLASS (CHỐNG LỘN XỘN & ĐẺ TRÀN LAN CLASS)
- **1 Feature / Domain = 1 Test Suite duy nhất**:
  - Tên class chuẩn: `<Feature>ApiTest.java` (Ví dụ: `CredentialApiTest`, `OrderSyncApiTest`, `AuthApiTest`).
  - **NGHIÊM CẤM** tách nhỏ đẻ ra nhiều class cho cùng một tính năng:
    - ❌ CẤM: `CredentialLiveTest`, `CredentialCsvTest`, `CredentialSearchTest`, `CredentialSmokeTest`.
    - ✅ CHUẨN: Tất cả phương thức kiểm thử của feature đó gom vào duy nhất `CredentialApiTest`.
- **Phân nhóm TestNG chuẩn**:
  - `smoke`: Kiểm tra tính khả dụng cơ bản (health check, ping, list tối thiểu).
  - `integration`: Kịch bản tích hợp nghiệp vụ đầy đủ (tạo, cập nhật, tìm kiếm, xác thực API).
  - `database`: Kiểm tra dữ liệu trực tiếp trong DB (chỉ chạy với profile `-Pdatabase-tests` hoặc group tương ứng).

---

### 1.1 NGUYÊN TẮC THIẾT KẾ DOMAIN API CLIENT VÀ RANH GIỚI MODEL
- **Mỗi Feature / Bounded Context có Domain Client chuyên trách**:
  - Đặt tên theo nghiệp vụ: `<Feature>Client` hoặc `<Subsystem><Feature>Client` khi cần phân biệt rõ hệ thống/cổng, ví dụ `CitizenReflectionClient`, `InternalReflectionClient`, `CitizenAuthClient`.
  - Một client có thể chứa nhiều thao tác/API thuộc cùng một feature và cùng một ranh giới dịch vụ; không tạo một client riêng cho từng endpoint nhỏ.
  - **CẤM God Client**: Không gom API của nhiều feature hoặc nhiều phân hệ độc lập vào một client chỉ vì chúng cùng thuộc một sản phẩm. Khi client bắt đầu gánh các trách nhiệm nghiệp vụ độc lập, tách theo feature/bounded context.
  - Client chỉ điều phối request/response của API domain qua `ApiClient`; không đặt assertion, kịch bản test hoặc logic của Test Suite vào client.
- **Model thuộc về ranh giới API sở hữu contract**:
  - Request/response DTO của phân hệ phải nằm trong model package do phân hệ đó quản lý. **CẤM** phân hệ này import DTO từ phân hệ độc lập khác (ví dụ `cbkc_internal` import `ExternalReflectionDetail` từ `cbkc_external`).
  - Nếu nhiều phân hệ thực sự dùng chung một contract ổn định, đặt DTO dùng chung trong package/module trung lập như `shared.model`; không để một phân hệ làm chủ DTO của phân hệ còn lại. Chỉ chia sẻ sau khi xác nhận schema và ngữ nghĩa giống nhau.
  - Khi tách client, chuyển các DTO liên quan, response envelope và kiểu lồng nhau cần thiết cùng ranh giới; không để lại import chéo gián tiếp.
- **Quan hệ giữa Client và Test Suite**:
  - Test Suite của feature tập trung toàn bộ smoke, data-driven và CRUD/lifecycle scenario của feature theo quy tắc Mục 1; dùng Domain Client tương ứng thay vì tự dựng request lặp lại.
  - Test Suite E2E/workflow có thể phối hợp nhiều Domain Client để đi qua nhiều phân hệ; không nhân bản endpoint logic sang một God Client hoặc tạo suite trùng feature chỉ để phân loại loại test.
- **Cấu trúc package theo quy mô**:
  - Dự án nhỏ có thể giữ `client/`, `model/`, `tests/` ở cấp package gốc.
  - Dự án có nhiều phân hệ/cổng độc lập phải nhóm theo `modules/<subsystem>/{client,model,tests}/`; đặt model dùng chung thật sự trong `shared/model/`.

---

## 2. ASSERT THEO CHUẨN BUSINESS & BÁO CÁO THẬT (KHÔNG NẮN THEO LOGIC SAI CỦA BACKEND)
- **Kiểm thử là tấm gương phản chiếu chất lượng thật**:
  - Assertion phải dựa hoàn toàn vào **Tài liệu đặc tả (BRD/PRD), Business Logic chuẩn, và Chuẩn HTTP RESTful**.
  - **TUYỆT ĐỐI KHÔNG "uốn nắn" Assertion để làm bài test xanh ảo**:
    - ❌ **CẤM**: Thấy Backend validate sai vẫn trả `200 OK` thì sửa assert thành `statusCode == 200` để cho test pass.
    - ❌ **CẤM**: Bỏ qua assert các trường dữ liệu bị thiếu hoặc sai lệch kiểu dữ liệu để "lách" qua kiểm thử.
    - ❌ **CẤM**: Viết logic workaround trong test code để che đậy lỗi của hệ thống.
  - ✅ **BẮT BUỘC**:
    - Assert nghiêm ngặt theo đúng Business & Status Code quy chuẩn (ví dụ: tạo mới thành công `201`/`200`, lỗi validate phải `400 Bad Request`, không có quyền `401`/`403`, không tìm thấy `404`).
    - Khi Backend xử lý sai, bài test **PHẢI BỊ FAIL**. Giữ nguyên failure và báo cáo BUG thực tế cho đội ngũ phát triển sửa lỗi.

### 2.1 Kiểm tra response chặt chẽ
- Với endpoint được kỳ vọng trả JSON, body rỗng, JSON lỗi, thiếu field bắt buộc hoặc sai kiểu dữ liệu phải làm test fail; không đặt assertion trong `if (!body.isBlank())` để bỏ qua trường hợp không có body.
- Không coi field `data` bị thiếu hoặc không phải array là kết quả rỗng. Xác nhận response envelope, `data` và kiểu dữ liệu trước khi kiểm tra số lượng phần tử.
- Với tìm kiếm, kiểm tra cả tổng số lượng và nội dung item khớp điều kiện theo contract. Với điều kiện không có kết quả, xác nhận response hợp lệ và count bằng 0.
- Không viết nhiều expected value thay thế nhau (ví dụ business code `0` hoặc `200`) nếu contract không quy định cả hai.

### 2.2 CẤM FALLBACK CHE LỖI HOẶC LÀM TEST PASS GIẢ
- Test phải phản ánh đúng kết quả mà người dùng thực tế nhận được. Nếu API trả dữ liệu null/thiếu field, sai trạng thái, lỗi HTTP, không tìm thấy bản ghi hoặc không đồng bộ đúng, test phải fail tại assertion tương ứng và báo rõ lỗi.
- **CẤM** dùng nhánh dự phòng để đổi sang endpoint, actor, bản ghi, dữ liệu, trạng thái hoặc expected value khác sau khi luồng chính thất bại, nhằm giữ test xanh. Ví dụ: API tra cứu theo mã không có dữ liệu thì chuyển sang endpoint khác rồi coi như đạt; không thấy bản ghi cần cập nhật thì âm thầm lấy bản ghi bất kỳ; nhận `null` thì bỏ qua bước kiểm tra.
- **CẤM** bỏ qua assertion bằng `if (status == 200)`, `if (value != null)`, `ifPresent(...)`, `hasNonNull(...)` hoặc `catch` rồi tiếp tục khi điều kiện đó là điều test đang cần xác minh. Hãy assert status/field bắt buộc trước, sau đó mới kiểm tra giá trị. Chỉ dùng điều kiện để rẽ nhánh khi chính điều kiện đó là một phần của đặc tả test và mọi nhánh đều có assertion rõ ràng.
- **CẤM** nới lỏng kết quả bằng nhiều giá trị thay thế (`DONE || PROCESSED`), default khi đọc response (`asText(defaultValue)`), hoặc assertion tổng quát chỉ xác nhận response không null, nếu contract không cho phép các kết quả đó.
- Khi precondition không tồn tại hoặc fixture không hợp lệ, test phải fail rõ ràng. Chỉ được tạo fixture thay thế nếu đó là bước chuẩn bị được khai báo trước trong chính kịch bản; fixture phải được xác minh đúng trạng thái ban đầu, và không được thay đổi âm thầm mục tiêu hoặc ý nghĩa nghiệp vụ của test.
- Polling/eventually và retry có giới hạn được phép để chờ cùng một điều kiện nghiệp vụ cuối cùng (ví dụ đồng bộ bất đồng bộ). Hết timeout hoặc hết retry mà điều kiện chưa đạt thì test phải fail; không đổi điều kiện mong đợi hay chuyển sang kết quả dễ đạt hơn. Retry ở tầng HTTP phải tuân thủ policy an toàn của framework, không biến response lỗi thành pass.
- **Phạm vi UI automation:** fallback giữa các selector/locator để tìm cùng một control UI có thể dùng khi selector chính không ổn định, miễn là locator thay thế cùng đại diện cho control và hành động nghiệp vụ dự kiến. Đây là fallback định vị phần tử; không được dùng để thay đổi kết quả kỳ vọng hoặc che lỗi nghiệp vụ/API phía sau thao tác UI.
- Ví dụ đúng: `response.requireStatus(200); Assert.assertNotNull(response.value()); Assert.assertEquals(response.value().status(), expectedStatus);` Nếu endpoint hoặc dữ liệu bắt buộc không đáp ứng, để test fail và ghi nhận lỗi thay vì chuyển qua nhánh thành công giả.

---

## 3. LUÔN KIỂM TRA TRẠNG THÁI TRƯỚC VÀ SAU ACTION (BEFORE - ACTION - AFTER VERIFICATION)
- **Nguyên tắc "3 Bước Xác Thực Trạng Thái" cho mọi thao tác biến đổi dữ liệu (C-U-D)**:
  - Tuyệt đối không chỉ tin tưởng vào mã HTTP response (`200 OK`) từ API. Bắt buộc đối chiếu trạng thái thực tế tại Database / Hệ thống trước và sau khi hành động xảy ra.
- **Quy trình chuẩn**:
  1. **BEFORE (Trước khi tác động)**:
     - Kiểm tra trạng thái xuất phát điểm:
       - *Create*: Xác nhận bản ghi chưa tồn tại (count = 0 hoặc query không ra).
       - *Update/Patch*: Xác nhận bản ghi đang mang giá trị cũ $A$.
       - *Delete*: Xác nhận bản ghi đang tồn tại và hợp lệ trong Database trước khi gọi xóa.
  2. **ACTION (Thực thi hành động)**:
     - Gọi API endpoint (POST, PUT, PATCH, DELETE) và kiểm tra HTTP status code + response format.
  3. **AFTER (Sau khi tác động)**:
     - Kiểm chứng trực tiếp tại Database (hoặc API query độc lập):
       - *Create*: Bản ghi đã được ghi vào Database với đúng schema, đúng giá trị các trường.
       - *Update/Patch*: Chỉ các trường được chỉ định bị thay đổi sang giá trị mới $B$, các trường khác giữ nguyên.
       - *Delete*: Kiểm tra bản ghi đã bị xóa sạch (Hard Delete: `count == 0` trong DB) hoặc chuyển cờ `is_deleted = true` (nếu nghiệp vụ quy định Soft Delete).
- **Mục tiêu**: Phát hiện triệt để các lỗi "ngầm" (API trả thành công nhưng Database không lưu, lưu thiếu trường, hoặc không xóa thật sự).

### 3.1 Cleanup phải đáng tin cậy và không xóa nhầm
- Đăng ký cleanup ngay sau khi lấy được ID resource. Chỉ cleanup ID do chính test run tạo và track; không quét/xóa theo prefix chung trên môi trường dùng chung.
- Không xóa ID khỏi danh sách theo dõi trong `finally`. Chỉ bỏ ID sau khi API xác nhận xóa và trạng thái sau xóa đã được kiểm chứng.
- Không nuốt exception, không chỉ ghi log rồi pass, và không xóa danh sách ID còn lỗi. Cleanup thất bại phải làm test/class fail; class-level cleanup có thể retry các ID còn track.
- Tránh gọi DELETE lần hai cho resource đã được xóa và xác minh thành công; cleanup hook cần kiểm tra trạng thái tracking trước khi gọi.
- Nếu assertion hỏng trước khi lấy được ID sau thao tác tạo, thiết kế test nên lấy ID từ create response hoặc có cơ chế cleanup có giới hạn theo run ID riêng, không dùng prefix dùng chung.

---

## 4. TÁCH BIỆT TUYỆT ĐỐI CODE LOGIC VÀ TEST DATA (KHÔNG HARDCODE TRONG CODE)
- **Code kiểm thử chỉ chứa Logic Điều khiển & Khẳng định (Assertions)**:
  - Thân hàm `@Test` và Client code đóng vai trò bộ khung thực thi (Executor).
  - **Tuyệt đối không nhúng Test Data vào trong Code Logic**:
    - ❌ **CẤM**: Hardcode chuỗi JSON payload, giá trị magic string, email, phone, tên test cụ thể trong thân hàm Java.
    - ❌ **CẤM**: Viết câu lệnh `if-else` cứng để kiểm tra riêng cho từng giá trị dữ liệu cụ thể trong code.
  - ✅ **BẮT BUỘC**:
    - Dữ liệu kiểm thử, kịch bản, case biên, boundary values, expected status phải tách riêng vào file:
      - `src/test/resources/testdata/<feature>.csv` cho Data-Driven Testing qua `CsvDataProvider`.
      - `src/test/resources/payloads/` hoặc JSON template cho các payload phức tạp.
    - Câu truy vấn database nằm trong:
      - `src/test/resources/nosql/<query>.json` cho NoSQL/MongoDB.
      - `src/test/resources/sql/<query>.sql` cho SQL/Relational DB.
    - Sử dụng placeholder `${param}` hoặc `{{param}}` để nạp tham số động vào dữ liệu/truy vấn.
- Không dùng fallback chứa dữ liệu kịch bản trong Java (`asText("...")`, magic number, default tên/value, fallback URL/domain). Field bắt buộc thiếu trong resource/config phải báo lỗi rõ ràng qua `require()` để fixture lỗi không biến thành một kịch bản khác.
- Tuyệt đối không hardcode `wardCode`, mã tỉnh (`provinceCode`), tên tỉnh hay toạ độ địa bàn trong Java code. Toàn bộ thông tin địa bàn hành chính phải được đưa ra `src/test/resources/testdata/<feature>.csv` hoặc quản lý qua `Config.TestLocation` nạp từ `test.location.*` trong `config-local.properties`.
- Chỉ giữ trong Java các hằng số thuộc contract/schema (HTTP path, tên field, enum protocol); URL, portal/tenant/environment ID, credential và mọi expected/test value phải đến từ config hoặc fixture phù hợp.
- Tách dữ liệu test khỏi config: expected status, keyword, payload value và boundary case nằm trong fixture; endpoint, tenant code, timeout môi trường và credential nằm trong config.

---

## 5. QUY CHUẨN NGÔN NGỮ: CODE TIẾNG ANH TOÀN BỘ — COMMENT TIẾNG VIỆT
- **Toàn bộ Code thực thi viết bằng Tiếng Anh (100% English Code)**:
  - Tên class, interface, method, biến, hằng số, enum, model, builder, payload key.
  - Tên test method (theo chuẩn `createsCredentialSuccessfully`, `verifiesHardDeleteInMongoDb`, v.v.).
  - Các thông điệp Assertion và log runtime (`Assert.assertEquals(actual, expected, "Credential document must exist in MongoDB")`).
  - Tên file tài nguyên (`.csv`, `.json`, `.sql`), tên branch, git commit messages.
  - Mục đích: Đảm bảo tính chuẩn hóa quốc tế, chuyên nghiệp và tương thích hoàn toàn với các công cụ CI/CD, linter, report parser.
- **Toàn bộ Comment & Chú thích viết bằng Tiếng Việt (Vietnamese Comments)**:
  - JavaDoc mô tả mục đích của Class, Method, Test Suite.
  - Comment từng bước thực hiện (Step 1: Tạo credential qua API; Step 2: Xác thực trạng thái ban đầu tại MongoDB...).
  - Giải thích lý do nghiệp vụ, logic phức tạp, cảnh báo kỹ thuật hoặc edge case.
  - Mục đích: Giúp đội ngũ QC/QA và Developers người Việt nắm bắt luồng kiểm thử ngay lập tức mà không gặp rào cản ngôn ngữ.

---

## 6. TRUY VẤN DATABASE: SỬ DỤNG MODULE CHUẨN TỪ FRAMEWORK
- **Sử dụng module chuẩn của Framework**:
  - Dùng `vn.ots.qc:framework-nosql` (`NoSqlClient`, `NoSqlQueryLoader`, `NoSqlConfig`); không khai báo MongoDB driver trực tiếp trong consumer nếu đã được kéo bắc cầu từ module này.
  - Dùng `framework-sql` cho JDBC API. Vì module trung lập vendor không đóng gói JDBC driver, consumer khai báo driver đúng loại database theo version được tổ chức phê duyệt và quản lý version tập trung.
- **Database chỉ dùng để READ/VERIFY trong kiểm thử**:
  - Thao tác xóa hoặc dọn dẹp dữ liệu phải đi qua API của ứng dụng để mô phỏng đúng luồng người dùng thực tế. Không xóa tắt trực tiếp bằng lệnh DB DELETE.

---

## 7. CẤU TRÚC PACKAGE CONSUMER CHUẨN
```
src/test/java/<package_root>/
├── auth/                 # AuthProvider, token/session và đăng nhập
├── config/               # Config / ConsumerConfig
├── data/                 # DataProvider và fixture loader
├── runtime/              # Runtime factories
├── shared/
│   └── model/            # DTO chỉ dùng chung khi contract thực sự giống nhau
└── modules/              # Dùng khi dự án có nhiều phân hệ/cổng
    └── <subsystem>/
        ├── client/       # Domain API Client theo feature/bounded context
        ├── model/        # DTO thuộc contract của phân hệ
        └── tests/        # Một <Feature>ApiTest cho mỗi feature/domain
src/test/resources/
├── config.properties
├── nosql/                # Query NoSQL (.json)
├── sql/                  # Query SQL (.sql)
└── testdata/             # Test data (.csv / payload fixtures)
```

Dự án nhỏ có thể dùng `client/`, `model/`, `tests/` trực tiếp dưới `<package_root>`. Khi có nhiều phân hệ độc lập, chuyển sang `modules/<subsystem>/...` để giữ rõ ownership; không tách package chỉ theo loại test như `smoke/`, `csv/`, `crud/`.
---

## 8. BẢO MẬT & THỰC THI AN TOÀN
- Không hardcode credentials, secrets, tokens, domain/IP thật trong Git.
- Cấu hình nhạy cảm nằm ở `config-local.properties` (gitignored) hoặc biến môi trường.
- AI không tự ý thực thi các lệnh tấn công mạng/quét trực tiếp ngoài phạm vi cho phép.
