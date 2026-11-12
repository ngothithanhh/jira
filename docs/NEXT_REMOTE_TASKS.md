# Vòng tiếp theo — Thành viên dự án và tài khoản cá nhân

Ngày 12/11/2026. Người dùng xác nhận máy kia đã hoàn tất/test ổn Notification và Báo cáo Scrum, yêu cầu kiểm Git và giao việc tiếp. Bản này thay nhiệm vụ B1/B2 trong handoff trước; không giao lại các màn đã xong.

## 1. Kết quả kiểm Git

FE đã có các commit:

| Nhánh | Commit | Nội dung |
|---|---|---|
| work/notifications | 920f2b3 | BFF routes notification/report và test transport. |
| work/notifications | 0570367 | Inbox/list/count/read/read-all/delete nối API. |
| work/notifications | d1e9743 | Bell và menu đi tới inbox. |
| work/scrum-reports | 9abde88 | Project/sprint selector, summary, burndown và velocity đọc snapshot. |

`work/scrum-reports` chứa cả các commit Notification phía trước. Chưa thấy nhánh BE mới cho hai feature này; dùng API BE có sẵn là phù hợp. Không cần commit BE rỗng để đủ hai repo.

Các nhánh này xuất phát từ FE885df8e, chưa chứa FE483766b (project2A). Source khác nhau ở shared server.ts/shell.tsx/tests BFF; không copy nguyên server.ts của nhánh reports đè lên nền mới. Owner máy hiện tại sẽ ghép và kiểm shared patch riêng; **không bắt việc mới chờ hoàn tất merge này**.

Đã đọc code/commit và test transport được commit. Kết quả test máy kia được ghi nhận theo xác nhận người dùng; lệnh/log/browser evidence của máy kia chưa có trong Git, không coi đọc diff là đã tự chạy lại. Bổ sung tài liệu test và luồng bàn giao cho hai feature cũ khi tiện, không làm lại chức năng.

## 2. Phân công hiện tại

| Owner | Phạm vi |
|---|---|
| Máy hiện tại | Tiếp tục issue/detail/comment/attachment, product/sprint backlog, sprint/board và tích hợp các patch hạ tầng chung. |
| Máy kia, C1 | Thành viên project: danh sách/tìm kiếm, thêm, đổi role, gỡ; nối UI hiện có → BFF → API → PostgreSQL. |
| Máy kia, C2 | Sau C1 được kiểm: hồ sơ cá nhân và đổi mật khẩu, nối UI hiện có với account API thật. |

Không giao global admin/users/role-permission/type/status/workflow, full dataset hoặc hợp nhất BE master trong vòng này. Chỉ đọc danh mục role để chọn role project; không biến chức năng thành CRUD role toàn cục.

## 3. Lấy đúng nền cho việc mới

Hai repo cạnh nhau; fetch rồi tạo nhánh mới từ **nhánh nền**, không từ work/scrum-reports cũ:

```powershell
git -C jira fetch origin
git -C jira_fe fetch origin
git -C jira switch -c work/project-members origin/feat/backend-integration
git -C jira_fe switch -c work/project-members origin/feat/backlog-ui-review
```

Mốc nền đã có: FE483766b, BEe8db087; dùng tip mới nhất sau khi fetch. Không reset worktree đang bẩn; lưu phần sửa có thật/checkout riêng khi cần. Giữ work/notifications và work/scrum-reports để owner ghép, không xóa hoặc force-push.

C2 tạo `work/account-profile` ở cả hai repo từ nền mới sau C1 đã tích hợp. Nếu feature chỉ sửa FE, báo rõ BE không đổi thay vì tạo commit rỗng. Không đưa các nhánh nền/master cũ vào giữa feature bằng merge toàn bộ.

## 4. C1 — Quản lý thành viên dự án

### Phạm vi source

- UI mẫu: FE `src/features/ui-demo/screens/members-screen.tsx`. Tách phần trình bày hoặc dùng kit hiện có; không thiết kế/dựng lại màn, không lấy DemoProvider/fixture vào route thật.
- FE mới: `src/features/connected/members/**`, `app/app/(protected)/projects/[projectId]/members/page.tsx`, tests và docs/MEMBERS_CONNECTED.md riêng.
- BE khi cần: ProjectMemberController/ProjectMemberServiceImpl, DTO member/candidate và query/repository riêng cho danh sách ứng viên. Giữ security/issue/sprint/board service/ProjectStatsRepository/migrations đã phát hành.
- Shared BFF server.ts, client.ts, session.tsx, shell.tsx, project queries/layout/gate/kit/package/config: không sửa lẫn domain. Allowlist/điều hướng cần thiết tách commit nhỏ và gửi owner ghép/test; đồng bộ lịch sửa trước để hai bên không cùng sửa server.ts.

### API và ý nghĩa ID hiện có

