# Hệ thống quản lý dự án — Backend

Nhánh `feat/backend-integration` chứa backend đã kiểm thử và API cho FE phần 1. **Chưa hợp nhất với master remote** đang có thêm các thay đổi khác; đọc handoff trước khi tiếp tục phát triển.

- [Phân công hai máy và kế hoạch bắt đầu cho agent backend](docs/REMOTE_COLLABORATION.md)
- [Cấu hình, baseline, migration và khởi chạy](docs/RUN_AND_MIGRATE.md)
- [Tình trạng 19 nhóm chức năng và giới hạn](docs/FEATURE_STATUS.md)
- [Kiểm chứng backend qua HTTP thật](docs/FINAL_VERIFICATION_RESULTS.md)
- [Bộ test hồi quy](docs/REGRESSION_TESTS.md)
- FE tương ứng: [ngothithanhh/jira_fe — feat/backlog-ui-review](https://github.com/ngothithanhh/jira_fe/tree/feat/backlog-ui-review).

Dùng JDK17, Maven wrapper và PostgreSQL18 cho cấu hình đã kiểm. Copy `docs/application-example.properties` sang `src/main/resources/application.properties` và tự cấp biến môi trường ở máy của mình; file cá nhân bị ignore. Dựng DB rỗng riêng, chạy migration rồi dùng Hibernate validate. Không dùng dữ liệu/credential của máy gốc hoặc đưa chúng lên Git.

`docker-compose.yml` hiện chỉ dựng PostgreSQL16 local, không chạy app/migration/demo seed. PostgreSQL16 chưa được kiểm thực thi baseline trong đợt bàn giao này; không coi Compose là cách dựng đầy đủ đã được kiểm.
