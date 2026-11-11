# Kết quả kiểm thử ngày 11 tháng 11 năm 2026

> Báo cáo này lưu baseline trước khi sửa lỗi. Kết quả sau sửa xem `PHASE1_RESULTS.md`; các nhận định "chưa sửa/chưa chạy DB" bên dưới chỉ áp dụng baseline.

## Kết quả đã chạy

Lệnh `mvnw.cmd -B -ntp clean test` chạy bằng JDK 17.0.20.1 kết thúc với mã lỗi do các case tái hiện bug. Không bật bỏ qua failure trong lượt này.

| Chỉ số unit và HTTP | Kết quả |
|---|---:|
| Số case thực thi | 235 |
| Đạt | 222 |
| Assertion thất bại | 12 |
| Exception ngoài kết quả mong đợi | 1 |
| Bỏ qua | 0 |

Cả 13 case đỏ đều là test `known-bug`. Exception là NPE trong `IssueMapper` khi tạo issue thường. Không còn lỗi mock/stub, khởi tạo MVC context hoặc sai HTTP status do bản thân test ở lượt chạy cuối.

Số case được tính từ các phần tử `testcase` và tổng kết Surefire. Với test `@Nested`, thuộc tính `tests` trên XML của lớp cha có thể bằng 0 dù XML chứa kết quả của lớp con; không cộng thuộc tính đó để báo thiếu số case.

## Bug được tái hiện

| Nhóm | Case đỏ | Hành vi quan sát |
|---|---:|---|
| Mapper quan hệ tùy chọn | 2 | Assignee hoặc issue cha null gây NPE |
| Tạo issue thường | 1 | Lưu issue rồi mapper trả lỗi |
| Quyền sửa role-permission | 1 | User thường không bị chặn khi gán permission |
| Quyền đọc project | 1 | Người ngoài vẫn đọc được chi tiết |
| Quyền xem thành viên | 1 | Người ngoài vẫn lấy được danh sách |
| Issue khác project vào sprint | 1 | Không bị từ chối |
| Mở lại sprint CLOSED | 1 | Start chuyển thẳng sang ACTIVE |
| Nhận thêm issue vào sprint CLOSED | 1 | Không bị từ chối |
| Nhóm trạng thái DONE | 1 | Tên khác Done bị coi là chưa hoàn thành |
| JWT sai cấu trúc, hết hạn, sai chữ ký qua HTTP | 3 | Exception parser thoát khỏi filter thay vì trả 401 có kiểm soát |

Đây là kết quả kiểm chứng qua unit/MockMvc. Những test này không sử dụng PostgreSQL ứng dụng hoặc Cloudinary thật. Chưa sửa các lỗi nghiệp vụ trong lượt viết test.

## Coverage dòng

JaCoCo tạo báo cáo từ lượt clean test trên, bằng goal `jacoco:report` riêng vì lifecycle dừng ở các case đỏ.

| Nhóm mã | Dòng đã chạy / tổng | Tỷ lệ |
|---|---:|---:|
| Service implementation | 565 / 646 | 87,5% |
| Security và JWT | 84 / 97 | 86,6% |
| Controller | 106 / 106 | 100% |
| Mapper | 153 / 170 | 90% |

Coverage dòng không phải coverage mọi nhánh hoặc mọi quy tắc. HTTP contract test mock service; 100% dòng controller không chứng minh phân quyền, transaction hay tích hợp FE đã hoàn chỉnh.

## PostgreSQL integration

Đã thêm 10 case PostgreSQL riêng cho unique email, JPQL theo project, khóa ghép backlog, rollback reorder/đóng sprint, seeder idempotency, optimistic locking, refresh đồng thời, tạo issue đồng thời và lỗi notification giữa các bước.

Docker CLI hiện không kết nối được Linux engine (`dockerDesktopLinuxEngine`). Lượt chạy integration dừng tại khởi tạo Testcontainers, trước khi Spring context và các case DB thực thi. Không coi lỗi môi trường này là một bug nghiệp vụ và không báo 10 case đã chạy hoặc đã đạt. Cần chạy lại khi Docker sẵn sàng theo hướng dẫn trong `REGRESSION_TESTS.md`.

Không có kiểm chứng runtime cho transaction, ràng buộc PostgreSQL, test proxy chèn điểm đồng bộ hoặc các race DB trong phiên này. Không dùng H2 để thay thế rồi kết luận PostgreSQL đã được kiểm thử.

## Review độc lập

Sub-agent đã review theo 10 tiêu chí được yêu cầu. Các finding về test race không đi qua service, đồng bộ sau đọc DB, xử lý kết quả refresh, cấu hình JWT test, assertions seeder và HTTP expected status đã được đối chiếu và sửa.

Lượt review cuối không còn finding cần sửa trong phạm vi test. Giới hạn còn lại là integration chưa chạy được trên Docker và bộ test chưa thể bao phủ mọi tổ hợp dữ liệu, lỗi dịch vụ ngoài hoặc interleaving đồng thời.

## Tệp kết quả có thể tái tạo

- `target/surefire-reports`: kết quả từng case unit và HTTP.
- `target/failsafe-reports`: báo cáo khởi tạo integration và các lượt chạy PostgreSQL sau này.
- `target/site/jacoco/index.html`: báo cáo coverage để xem từng lớp và nhánh.
- `docs/REGRESSION_TESTS.md`: lệnh chạy, phạm vi và bảng ánh xạ chức năng/test.
