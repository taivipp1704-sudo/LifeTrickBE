# Database

- migrations/: SQL Server DDL do Flyway áp dụng theo V1, V2, …; thêm migration mới thay vì sửa file đã chạy.
- seed/: hướng dẫn tham khảo; API nạp idempotent khi SEED_CONTENT=true.
- scripts/: thiết lập container, backup và mẫu restore sang database khác.
- backups/: dữ liệu riêng, đã Git ignore.

SQL Server local: TuiKhonApp. Credential nằm ở ../env/.env.local. Tài khoản tk_runtime_v2 dùng cho truy cập dữ liệu; tk_migrator_v2 chạy Flyway. Bảng phiên đăng nhập cũng nằm trong SQL Server.

Maven lấy migrations/ và seed/ làm resource khi build BE; Dockerfile API cũng sao chép đúng hai thư mục này. Không cần giữ một bản SQL khác trong BE.

Để lấy bản sao nhất quán trên máy local: dừng ứng dụng bằng npm stop ở thư mục dự án, chạy scripts/backup.ps1 của database, rồi npm start lại. Backup không tự xóa bản cũ hoặc tự cài lịch chạy.
