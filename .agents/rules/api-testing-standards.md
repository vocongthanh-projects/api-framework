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

---

## 5. TRUY VẤN DATABASE: SỬ DỤNG MODULE CHUẨN TỪ FRAMEWORK
- **Sử dụng module chuẩn của Framework**:
  - Không tự ý kéo DB driver ngoài (MongoDB driver, JDBC vendor driver) vào consumer để tránh lệch phiên bản.
  - Dùng `vn.ots.qc:framework-nosql` (`NoSqlClient`, `NoSqlQueryLoader`, `NoSqlConfig`).
  - Dùng module JDBC chuẩn từ `api-framework`.
- **Database chỉ dùng để READ/VERIFY trong kiểm thử**:
  - Thao tác xóa hoặc dọn dẹp dữ liệu phải đi qua API của ứng dụng để mô phỏng đúng luồng người dùng thực tế. Không xóa tắt trực tiếp bằng lệnh DB DELETE.

---

## 6. CẤU TRÚC PACKAGE CONSUMER CHUẨN
```
src/test/java/<package_root>/
├── auth/           # OAuth, AuthProvider, Token/Key manager, Runtime factory
├── client/         # Domain API Clients (<Feature>Client) gọi API qua ApiClient
├── config/         # Cấu hình chuẩn (Config / ConsumerConfig)
├── data/           # CsvDataProvider nạp dữ liệu từ CSV
├── database/       # Adapter truy vấn bổ sung (nếu có)
└── tests/          # Các Test Suite (<Feature>ApiTest) - KHÔNG để file nháp/rác
src/test/resources/
├── config.properties
├── nosql/          # File truy vấn NoSQL (.json)
├── sql/            # File truy vấn SQL (.sql)
└── testdata/       # File dữ liệu kiểm thử (.csv)
```

---

## 7. BẢO MẬT & THỰC THI AN TOÀN
- Không hardcode credentials, secrets, tokens, domain/IP thật trong Git.
- Cấu hình nhạy cảm nằm ở `config-local.properties` (gitignored) hoặc biến môi trường.
- AI không tự ý thực thi các lệnh tấn công mạng/quét trực tiếp ngoài phạm vi cho phép.
