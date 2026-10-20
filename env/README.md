# Cấu hình môi trường

.env.example: mẫu local. .env.production.example: mẫu Docker/VPS. scripts/setup-local.ps1 tạo .env.local với mật khẩu ngẫu nhiên và ACL riêng.

FE không nhận password database/gateway secret. NodeGateway và BE đọc cấu hình server trong env/. Khởi động lại sau khi chỉnh env.

Không chia sẻ .env.local, .env.production, backup hoặc media trong Git/bản chia sẻ code. Đổi mật khẩu qua giao diện không cập nhật biến bootstrap; biến đó không đặt lại mật khẩu tài khoản đã có.
