# LifeTrickBE — Túi Khôn

Spring Boot 3.5 / Java 21 / SQL Server, cùng Node.js gateway. Có đăng nhập session/CSRF, phân quyền, sổ tay, ảnh/video, đóng góp, thẩm định độc lập và nhật ký.

FE ở repository riêng: https://github.com/taivipp1704-sudo/LifeTrickFE . Chưa triển khai backend lên Render vì chưa có SQL Server cloud. Giữ SQL Server, không thay bằng H2. Giao diện Vercel chạy chế độ xem thử.

## Quản lý từng phần

- BE/: controller, service, repository và cấu hình Spring.
- NodeGateway/: Node.js gateway, CORS, headers, rate limit và log.
- database/: migrations, seed, scripts backup; không lưu dữ liệu thật trong Git.
- env/: chỉ có mẫu .env, không có mật khẩu thật.
- deployment/: Dockerfile và hướng dẫn Render.

## Chạy local

Cần Java 21, Maven 3.9, Node 24, PowerShell 7, SQL Server và sqlcmd. Thiết lập database bằng Windows Authentication có quyền tạo login:

```powershell
pwsh -NoProfile -File scripts/setup-local.ps1
pwsh -NoProfile -File scripts/install-media-tools.ps1
npm --prefix NodeGateway ci
mvn -f BE/pom.xml verify
```

Từ terminal tại BE/ chạy `java -jar target/tui-khon-api-2.0.0.jar`. Ở terminal khác tại repository root chạy `npm start`. API bind loopback 8088, gateway 3000. Cho FE Vite chuyển /api đến gateway và khai báo origin FE chính xác trong APP_ORIGINS.

Setup tạo env/.env.local riêng với secret ngẫu nhiên, không tự đổi mật khẩu đã có. Admin ban đầu dùng email/mật khẩu từ file riêng đó. Đổi mật khẩu qua giao diện không cập nhật giá trị bootstrap.

## Chuẩn bị Render

Đọc deployment/RENDER.md. Dockerfile.render chạy gateway ở PORT và Java ở loopback. Không tạo SQL Server container trong gói Free. Filesystem Render Free không bền vững: chưa dùng để lưu ảnh/video quan trọng. Chưa có public SQL Server thì không bật dịch vụ thật hoặc trỏ website vào một backend chưa thuộc tài khoản của bạn.

## Kiểm tra và sao lưu

`npm run check` kiểm tra gateway/launcher. `npm run build:be` build Java. API integration test dùng database local thực, tạo tài khoản @example.test, giữ lịch sử và gỡ bài test công khai; không chạy trên production.

`database/scripts/backup.ps1` tạo .bak có checksum và xác minh, kèm media/SHA-256. Tạm ngừng ghi trước khi backup nhất quán. Giữ backup mã hóa ngoài máy. Không đưa .env thật, media, backup, log hay tool binary vào repository.

Bản local nguồn đã vượt 40 kiểm tra API và 24 kiểm tra browser trước khi tách repository; kiểm tra lại sau khi cấu hình cloud. Xem ARCHITECTURE.md để hiểu quyền, transaction và các giới hạn.
