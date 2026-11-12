# Bàn giao audit log — WIP, chưa sẵn sàng chạy

Ngày bàn giao: 2026-11-13. Người dùng yêu cầu dừng triển khai để chuyển máy khác, commit/push nguyên trạng và tổng hợp công việc còn lại.

## 1. Nhánh và yêu cầu đã chốt

- Backend: https://github.com/ngothithanhh/jira, nhánh `feat/backend-integration`. Base trước audit: `6e20effe8532da14708550d9695110bb6f4790ca`.
- Frontend: https://github.com/ngothithanhh/jira_fe, nhánh `feat/backlog-ui-review`. Base trước audit: `86c7c7874479e818d83cb00941a6d57b385658bf`.
- Lấy cả hai nhánh. Commit mang nhãn WIP là điểm tiếp tục, không phải chức năng đã kiểm thử xong. Không force push hoặc tự ghép vào master.
- Audit phải có cả toàn hệ thống và riêng project. SYSTEM_ADMIN xem toàn bộ; lead hoặc thành viên có UPDATE_PROJECT chỉ xem project được phép. VIEW_PROJECT đơn thuần không đủ.
- Người dùng đã yêu cầu triển khai hai phạm vi này. Tiếp tục plan bên dưới; chỉ xin chốt lại nếu thay đổi đáng kể nghiệp vụ/thiết kế.
- Máy còn lại đang làm Gmail API/outbox → xác minh email đăng ký → chấp nhận/từ chối invitation. Không sửa chồng auth/registration/invitation. Fetch gần nhất thấy `origin/work/email-onboarding`, migration 011_mail_outbox.sql; dự kiến mail dùng 011..013. Audit chọn 014.
- Giữ shell/header/sidebar hiện tại; không dùng màn demo `/ui-review` hay DemoProvider thay API thật.

## 2. Trạng thái chính xác lúc dừng

**Đã sửa code, chưa được xác nhận hoàn tất:**

Backend:

1. `AuditLog.projectId` nullable là snapshot, không FK đến project, nhằm giữ log sau xóa dữ liệu.
2. Migration `scripts/migrations/014_audit_project_scope.sql`: thêm cột/index, backfill theo entity type và quan hệ đang còn trong DB, kiểm thời điểm tạo. PROJECT dùng entity_id; USER/ROLE giữ null. Đối tượng con mất thì không suy từ actor/description.
3. `AuditLogService.logProjectAction(action, entityType, entityId, projectId, description)` và implementation dùng chung transaction. `logAction` cũ tự đặt projectId cho type PROJECT, còn toàn cục giữ null. Đổi cách tìm actor để không nuốt lỗi DB thành actor hệ thống; phải kiểm lại khả năng tương thích.
4. `AdminQueryService`: thêm projectId/systemOnly; predicate project bắt buộc, actor null bằng IS NULL; DTO AuditEntry thêm projectId ở cuối.
5. `ProjectAuditQueryService` + `ProjectAuditController`: GET `/api/projects/{projectId}/audit-logs`, kiểm UPDATE_PROJECT rồi tồn tại, từ chối query projectId thứ hai.
6. Gắn snapshot cho log comment/delete issue đã có; thêm log cho create/update/assign/status issue, upload/delete attachment, sprint và membership sprint, backlog order, board/column/mapping/order/card move, update project. Đọc diff và caller để xác minh từng vị trí.

Frontend:

1. `src/features/connected/audit/{contract.ts,queries.ts,screen.tsx}`: whitelist/filter/parser, đọc qua khóa phiên + probe sau GET, màn read-only có filter/table/detail/paging.
2. Trang `/app/admin/audit` và `/app/projects/[projectId]/audit`.
3. Admin tab Nhật ký và project nav Nhật ký theo UPDATE_PROJECT; BFF server.ts thêm route audit dạng GET.
4. package.json đã thêm `tests/connected-audit.test.mjs` vào test:connected **nhưng file test này CHƯA tạo**, phải bổ sung trước chạy script.

**Chưa thực hiện:** migration DB demo, khởi chạy bản audit, unit/integration/browser test thành công, review độc lập, retest, hướng dẫn kiểm tra bản hoàn tất.

Kết quả kiểm tra thực tế:

