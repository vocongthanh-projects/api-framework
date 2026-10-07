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
- Không dùng fallback chứa dữ liệu kịch bản trong Java (`asText("...")`, magic number, default tên/value). Field bắt buộc thiếu trong resource phải báo lỗi rõ ràng để fixture lỗi không biến thành một kịch bản khác.
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
├── auth/           # OAuth, AuthProvider, Token/Key manager, Encryption utils
├── client/         # Domain API Clients (<Feature>Client) gọi API qua ApiClient
├── config/         # Cấu hình chuẩn (Config / ConsumerConfig)
├── data/           # CsvDataProvider nạp dữ liệu từ CSV
├── runtime/        # Runtime Factories (ConsumerRuntimeFactory: API & NoSQL)
└── tests/          # Các Test Suite (<Feature>ApiTest) - KHÔNG để file nháp/rác
src/test/resources/
├── config.properties
├── nosql/          # File truy vấn NoSQL (.json)
├── sql/            # File truy vấn SQL (.sql)
└── testdata/       # File dữ liệu kiểm thử (.csv)
```

---

## 8. BẢO MẬT & THỰC THI AN TOÀN
- Không hardcode credentials, secrets, tokens, domain/IP thật trong Git.
- Cấu hình nhạy cảm nằm ở `config-local.properties` (gitignored) hoặc biến môi trường.
- AI không tự ý thực thi các lệnh tấn công mạng/quét trực tiếp ngoài phạm vi cho phép.