| Method/path | Dữ liệu/quyền/kết quả |
|---|---|
| GET /api/projects/{projectId}/members | VIEW_PROJECT; list userId/userName/fullName/email/roleId/role/assignedAt. |
| POST /api/projects/{projectId}/members | ADD_MEMBER; body `{userId,roleId}`; thành công200 rỗng. |
| PUT /api/projects/{projectId}/members/{userId}/role | ADD_MEMBER; body `{roleId}`; thành công204. |
| DELETE /api/projects/{projectId}/members/{userId} | DELETE_MEMBER; thành công200 rỗng. |
| GET /api/user-role | Danh mục `{role,roleName}`: field `role` là roleId, không phải userId. |

projectId là projects.project_id từ URL, userId là users.user_id người được thêm/sửa/gỡ, actor lấy từ JWT. roleId thuộc user_roles nhưng cặp gán ở **project_members**, không ở user_role_assignments. `SYSTEM_ADMIN` không được gán làm role project.

### Plan C1 phải mô tả trước khi code

1. Route projectId → context/quyền + ProjectGate có sẵn → GET members → mapper → bảng/filter. Query key có connected/userId/projectId; không có quyền403, project thiếu404, không fallback fixture.
2. Mở form thêm chỉ khi có ADD_MEMBER. Dùng ID thật từ ứng viên/role; không lấy project lead, email hoặc vị trí dropdown thay userId/roleId.
3. **Ứng viên là điểm cần chốt:** hiện `/api/user` trả danh sách users toàn hệ thống, chưa phải search ứng viên có quyền/phân trang. Trình phương án endpoint scoped theo project + ADD_MEMBER, query có giới hạn và field tối thiểu; không mở rộng danh sách/email toàn hệ thống chỉ để form dễ làm. Bổ sung BE nếu plan được chốt, không tạo tài khoản tự động từ email không tồn tại.
4. POST/đổi role/gỡ dùng connectedMutation với actor/scope, không dùng connectedRequest recovery mặc định cho mutation. Chỉ BE quyết định quyền; dropdown ẩn SYSTEM_ADMIN không thay enforcement BE.
5. BE giữ project lock/transaction hiện có; chặn trùng membership, hơn một PROJECT_MANAGER và gỡ lead còn được projects tham chiếu. Body sai400, thiếu403/404, conflict409; lỗi giữ form và không thêm notification/audit thành công giả.
6. Ghi project_members đúng cặp project/user; đổi role giữ assignedAt và không cấp admin toàn cục. Add tạo notification/audit theo transaction BE; không tự thêm notification FE. Remove không tự xóa user, issue hoặc sửa reporter/assignee lịch sử ngoài nghiệp vụ hiện có.
7. Success invalidate members, context quyền liên quan và list/detail project counts. Nếu tự đổi role/gỡ mình thì tải lại context để UI đổi đúng quyền. Query cũ/response muộn sau đổi tài khoản không ghi vào cache user mới; completion dùng helper đã có để chặn cookie đổi trước BroadcastChannel.
8. Hộp confirm/pending cho gỡ, form có lỗi/empty/loading/readonly/403/404. Giữ lead hiển thị theo dữ liệu thật: lead có thể chưa có project_members; không giả nó thành một membership đã lưu.

### Test C1 và điều kiện bàn giao

- DB demo riêng: PM/lead + developer + outsider và hai project. Tạo ứng viên/tài khoản qua cơ chế được kiểm, không dùng DB jira người dùng hoặc gửi email ngoài.
- Browser/HTTP thật: PM mở members → tìm ứng viên → thêm developer → F5/count tăng → đổi role → F5 giữ assignedAt → gỡ → F5/count giảm.
- Test trùng member, PM thứ hai, gỡ lead, SYSTEM_ADMIN role project, truy cập project khác và API trực tiếp của developer/outsider. Kiểm role toàn cục của người được gán vẫn giữ nguyên.
- Test nhập/search ID/filter/response rỗng200/204, double-submit, network error, đổi user trong lúc request đang chờ và mobile. Test có quyền đổi role sau khi chờ khóa đúng theo BE; không tự tuyên bố mọi interleaving đã kiểm.
- Unit/typecheck/build và BE tests tương ứng; phần phức tạp review chỉ đọc theo AGENTS. Báo rõ gì mock và gì chạy browser/DB thật.
- Commit/push work/project-members; gửi SHA FE+BE, API/schema delta, test/giới hạn và luồng người dùng kiểm. Dừng chờ kiểm C1 trước C2.

## 5. C2 — Hồ sơ cá nhân và đổi mật khẩu

### Phạm vi/API

- UI mẫu FE account-screen.tsx; route thật `/app/account` đã đọc profile, cần nối edit/password. FE domain mới `features/connected/account/**`, tests và docs/ACCOUNT_CONNECTED.md. Wiring screens.tsx/account page/shared transport tách patch nhỏ cho owner.
- PATCH `/api/account/profile`: userName tùy chọn1..50, fullName tối đa100; trả UserResponse. Email/ID/role/enable không nằm trong form ghi.
- POST `/api/account/password`: oldPassword/newPassword; mới ít nhất8 ký tự và tối đa72 **byte UTF-8**, thành công204. Đúng mật khẩu cũ mới hash mới/tăng users.auth_version/revoke toàn bộ refresh/audit cùng transaction. Sai mật khẩu400; không giả204 khi BE lỗi.