- `git diff --check` không báo lỗi whitespace ở lần bàn giao; có cảnh báo LF/CRLF thông thường.
- Maven lần đầu báo biến changed trong ProjectServiceImpl bị chèn sai scope. Đã sửa khai báo về updateProject, bỏ khỏi create/read. Chưa có lượt build/test thành công sau sửa.
- Lượt Maven test sau chạy trong sandbox bị JVM ByteBuddy attach treo. Đã dừng đúng tiến trình lượt đó (Maven 24152, surefire 21928, attacher 6348 sau đối chiếu command line). Cần chạy lại ngoài sandbox nếu gặp tương tự.
- FE build `.next-account-build` dừng ở EPERM mkdir thư mục types, chưa đến kết luận TypeScript/UI đúng. Chạy lại với quyền thực thi phù hợp, không coi đây là build pass.
- **Không có kết quả kiểm thử audit thật, rollback, phân quyền HTTP hay chạy đồng thời để tuyên bố đã đạt.**

## 3. Việc cần làm ngay trước khi test

1. Đọc diff WIP ở cả hai repo; không chạy lại script thay thế text cũ trong outputs của máy nguồn. Các script đó chỉ phục vụ lần sửa vừa rồi, không idempotent.
2. Thêm mock AuditLogService cho các test dùng @InjectMocks service vừa thêm dependency. Hiện test chưa được cập nhật: BacklogRegressionTest, BoardCardRegressionTest, ContainerManagementRegressionTest, IssueRegressionTest; nested classes của CollaborationRegressionTest (attachment), ScrumRegressionTest (sprint/board).
3. Cập nhật verification của CollaborationAdminRegressionTest.editorCanModerateOtherAuthorsComment và IssueQueryRegressionTest delete: đổi 4 tham số logAction sang 5 tham số logProjectAction, projectId fixture là 10. Chỉ cập nhật assertion theo hợp đồng thật, không bỏ kiểm tra rollback/quyền/no-op.
4. Migration 014 hiện dùng ADD COLUMN/CREATE INDEX thường, chưa IF NOT EXISTS. Các migration cũ có test chạy SQL lặp. Hoàn thiện tính idempotent theo quy ước và tránh backfill ghi đè snapshot đã có (`project_id IS NULL`). Test cả lần đầu/lặp và ledger checksum. Không sửa migration 000..010 đã áp dụng.
5. Thêm test audit BE/FE/browser; package script đang trỏ file chưa có.
6. Kiểm lại logic no-op trước/sau khóa và trước khi sửa entity/rank. Board move có thể sinh CHANGE_STATUS và MOVE_BOARD_ISSUE cho hai thay đổi khác nhau; không tạo hai bản cùng một action. Không ghi log cho request bị từ chối/rollback.
7. Đọc lại comment tiếng Việt tại mọi hàm/khối vừa sửa; bổ sung ý nghĩa snapshot/projectId và tính cùng transaction. Không coi comment cũ đã đủ.

## 4. Luồng backend cần giữ và kiểm chứng

### Ghi log

1. Caller đọc issue/sprint/board/comment/attachment từ DB theo ID trên path; dùng project sở hữu để kiểm quyền cũ. ID entity không phải projectId, không lấy actorId từ request.
2. Giữ nguyên thứ tự khóa project, refresh entity, kiểm version, workflow, WIP, scope sprint và validation của luồng hiện tại. Không thêm retry hay thay business state ngoài plan audit.
3. Khi thay đổi thành công, gọi logProjectAction với project lấy từ entity tin cậy. Với delete, giữ project trước xóa; log không cần JOIN đối tượng còn tồn tại.
4. Audit ghi cùng transaction REQUIRED với nghiệp vụ; lỗi audit/flush phải rollback nghiệp vụ. Không async, không tự bắt rồi bỏ lỗi. Tác vụ không có danh tính có actor null; lỗi DB lấy user không được biến thành log hệ thống giả.
5. Không log password/token/file bytes/comment text hoặc giả before/after. Mô tả chỉ chứa action/ID/key cần trace. USER/ROLE là thay đổi toàn cục, không nhân vào mọi project dùng role.
6. Column/mapping dùng EntityType.BOARD và entityId=boardId, mô tả ID cột/status. Sprint membership dùng SPRINT/sprintId, mô tả issueId. Không thêm enum giả cho các bảng con.

