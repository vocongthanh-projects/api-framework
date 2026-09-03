# Project Agent Rules

## Core Rules

1. **Tuyệt đối không hardcode (Strictly No Hardcoding)**:
   - Không hardcode URL môi trường, IP, domain, username, password, token, OTP trong source code hoặc file properties.
   - Toàn bộ thông số môi trường phải được lấy qua System Properties hoặc Environment Variables hoặc file `config.properties`/`config-local.properties`.
   - Nếu thiếu cấu hình bắt buộc, ném lỗi `IllegalStateException`, không được fallback về URL/tài khoản cố định.

2. **Quy tắc đặt tên Cấu hình (Generic Config Naming)**:
   - Không đặt tên class cấu hình theo tên dự án (VD: KHÔNG dùng `MapConfig`, `HrmConfig`).
   - Tên class cấu hình chuẩn là `Config` (hoặc `ConfigManager`).

3. **An toàn & Môi trường**:
   - Không tự ý chạy các lệnh tấn công mạng/quét trực tiếp vào IP mục tiêu từ AI runner.
   - Không ghi đè hoặc làm thay đổi project gốc `api-map`.
