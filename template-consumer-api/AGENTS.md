# Hướng dẫn trợ lý AI

Đọc README và tuân thủ bộ quy chuẩn tại [`.agents/AGENTS.md`](.agents/AGENTS.md), bao gồm các rule phù hợp trong `.agents/rules/`. Đây là nguồn chuẩn duy nhất của quy tắc kiểm thử API trong repository; không tạo bản sao theo từng AI.

## Quy tắc CI OTS/FLI

- Pipeline khi push source lên GitLab 10 chỉ kiểm tra cấu hình/dependency và compile/package nhẹ; không chạy API test suite trên runner source.
- API suite chạy qua hạ tầng test riêng bằng trigger manual hoặc schedule đã cấu hình. Không giả định runner source có network, service, dữ liệu hoặc runtime cần thiết.
- Không publish, deploy, push hoặc trigger pipeline từ xa nếu người dùng chưa yêu cầu rõ.