### Đọc log

- Global GET `/api/admin/audit-logs`: SYSTEM_ADMIN trước query; projectId optional, cộng các filter khác.
- Project GET `/api/projects/{projectId}/audit-logs`: quyền UPDATE_PROJECT của đúng project; predicate `audit.projectId = pathProjectId` bắt buộc cho cả data/count. Không cho filter đổi scope, không JOIN parent entity bị xóa.
- Filter: entityType enum; entityId dương chỉ khi kèm entityType; actorId dương hoặc systemOnly=true, không cả hai; action exact trim/uppercase tối đa 50; createdFrom/To LocalDateTime; page>=0,size1..100,offset<=Integer.MAX_VALUE.
- Sort createdAt DESC, auditId DESC. LEFT FETCH actor ở data query; count không fetch. Actor null vẫn được trả. DTO chỉ chứa các trường cần hiển thị, projectId nullable.
- Legacy không xác định project vẫn chỉ admin xem; không bịa lịch sử cho thao tác chưa từng ghi.

## 5. Luồng FE và session phải kiểm chứng

1. Gate account/context thật theo actor/project. Admin phải systemAdmin, project cần UPDATE_PROJECT; hiển thị sidebar theo quyền nhưng BE vẫn là nơi quyết định.
2. useAudit dùng configurationRead dưới khóa phiên, rồi completeConnectedMutation để probe actor sau GET trước cache. Không đặt nested session lock. Kiểm signal, logout và generation/unmount để response cũ không đưa về UI.
3. Query key tách actor, project, applied filter, page và size. Draft chỉ apply khi submit; đổi actor/project phải bỏ modal/filter cũ.
4. 401/403/404/409 phiên thay đổi phải chặn table và modal cũ. 503 khi vẫn cùng actor giữ dữ liệu trước đó kèm lỗi/retry; không coi cache là đã tải mới. Test cả thu hồi UPDATE_PROJECT từ tab khác rồi focus.
5. Ngày theo DB LocalDateTime, không cộng Z/UTC đổi ngày. Từ ngày T00:00:00, đến ngày T23:59:59.999999. Không gửi ngày không tồn tại hoặc from>to.
6. Modal dùng snapshot DTO của row, escape text React; không gọi GET auditId chưa có, không fake before/after hoặc link entity không đủ thông tin.
7. BFF chỉ GET list/whitelist filter; từ chối key lạ, lặp key, scope override; no-store/private/Vary Cookie, Bearer lấy từ cookie, giữ 401/403/400. Không mở mutation/export audit.
8. Desktop/mobile: table có overflow-x riêng, tránh tràn cả trang. Giữ shell/header/sidebar bản đã duyệt.

## 6. Kiểm thử còn phải bổ sung/chạy

- BE unit: filter invalid, actor null/systemOnly, quyền project, snapshot đúng, no-op, failure audit không nuốt lỗi.
- PostgreSQL thật: hai project, entityId khác loại trùng số, predicate data/count/paging đúng; scope không đổi bằng filter; deleted entity vẫn có snapshot; USER/ROLE và legacy null không lọt project; rollback khi audit lỗi; migration first/repeat/backfill unknown. Test schema fixture hiện có dùng create-drop nên cần test riêng SQL migration 014, không nhầm entity DDL với migration pass.
- HTTP/JWT thật: SYS/global, lead/own project, member UPDATE đúng project, VIEW-only/outsider403, thu hồi quyền lần đọc sau403.
- FE contract/BFF/parser: query injection/date/id/page boundary, methods chưa mở, scope response sai, không chuyển secret; status/count giữ đúng. Tạo tests/connected-audit.test.mjs.
- Browser: filter/apply/reset, empty/error/retry, paging, system actor, detail text dài, project/global, refresh persistence, session A→B khi GET đang chờ/kết thúc, logout, revocation, mobile.
- Hồi quy các writer vừa chạm: issue/backlog, board/version/WIP, sprint/membership/complete, comment/attachment/local bytes, project/member, role R1/R2 và shared BFF/auth.
- Baseline trước audit để tham khảo: BE 427 unit + 93 integration; FE 155 tests. Đây không phải kết quả của WIP hiện tại.
- Theo AGENTS hiện hành, phần phức tạp cần 1 reviewer độc lập read-only theo 10 tiêu chí, đưa plan/diff/caller/kết quả test, xác minh finding, sửa thật/retest/re-review. Tác vụ này chưa được review.

