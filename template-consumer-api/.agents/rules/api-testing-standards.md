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
  - `database`: Kiểm tra dữ liệu trực tiếp trong DB (chỉ chạy với profile `-Pdatabase-tests`).

---

## 2. TEST DATA-DRIVEN: BẮT BUỘC DÙNG CSV (KHÔNG HARDCODE DATA)
- **Không hardcode tham số kiểm thử trong thân hàm `@Test`**:
  - Các bộ giá trị kiểm thử, case biên, keyword tìm kiếm, expected status phải được lưu trong file CSV.
  - Vị trí: `src/test/resources/testdata/<feature>.csv`
  - Đọc dữ liệu qua `CsvDataProvider` với annotation `@DataProvider(name = "...")`.
  - Sử dụng `@Test(dataProvider = "...", dataProviderClass = CsvDataProvider.class)`.

---

## 3. TRUY VẤN DATABASE: TÁCH RIÊNG SQL VÀ NOSQL VÀO FILE TÀI NGUYÊN
- **Không hardcode câu lệnh truy vấn trong code Java**:
  - **SQL** (Relational DB): Lưu tại `src/test/resources/sql/<query-name>.sql`.
  - **NOSQL** (MongoDB / Document DB): Lưu tại `src/test/resources/nosql/<command-name>.json`.
- **Hỗ trợ tham số động trong file truy vấn**:
  - Dùng placeholder `${paramName}` hoặc `{{paramName}}` trong file `.json` / `.sql`.
  - Sử dụng `NoSqlQueryLoader.loadDocument(...)` hoặc loader tương ứng để truyền biến động vào query.
- **Sử dụng module chuẩn của Framework**:
  - **Tuyệt đối không** tự ý thêm các driver DB vendor trực tiếp vào `pom.xml` của consumer (tránh chênh lệch version).
  - Sử dụng các module chuẩn từ `api-framework`:
    - NoSQL: `vn.ots.qc:framework-nosql` (sử dụng `NoSqlClient`, `NoSqlQueryLoader`, `NoSqlConfig`).
    - SQL: Sử dụng JDBC module/client chuẩn do framework quản lý.

---

## 4. CẤU TRÚC PACKAGE CONSUMER CHUẨN
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

## 5. BẢO MẬT & THỰC THI AN TOÀN
- Không hardcode credentials, secrets, tokens, domain/IP thật trong Git.
- Cấu hình nhạy cảm nằm ở `config-local.properties` (gitignored) hoặc biến môi trường.
- AI không tự ý thực thi các lệnh tấn công mạng/quét trực tiếp ngoài phạm vi cho phép.
