---
trigger: always_on
description: Quy tắc cấu hình an toàn, không hardcode môi trường, secret hoặc dữ liệu nghiệp vụ.
---

# Coding Rule: No Hardcoding & Generic Config Naming

## 1. Tuyệt đối không Hardcode (No Hardcoding)
- **Nghiêm cấm hardcode**:
  - Không hardcode URL môi trường (VD: `http://qc-...`, domain, host, IP).
  - Không hardcode credentials (username, password, access token, secret, OTP).
  - Không hardcode portal/tenant/client code, account alias được dùng trực tiếp làm username, hay credential vào domain client/test. Truyền cấu hình qua config object thay vì constant rải rác.
  - Không hardcode dữ liệu địa bàn hành chính (`wardCode`, `provinceCode`, `provinceName`, địa chỉ, toạ độ GPS) vào Java source code. Tất cả phải được nạp động từ file cấu hình (`test.location.*`) hoặc test data fixture.
  - Không commit endpoint nội bộ, IP/hostname thật, username tài khoản, resource ID có thể truy cập môi trường, log hoặc report chứa dữ liệu nhạy cảm trong README/docs/evidence. Dùng placeholder và hướng dẫn lấy giá trị từ config được cấp quyền.

- **Nghiêm cấm Fallback Domain / URL / Dữ liệu môi trường trong Code Java**:
  - Khi viết getter trong class cấu hình (`Config` / `ConsumerConfig`): Mọi endpoint, domain, base-url, redirect-uri, SSO URL, property/tenant ID, hay tham số địa bàn hành chính (`wardCode`, `provinceCode`) bắt buộc phải dùng `require(key)` hoặc ném ngoại lệ `IllegalStateException("Missing required configuration: " + key)` khi không tìm thấy.
  - **TUYỆT ĐỐI CẤM** dùng `get(key, "https://dev-...")` hoặc gán giá trị mặc định là các domain / URL / mã xã phường cụ thể trong Java code.
  - **Lý do**: Khi cài đặt dự án mới (install template) hoặc chuyển đổi môi trường (QC -> Staging -> UAT -> Prod), giá trị fallback ngầm sẽ khiến bài test âm thầm gửi request sang môi trường dev cũ, gây sai lệch dữ liệu kiểm thử, rò rỉ bảo mật hoặc làm che giấu lỗi cấu hình.
  - File `config.properties` mẫu chỉ chứa template placeholder (`https://replace-me-with-...`). Toàn bộ domain thật, credential thật và địa bàn kiểm thử của môi trường dev chỉ được phép nằm trong `config-local.properties` (file đã được gitignore).

- **Cơ chế nạp cấu hình**:
  - Mọi URL, credential và tham số môi trường phải được lấy theo thứ tự ưu tiên:
    1. System Properties (`-Dkey=value`)
    2. Biến môi trường / Environment Variables (`KEY_NAME`)
    3. File cấu hình cục bộ / local properties (`config-local.properties`, `config.properties`)
  - Khi đọc file `.properties`, bắt buộc sử dụng `InputStreamReader(..., StandardCharsets.UTF_8)` để bảo toàn 100% tiếng Việt có dấu cho tên tỉnh, địa chỉ, đơn vị hành chính.
  - Nếu thiếu tham số bắt buộc (như `base.url`), phải ném ngoại lệ rõ ràng (`IllegalStateException("Missing required configuration: base.url")`), tuyệt đối không fallback về một server cụ thể.
  - Fixture bắt buộc phải được kiểm tra schema/field trước khi dùng; không đặt giá trị mặc định cho test data bị thiếu vì sẽ che lỗi fixture.
  - Không đưa secret giả có hình thức như token/password thật vào test fixture. Dùng dữ liệu giả rõ ràng, không có quyền truy cập và chỉ phục vụ unit test.
  - `Actor.alias` là định danh tra cứu trong test/framework, không mặc định là username đăng nhập. Auth provider chỉ được dùng alias để tra mapping credential rõ ràng; username/password vẫn phải lấy từ cấu hình/secret store tương ứng.

## 2. Quy ước đặt tên Cấu hình (Generic Config Naming)
- **Không đặt tên theo dự án**:
  - Tuyệt đối không đặt tên class cấu hình theo tên dự án (VD: KHÔNG dùng `MapConfig`, `HrmConfig`, `GtelConfig`...).
  - Đặt tên thuần túy, chuẩn hóa và tổng quát là `Config` hoặc `ConfigManager` (hoặc `ConsumerConfig` đối với consumer project).
- **Package cấu hình**:
  - Đặt trong package `config` (ví dụ `...config.Config` hoặc `...config.ConsumerConfig`).