## 7. Cách chạy và môi trường máy nguồn

Không chép credential vào Git/chat. Máy đích dùng cấu hình local riêng.

Backend Java17: máy nguồn `C:/Users/Admin/.jdks/ms-17.0.20.1`. Maven wrapper có sẵn. Unit: `./mvnw.cmd test`. Integration: `scripts/test-postgres.ps1` tạo cluster riêng loopback55432, database jira_regression và xác minh data_directory trước DDL. Không trỏ test create-drop vào DB demo.

Máy nguồn có PostgreSQL18 cổng5432, DB demo `jira_demo_ready`, config secret ở `src/main/resources/application.properties` ignored. Migration runner `scripts/migrate.ps1` có ledger/checksum. Backup DB trước 014, chạy migration 2 lần, kiểm ledger/index/backfill và dữ liệu demo. **014 chưa chạy trên demo lúc bàn giao.**

Trước WIP đang có server:

- Java PID5620, jar target/jira-0.0.1-SNAPSHOT.jar,8080, DB jira_demo_ready,ddl-auto=validate.
- FE PID20716 preview3130; PID2012 test3131. Cả hai dùng `.next-connected-build` của bản trước audit.
- WIP FE build chọn `.next-account-build` để không ghi đè server đang chạy. Chỉ dừng server sau đối chiếu command line/port; PID có thể đổi sau bàn giao. Stop Java trước package/clean trên Windows tránh khóa jar.
- Không rebuild vào dist đang có process đọc; test3131 dùng dist mới, xong mới swap preview3130. URL3130 hiện chưa phải bản audit WIP.

FE unit command: `node --experimental-strip-types --test tests/connected-*.test.mjs tests/planning-model.test.mjs tests/ui-demo-model.test.mjs`. Production build: đặt NEXT_DIST_DIR riêng rồi npm run build.

Browser tests có sẵn trong `tests/connected-*.browser.cjs`, dùng Playwright local. Máy nguồn có runtime Playwright ở Codex bundled dependencies/node/node_modules, Chromium headless shell1254 trong `%LOCALAPPDATA%/ms-playwright`. Có thể dùng runtime máy đích tương đương.

Private fixture trên máy nguồn (không có trên Git): outputs/backend-integration/credentials.local.json, members-merge/regression-credentials.local.json, roles-r2/*.local.*, admin-users/configuration-credentials.local.json. Đọc cấu trúc cẩn thận, không in password/token. SHOP project1 có 9 issues/1 board/4 active sprint issues cần giữ; hồi quy dùng project18, không reset dữ liệu demo chính.

Hồi quy cũ có runner local `outputs/backend-integration/admin-users/run-regression.ps1`; trước chạy cần prepare account-merge và restore-nr của collaboration. Sau chạy restore-nr + cleanup E2E sprints đúng project18/27. Không xóa tùy tiện seed/demo.

## 8. Kết thúc tác vụ ở máy nhận

1. Fix/hoàn thiện code và test nêu trên; review độc lập, retest.
2. Migration/backups thật, chạy preview đúng build; ghi rõ kết quả unit/PG/HTTP/browser và giới hạn.
3. Secret scan staged diff và lịch sử chưa push; không stage config/credential/backup/log/screenshot có dữ liệu riêng tư.
4. Commit/push lên hai feature branches, kiểm remote SHA; bàn giao luồng user: admin → Nhật ký → lọc project/entity/actor/action/date → chi tiết/paging; project lead → Nhật ký riêng; VIEW member không thấy link/direct API403; thử xóa entity vẫn thấy log.
5. Không tự làm thêm Google Sign-In/export hoặc đổi hệ thống phân quyền ngoài audit.

Tài liệu này phản ánh trạng thái WIP; agent nhận phải tự kiểm chứng trước khi báo hoàn tất.
