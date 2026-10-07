# Coding Rule: No Hardcoding & Generic Config Naming

## 1. Tuyệt đối không Hardcode (No Hardcoding)
- **Nghiêm cấm hardcode**:
  - Không hardcode URL môi trường (VD: `http://qc-...`, domain, host, IP).
  - Không hardcode credentials (username, password, access token, secret, OTP).
  - Không hardcode giá trị fallback là các domain / URL / tài khoản cụ thể trong source code Java hoặc file properties mẫu.
  - Không hardcode portal/tenant/client code, account alias được dùng trực tiếp làm username, hay credential vào domain client/test. Truyền cấu hình qua config object thay vì constant rải rác.
  - Không commit endpoint nội bộ, IP/hostname thật, username tài khoản, resource ID có thể truy cập môi trường, log hoặc report chứa dữ liệu nhạy cảm trong README/docs/evidence. Dùng placeholder và hướng dẫn lấy giá trị từ config được cấp quyền.
- **Cơ chế nạp cấu hình**:
  - Mọi URL, credential và tham số môi trường phải được lấy theo thứ tự ưu tiên:
    1. System Properties (`-Dkey=value`)
    2. Biến môi trường / Environment Variables (`KEY_NAME`)
    3. File cấu hình cục bộ / local properties (`config-local.properties`, `config.properties`)
- Nếu thiếu tham số bắt buộc (như `base.url`), phải ném ngoại lệ rõ ràng (`IllegalStateException("Missing required configuration: base.url")`), tuyệt đối không fallback về một server cụ thể.
- Fixture bắt buộc phải được kiểm tra schema/field trước khi dùng; không đặt giá trị mặc định cho test data bị thiếu vì sẽ che lỗi fixture.
- Không đưa secret giả có hình thức như token/password thật vào test fixture. Dùng dữ liệu giả rõ ràng, không có quyền truy cập và chỉ phục vụ unit test.
- `Actor.alias` là định danh tra cứu trong test/framework, không mặc định là username đăng nhập. Auth provider chỉ được dùng alias để tra mapping credential rõ ràng; username/password vẫn phải lấy từ cấu hình/secret store tương ứng.

## 2. Quy ước đặt tên Cấu hình (Generic Config Naming)
- **Không đặt tên theo dự án**:
  - Tuyệt đối không đặt tên class cấu hình theo tên dự án (VD: KHÔNG dùng `MapConfig`, `HrmConfig`, `GtelConfig`...).
  - Đặt tên thuần túy, chuẩn hóa và tổng quát là `Config` hoặc `ConfigManager`.
- **Package cấu hình**:
  - Đặt trong package `config` (ví dụ `...config.Config`).
