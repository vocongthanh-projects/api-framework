# Project Agent Rules & Automation Standards

## Core Rules

1. **Tuân thủ Chuẩn Kiểm thử API & Cấu trúc Dự án (API Testing Standards)**:
   - Đọc và thực thi nghiêm ngặt: [.agents/rules/api-testing-standards.md](rules/api-testing-standards.md)
   - **Tổ chức test theo feature**: Nhóm test theo domain/workflow; cho phép nhiều suite khi chúng có trách nhiệm rõ ràng. Tránh class khổng lồ và class riêng cho từng endpoint/biến thể dữ liệu.
   - **Assert theo chuẩn Business & Báo cáo thật (No Adapting to BE Flaws)**: Luôn assert theo Business Logic chuẩn và đặc tả HTTP RESTful. Tuyệt đối không "uốn nắn" assert theo logic sai của Backend chỉ để test xanh ảo. Khi BE sai, bài test PHẢI fail và báo cáo BUG thật.
   - **Cấm fallback làm test xanh giả**: Không bỏ qua assertion khi status/field bắt buộc sai hoặc thiếu, không đổi endpoint/data/actor/expected value sau khi luồng chính lỗi. Polling/retry chỉ chờ cùng điều kiện và phải fail khi hết giới hạn. Fallback selector để tìm cùng một control trong UI automation được phép; không được dùng để che lỗi nghiệp vụ. Xem Mục 2.2 trong API Testing Standards.
   - **Luôn kiểm tra State Trước và Sau Action (Before - Action - After)**: Mọi thao tác làm biến đổi dữ liệu (Create, Update/Patch, Delete) phải kiểm tra trạng thái trước khi tác động, thực thi action, và đối chiếu lại dữ liệu thực tế tại Database/API sau tác động.
   - **Tách biệt tuyệt đối Code Logic và Test Data (No Hardcoding Test Data)**: Code kiểm thử chỉ chứa logic điều khiển luồng và assertion. Toàn bộ dữ liệu kiểm thử, kịch bản, tham số phải nằm trong `src/test/resources/testdata/<feature>.csv` hoặc file tài nguyên.
   - **Quy chuẩn Ngôn ngữ (Language Convention)**: Toàn bộ Code thực thi (class, method, variable, assertion, log) viết bằng **Tiếng Anh toàn bộ**. Toàn bộ Comment & JavaDoc giải thích chức năng, nghiệp vụ viết bằng **Tiếng Việt**.
   - **Database Queries (SQL & NoSQL)**: Câu truy vấn phải nằm trong file tài nguyên riêng biệt (`.sql` cho SQL, `.json` cho NoSQL). Không hardcode chuỗi truy vấn trong Java code. Dùng `framework-nosql` cho MongoDB và `framework-sql` cho JDBC; consumer vẫn cần khai báo JDBC driver vendor tương ứng theo phiên bản được tổ chức phê duyệt. Thao tác dọn dẹp dữ liệu phải đi qua API, không xóa trực tiếp từ DB.

   - **Domain Client & Module Boundary**: Mỗi feature/bounded context có Domain Client chuyên trách; không tạo God Client gom nhiều feature và không tạo client riêng cho từng endpoint. DTO thuộc resource sở hữu API; cấm import chéo model giữa các phân hệ độc lập. Chỉ đặt DTO vào package `shared.model` khi contract thực sự giống nhau. Suite E2E được phối hợp nhiều client; suite theo feature có thể tách khi trách nhiệm rõ ràng.
   - **Package theo feature/resource**: Dự án nhiều phân hệ dùng `modules/<subsystem>/<feature-or-resource>/{client,model,tests}/`; DTO theo ownership của resource, không gom model toàn hệ thống hoặc tách chỉ theo HTTP verb. Dự án nhỏ có thể giữ package gốc. Đọc API Testing Standards Mục 1.1 và 7.

2. **Tuyệt đối không hardcode cấu hình & Cấm Fallback Domain/Địa bàn (No Hardcoding Config & No Fallback)**:
   - Đọc và thực thi nghiêm ngặt: [.agents/rules/no-hardcode-config.md](rules/no-hardcode-config.md)
   - Toàn bộ URL môi trường, IP, username, password, token, mã địa bàn (`wardCode`, `provinceCode`) phải được lấy qua System Properties, Environment Variables, hoặc `config-local.properties`.
   - Tuyệt đối CẤM hardcode fallback domain/URL (ví dụ `get(key, "https://dev-...")`) hoặc mã xã/phường/tỉnh ngầm trong code Java. Thiếu cấu hình bắt buộc phải dùng `require(key)` để báo lỗi rõ ràng.

3. **An toàn & Môi trường**:
   - Không tự ý chạy các lệnh tấn công mạng/quét trực tiếp vào IP mục tiêu từ AI runner.
