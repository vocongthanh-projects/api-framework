# Project Agent Rules & Automation Standards

## Core Rules

1. **Tuân thủ Chuẩn Kiểm thử API & Cấu trúc Dự án (API Testing Standards)**:
   - Đọc và thực thi nghiêm ngặt: [.agents/rules/api-testing-standards.md](rules/api-testing-standards.md)
   - **Chống lộn xộn (No Test Class Sprawl)**: Mỗi Feature/Domain chỉ có duy nhất 1 Test Suite `<Feature>ApiTest`. Không tạo nhiều class test nhỏ lẻ.
   - **Data-Driven Testing qua CSV**: Dữ liệu kiểm thử phải nằm trong `src/test/resources/testdata/<feature>.csv`.
   - **Database Queries (SQL & NoSQL)**: Câu truy vấn phải nằm trong file tài nguyên riêng biệt (`.sql` cho SQL, `.json` cho NoSQL). Không hardcode chuỗi truy vấn trong Java code. Phải dùng module chuẩn từ `api-framework` (`framework-nosql`), không tự ý kéo DB driver ngoài vào consumer.

2. **Tuyệt đối không hardcode cấu hình (No Hardcoding Config)**:
   - Đọc và thực thi: [.agents/rules/no-hardcode-config.md](rules/no-hardcode-config.md)
   - Toàn bộ URL môi trường, IP, username, password, token phải được lấy qua System Properties, Environment Variables, hoặc `config-local.properties`.

3. **An toàn & Môi trường**:
   - Không tự ý chạy các lệnh tấn công mạng/quét trực tiếp vào IP mục tiêu từ AI runner.