### Plan C2 phải mô tả

1. Context thật → form profile theo user đang login, có dirty/save/cancel/pending. Actor lấy JWT; không gửi target userId. Dùng actor-bound mutation/scope để response userA không sửa UI userB.
2. Success profile: cập nhật context và các tên hiển thị liên quan bằng dữ liệu máy chủ; F5 giữ thay đổi. Không coi userName là email đăng nhập, không tự sửa email hoặc authority. Avatar chưa có API thật: không đưa blob/localStorage vào như upload thành công; ghi rõ giới hạn, không dựng lại chức năng fake.
3. Password form kiểm old/new/confirm ở FE; confirm không cần gửi BE. Không lưu password hoặc echo/log request. Cần kiểm byte UTF-8, không chỉ maxlength ký tự.
4. Sau BE204, phiên cũ không còn hiệu lực. **Không dùng completeConnectedMutation xác minh /me với JWT cũ để chốt password**, vì auth_version đã tăng. Trình patch BFF chuyên biệt: thành công clear đúng HttpOnly cookie paths rồi dưới khóa phát ended/clear cache, chuyển login, không auto refresh để kéo phiên cũ sống lại.
5. Tab khác được báo thoát; login lại bằng mật khẩu mới, mật khẩu cũ thất bại, access/refresh cũ không dùng được. Đây là API thật với dữ liệu test, không thay mật khẩu tài khoản thật của người dùng để chứng minh.
6. Timeout sau ghi có thể là BE đã đổi password; không retry mù hoặc khẳng định password chưa đổi. Đóng phiên cục bộ an toàn theo quy trình chung, báo rõ trạng thái chưa xác nhận và hướng dẫn thử đăng nhập bằng mật khẩu mới. Lỗi password sai400 giữ phiên và không làm mất dữ liệu form ngoài ý muốn.

### Test/bàn giao C2

Profile save/cancel/F5; body không thể sửa email/role/user khác; switch account response chậm; password sai, confirm sai, UTF8>72byte; đổi thành công/logout nhiều tab/login mới/token cũ bị từ chối; browser→Next và Next→BE lỗi mạng. Chạy đúng tài khoản giả lập có credential local, không commit. Test/build/review và gửi luồng kiểm rồi dừng như C1.

## 6. Quy ước và prompt gửi agent kia

Giữ AGENTS: plan theo luồng chạy/ID/validation/DB/state/transaction/retry/lý do, người dùng chốt trước code; comment tiếng Việt phục vụ review. Không tự merge master, đổi contract rộng, nâng framework hoặc sửa migration cũ. Scope cần shared change phải tách patch/commit rõ; chỉ một owner ghép mỗi nhánh nền.

```text
Đã nhận Notification/Reports của bạn ở các commit 920f2b3, 0570367, d1e9743 và 9abde88. Giữ nguyên hai nhánh bàn giao; owner sẽ ghép shared code với project2A, bạn không cần làm lại hoặc chờ full merge.
Nhiệm vụ tiếp theo C1 là quản lý thành viên dự án từ UI đã có tới API/DB thật; C2 sau khi C1 được kiểm là hồ sơ cá nhân/đổi mật khẩu.
Đọc BE docs/NEXT_REMOTE_TASKS.md. Fetch và tạo work/project-members ở cả hai repo từ origin/feat/backend-integration và origin/feat/backlog-ui-review mới nhất, không lấy work/scrum-reports cũ làm nền.
Lập plan C1 cụ thể trước code, nhất là ứng viên user scoped theo project/quyền, roleId field role của API danh mục, project_members khác global roles, và invalidation context/counts.
Chỉ sửa features/connected/members/**, members/page và BE member/candidate tương ứng sau chốt plan. BFF/menu/shared scope helper gửi patch nhỏ riêng, không ghi đè server/client/session/project layout bằng bản cũ.
Giữ kiểm một PM, duplicate, lead, SYSTEM_ADMIN; không tự cấp quyền để UI chạy được. Dùng helper actor-bound mutation/completion của project2A, test cả response muộn sau đổi tài khoản.
Tự test/build/review cần thiết, push work/project-members, gửi SHA + bằng chứng test/giới hạn + URL/tài khoản local + từng bước kiểm; dừng chờ người dùng trước C2.
Bổ sung docs/test commands và luồng kiểm Notification/Reports cũ khi tiện vì hiện branch chưa có tài liệu bàn giao test; không cần chạy/làm lại toàn bộ nếu kết quả đã có.
```

Mẫu bàn giao: branch/SHA FE+BE; file/phạm vi; contract/schema delta; test và giới hạn; review/finding; URL → account tự cấp trên máy mình → thao tác → DB/UI kết quả; điểm cần chốt trước phần sau.
