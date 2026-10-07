# Project Agent Rules & Automation Standards

## Core Rules

1. **Tuân thủ Chuẩn Kiểm thử API & Cấu trúc Dự án (API Testing Standards)**:
   - Đọc và thực thi nghiêm ngặt: [.agents/rules/api-testing-standards.md](rules/api-testing-standards.md)
   - **Chống lộn xộn (No Test Class Sprawl)**: Mỗi Feature/Domain chỉ có duy nhất 1 Test Suite `<Feature>ApiTest`. Không tạo nhiều class test nhỏ lẻ.
   - **Assert theo chuẩn Business & Báo cáo thật (No Adapting to BE Flaws)**: Luôn assert theo Business Logic chuẩn và đặc tả HTTP RESTful. Tuyệt đối không "uốn nắn" assert theo logic sai của Backend chỉ để test xanh ảo. Khi BE sai, bài test PHẢI fail và báo cáo BUG thật.
   - **Luôn kiểm tra State Trước và Sau Action (Before - Action - After)**: Mọi thao tác làm biến đổi dữ liệu (Create, Update/Patch, Delete) phải kiểm tra trạng thái trước khi tác động, thực thi action, và đối chiếu lại dữ liệu thực tế tại Database/API sau tác động.
   - **Tách biệt tuyệt đối Code Logic và Test Data (No Hardcoding Test Data)**: Code kiểm thử chỉ chứa logic điều khiển luồng và assertion. Toàn bộ dữ liệu kiểm thử, kịch bản, tham số phải nằm trong `src/test/resources/testdata/<feature>.csv` hoặc file tài nguyên.
   - **Quy chuẩn Ngôn ngữ (Language Convention)**: Toàn bộ Code thực thi (class, method, variable, assertion, log) viết bằng **Tiếng Anh toàn bộ**. Toàn bộ Comment & JavaDoc giải thích chức năng, nghiệp vụ viết bằng **Tiếng Việt**.
   - **Database Queries (SQL & NoSQL)**: Câu truy vấn phải nằm trong file tài nguyên riêng biệt (`.sql` cho SQL, `.json` cho NoSQL). Không hardcode chuỗi truy vấn trong Java code. Dùng `framework-nosql` cho MongoDB và `framework-sql` cho JDBC; consumer khai báo JDBC driver vendor theo phiên bản được tổ chức phê duyệt. Thao tác dọn dẹp dữ liệu phải đi qua API, không xóa trực tiếp từ DB.

2. **Tuyệt đối không hardcode cấu hình (No Hardcoding Config)**:
   - Đọc và thực thi: [.agents/rules/no-hardcode-config.md](rules/no-hardcode-config.md)
   - Toàn bộ URL môi trường, IP, username, password, token phải được lấy qua System Properties, Environment Variables, hoặc `config-local.properties`.

3. **An toàn & Môi trường**:
   - Không tự ý chạy các lệnh tấn công mạng/quét trực tiếp vào IP mục tiêu từ AI runner.
