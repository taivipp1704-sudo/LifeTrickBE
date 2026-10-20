# Chuẩn bị backend trên Render

Backend vẫn dùng SQL Server. Chưa tạo dịch vụ Render vì chưa có SQL Server trên cloud; bản Vercel dùng chế độ xem thử giao diện.

Khi đã có SQL Server cloud, tạo Blueprint từ `render.yaml` hoặc Web Service từ repository LifeTrickBE, branch main, Dockerfile `deployment/Dockerfile.render`, build context `.`. Blueprint cố định gói Free, health check và các biến công khai; các biến bí mật vẫn phải nhập trong Render.

Khai báo các biến server trong Render, không commit giá trị thật:

| Biến | Giá trị cần nhập |
|---|---|
| DB_URL | JDBC SQL Server cloud, TLS và chứng chỉ hợp lệ |
| DB_USER / DB_PASSWORD | Login runtime riêng |
| DB_MIGRATION_USER / DB_MIGRATION_PASSWORD | Login migration riêng |
| GATEWAY_SECRET | Chuỗi ngẫu nhiên ít nhất 32 ký tự |
| APP_ORIGINS | Origin Vercel chính xác, không dùng wildcard |
| BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD | Admin ban đầu; mật khẩu ngẫu nhiên ít nhất 16 ký tự |
| COOKIE_SECURE | true |
| NODE_ENV | production |

Chạy `powershell -ExecutionPolicy Bypass -File scripts/new-cloud-secrets.ps1` một lần để tạo `env/.env.cloud.local` đã được Git bỏ qua. File này giữ credential cloud trên máy; không đưa nội dung vào commit, issue hoặc chat.

Chọn healthcheck `/healthz`. Launcher chạy Java ở loopback 8088 và Node ở PORT do Render cấp, có giới hạn heap để thử trên gói nhỏ. Chưa kiểm chứng mức dùng RAM dưới tải Render.

Với Azure SQL Free, chọn hành vi **Auto-pause the database until next month** khi hết hạn mức. Tạo hai contained user bằng `database/scripts/azure-create-users.sql`: `tk_migrator_v2` có `db_owner` để Flyway chạy migration; `tk_runtime_v2` chỉ có quyền đọc/ghi và bị chặn DDL. JDBC URL phải có `databaseName`, `encrypt=true`, `trustServerCertificate=false` và `hostNameInCertificate=*.database.windows.net`.

Kết nối database cloud được giới hạn còn tối đa 3 connection và 0 connection nhàn rỗi trên Render để Azure SQL serverless có thể tự tạm dừng khi không dùng.

Filesystem của Render Free là tạm: ảnh/video sẽ mất sau restart/redeploy. Không dùng bản này để giữ dữ liệu quan trọng, chưa bật lưu video bền vững trên cloud. Tài khoản/bài/sổ tay chỉ bền vững nếu SQL Server ngoài Render được cấu hình đúng. Không đưa SQL Server trên máy cá nhân ra Internet để nối tạm.

Sau khi backend khỏe và xác nhận URL của chính bạn, đặt `API_PROXY_TARGET` của Vercel bằng HTTPS origin Render, đặt `VITE_PREVIEW_MODE=false` và redeploy FE. Cookie và CSRF đi qua rewrite `/api` cùng origin. Không ghi GATEWAY_SECRET, mật khẩu SQL hoặc mật khẩu admin vào biến VITE_*.

Nguồn: [Render Web Services](https://render.com/docs/web-services), [Free limitations](https://render.com/docs/free), [Vercel rewrites](https://vercel.com/docs/routing/rewrites).
