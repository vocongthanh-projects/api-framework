# Coding Rule: No Hardcoding & Generic Config Naming

## 1. Tuyệt đối không Hardcode (No Hardcoding)
- **Nghiêm cấm hardcode**:
  - Không hardcode URL môi trường (VD: `http://qc-...`, domain, host, IP).
  - Không hardcode credentials (username, password, access token, secret, OTP).
  - Không hardcode giá trị fallback là các domain / URL / tài khoản cụ thể trong source code Java hoặc file properties mẫu.
- **Cơ chế nạp cấu hình**:
  - Mọi URL, credential và tham số môi trường phải được lấy theo thứ tự ưu tiên:
    1. System Properties (`-Dkey=value`)
    2. Biến môi trường / Environment Variables (`KEY_NAME`)
    3. File cấu hình cục bộ / local properties (`config-local.properties`, `config.properties`)
  - Nếu thiếu tham số bắt buộc (như `base.url`), phải ném ngoại lệ rõ ràng (`IllegalStateException("Missing required configuration: base.url")`), tuyệt đối không fallback về một server cụ thể.

## 2. Quy ước đặt tên Cấu hình (Generic Config Naming)
- **Không đặt tên theo dự án**:
  - Tuyệt đối không đặt tên class cấu hình theo tên dự án (VD: KHÔNG dùng `MapConfig`, `HrmConfig`, `GtelConfig`...).
  - Đặt tên thuần túy, chuẩn hóa và tổng quát là `Config` hoặc `ConfigManager`.
- **Package cấu hình**:
  - Đặt trong package `config` (ví dụ `...config.Config`).
