# Chuẩn bị backend trên Render

Backend vẫn dùng SQL Server. Chưa tạo dịch vụ Render vì chưa có SQL Server trên cloud; bản Vercel dùng chế độ xem thử giao diện.

Khi đã có SQL Server cloud, tạo Web Service từ repository LifeTrickBE, branch main, Dockerfile `deployment/Dockerfile.render`, build context `.`. Chọn Free nếu chỉ thử ngắn hạn; không tạo disk hoặc database trả phí.

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

Chọn healthcheck `/healthz`. Launcher chạy Java ở loopback 8088 và Node ở PORT do Render cấp, có giới hạn heap để thử trên gói nhỏ. Chưa kiểm chứng mức dùng RAM dưới tải Render.

Filesystem của Render Free là tạm: ảnh/video sẽ mất sau restart/redeploy. Không dùng bản này để giữ dữ liệu quan trọng, chưa bật lưu video bền vững trên cloud. Tài khoản/bài/sổ tay chỉ bền vững nếu SQL Server ngoài Render được cấu hình đúng. Không đưa SQL Server trên máy cá nhân ra Internet để nối tạm.

Sau khi backend khỏe và xác nhận URL của chính bạn, đặt `API_PROXY_TARGET` của Vercel bằng HTTPS origin Render, đặt `VITE_PREVIEW_MODE=false` và redeploy FE. Cookie và CSRF đi qua rewrite `/api` cùng origin. Không ghi GATEWAY_SECRET, mật khẩu SQL hoặc mật khẩu admin vào biến VITE_*.

Nguồn: [Render Web Services](https://render.com/docs/web-services), [Free limitations](https://render.com/docs/free), [Vercel rewrites](https://vercel.com/docs/routing/rewrites).
