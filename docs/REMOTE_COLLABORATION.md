# Hai máy cùng nối FE–BE theo nhóm chức năng

Cập nhật 12/11/2026 theo yêu cầu mới: **“bên kia cũng nối fe be đi cho nhanh”**. Bản này thay thế phân công chỉ làm backend. Máy kia nhận **B1 Notification, B2 Báo cáo Scrum**, làm đủ FE → BFF → API → DB. Đây là plan bàn giao, chưa phải xác nhận các màn đó đã nối xong.

## 1. Nhánh phải lấy

| Repo | Nhánh dùng làm nền đã tích hợp phần 1 | Vai trò |
|---|---|---|
| https://github.com/ngothithanhh/jira | `feat/backend-integration` | Backend đã có kiểm quyền, version, migration và account context; máy kia bắt đầu tại đây. |
| https://github.com/ngothithanhh/jira_fe | `feat/backlog-ui-review` | Toàn bộ UI demo và phần 1 nối backend; máy kia sửa FE trong domain được giao. |

**Chưa hợp nhất BE với master.** Khi fetch ngày 12/11, `origin/master` ở `bc4c79b`, có 7 commit không nằm trong nhánh local đã test. Nhánh tích hợp có 10 commit nghiệp vụ riêng trước đợt bàn giao; hai phía cùng sửa 37 file. Cả hai nhánh phải được giữ lại, không force-push hoặc chọn toàn bộ một phía để giải conflict.

Các commit remote cần đối chiếu: `2be7708` (issue API), `cd55324` (product/sprint backlog), `8e3f386` (board), `bd31937` (cộng tác), `dfda52c` (Cloudinary), `7225547` (version/report/admin), `bc4c79b` (message). Remote có thể tiếp tục thay đổi: fetch và ghi lại SHA trước mỗi đợt.

## 2. Chia trách nhiệm

| Người thực hiện | Phạm vi được nhận | Giữ ngoài phạm vi |
|---|---|---|
| Agent ở máy hiện tại | Project/issue/detail/comment/attachment → backlog → sprint/board từ FE tới BE; giữ auth/session/query lifecycle, BFF và shell/layout chung | Không sửa feature notification/report của máy kia. Chỉ nối phần tiếp theo sau khi người dùng kiểm phần 1. |
| Agent ở máy kia | B1 Notification, B2 Báo cáo Scrum: nối giao diện với API/DB thật, bổ sung BE khi plan cần, chuẩn bị dữ liệu nhỏ và tự test | Không nhận toàn bộ BE, không tự sửa core project/issue/sprint/board, auth/session hoặc merge master. Không làm full dataset/admin/members ở vòng đầu. |
| Người dùng | Chốt plan nghiệp vụ/hợp đồng khi có khác biệt, kiểm từng phần theo luồng đã bàn giao | Không coi im lặng hoặc thời gian chờ là đồng ý. |

**Không bắt máy kia chờ hợp nhất toàn bộ master hoặc làm xong seeder 90 issue.** Cả hai dùng baseline BE feat/backend-integration đã test. Sau B1 báo người dùng kiểm rồi dừng trước B2. Các việc hợp nhất BE/full dataset ở mục 3–4 dưới đây là đợt riêng về sau, không còn là nhiệm vụ đầu tiên.

### File và nhánh của máy kia

- FE mới: `src/features/connected/notifications/**`, `src/features/connected/reports/**`.
- Pages mới: `app/app/(protected)/notifications/page.tsx`, `app/app/(protected)/projects/[projectId]/reports/page.tsx`.
- Tests/docs domain riêng. Dùng UI mẫu notifications-screen.tsx, reports-screen.tsx/charts.tsx để tách phần trình bày; trang thật không dùng DemoProvider/act/fixture.
- BE được sửa theo plan: NotificationController/NotificationInboxController, NotificationServiceImpl/NotificationInboxService, NotificationRepository, NotificationResponse/NotificationMapper; ScrumReportController, service/impl/ScrumReportService, dto/report/ScrumReport và test liên quan.
- Tạo `work/notifications` trong cả hai repo từ nhánh nền. Sau B1 đã tích hợp, fetch nền mới rồi tạo `work/scrum-reports` cho B2. BE không sửa thì báo rõ, không cần commit rỗng.
- Shared `connected/{client.ts,session.tsx,server.ts,shell.tsx,screens.tsx}`, API catch-all, layout/provider/kit/package/config, security/auth, core issue/sprint/board và migration cũ do owner giữ. Patch cần thiết phải tách nhỏ để owner ghép, không sửa chung lẫn feature.

### B0 — patch BFF nhỏ, bàn giao trước

BFF phần 1 chưa cho notification/report đi qua. Máy kia lập plan B0/B1, tách commit B0 để owner hạ tầng ghép/test trước rồi dùng SHA đó làm nền. Hai bên không cùng sửa server.ts trong lúc này; không chép proxy/auth thành bản thứ hai.

1. connectedRequest → backendRoute phân loại public auth/protected GET/protected mutation bằng allowlist method/path/query. Không mở proxy tới URL/API tùy ý.
2. Mutation notification phải có Bearer và kiểm Origin; login/refresh/logout không gắn Bearer cũ. Không gọi establishSession cho response notification.
3. Giữ no-store, 401/403/404/409 và cơ chế refresh cũ. Xử lý **200 không body** của mark-one-read và **204** của delete, không json() rồi báo 502.
4. B1 mở GET notifications?unreadOnly, GET notifications/unread-count, PUT notifications/{id}/read, PUT notifications/read-all, DELETE notifications/{id}. B2 mở GET sprints/project/{projectId}, sprints/{id}/reports/summary, sprints/{id}/reports/burndown, projects/{id}/reports/velocity?limit=N; reference GET/context khác phải ghi rõ.
5. Test BFF/client cũ và case Origin/Bearer/allowlist/query/response rỗng. Menu/bell wiring cũng tách patch nhỏ để owner gắn sau khi component domain sẵn sàng.

### B1 — Notification đủ luồng

- Route thật `/app/notifications`; query key bắt đầu `connected`, có userId/filter để cache bị bỏ đúng khi đổi phiên.
- GET `/api/notifications?unreadOnly=false` đọc inbox theo JWT; GET unread-count trả `{count}`. ID item là notifications.notification_id, userId là recipient, actorId là người thao tác, issueId nullable là issues.issue_id; không suy các ID từ tên.
- DTO hiện không có projectId. Nếu cần link issue thì đề xuất field projectId nullable additive/mapper/test hoặc chờ owner xác nhận route thật; không đoán project hoặc tạo link 404. Item không issue/actor vẫn đọc/xóa được.
- PUT mark-one trả 200 rỗng, PUT read-all trả `{updated}`, DELETE trả 204. BE kiểm recipient trước ghi notifications.is_read/xóa notification; không sửa issue hoặc inbox khác. Chờ success rồi invalidate list/count, lỗi giữ dữ liệu và báo rõ, không retry mutation mù.
- Polling có giới hạn/focus chỉ khi cần, cleanup khi đổi user/unmount; không gọi đó là realtime push. NotificationBell export theo domain để owner gắn shell bằng patch riêng.
- Dữ liệu cục bộ qua API thật: thêm member/assign/comment tạo notification; có hai recipient/read/unread, ca null/legacy tách riêng. Không gửi dịch vụ ngoài và không cần full dataset.
- Test HTTP/browser/DB thật: login A → inbox → mark-one/count → mark-all/F5 → delete → login B không thấy cache A; B gọi ID của A phải bị từ chối. Kiểm pending/double-click, 200 rỗng/204, empty/403/404/network và mobile.
- Bàn giao SHA FE+BE, test/giới hạn, URL/tài khoản tự cấp ở máy mình và luồng người dùng kiểm; dừng trước B2.

### B2 — Báo cáo Scrum đủ luồng

- Route `/app/projects/[projectId]/reports`. projectId thuộc projects từ URL; sprintId chọn từ GET `/api/sprints/project/{projectId}` và phải thuộc cùng project. BE vẫn kiểm VIEW_PROJECT theo resource thực.
- GET sprint summary/burndown và project velocity theo docs/SCRUM_REPORT_API.md. Đọc sprint_metrics/sprint_metric_points; GET report không tự ghi/backfill lịch sử.
- Query key chứa user/project/sprint/limit, bỏ response cũ khi đổi lựa chọn nhanh; 403/404 không fallback fixture. Không có sprint thì empty state.
- Summary phân biệt current/committed/completed; null/unavailable giữ đúng nghĩa, không đổi thành 0. Burndown dùng chuỗi event/step, không tự bịa ngày quan sát; LocalDateTime theo quy ước API, không tự thêm Z đổi timestamp.
- Velocity dùng snapshot CLOSED, không tính lại completed từ issue hiện tại. ACTIVE thiếu baseline/CLOSED legacy thiếu history phải hiện chưa có dữ liệu tương ứng.
- Dữ liệu/test nhỏ qua core API: create issue/sprint → start → đổi status/estimate/scope → complete → đọc report; có ACTIVE/CLOSED/missing history. Không sửa thuật toán/core service chỉ để làm đẹp chart.
- Test F5 giữ số liệu DB, CLOSED snapshot bất biến sau sửa issue, đổi project/sprint nhanh không ló số liệu trước, outsider403, empty/error/mobile. Unit/mock không thay cho browser/API/DB thật.
- Bàn giao project/sprint ID do script máy mình tạo, URL và từng bước thao tác/kết quả; không hardcode ID1. Sau phần này báo người dùng rồi mới nhận nhóm khác.

### Prompt mới gửi agent kia

```text
Bạn làm fullstack Notification và Báo cáo Scrum trên máy riêng.
Clone jira nhánh feat/backend-integration và jira_fe nhánh feat/backlog-ui-review cạnh nhau; đọc docs/REMOTE_COLLABORATION.md trong BE.
Nhiệm vụ mới thay thế phân công chỉ làm BE/reconcile/dataset. Không chờ và không tự merge master.
Tạo work/notifications ở cả hai repo, trình plan B0/B1 theo luồng chạy để người dùng chốt trước khi code.
B0 tách patch BFF allowlist/Bearer/Origin/response rỗng thành commit riêng để owner ghép/test trước; không viết lại auth/proxy.
B1 nối inbox/bell/unread/mark-one/mark-all/delete với API và PostgreSQL thật. Test, commit/push FE+BE liên quan, báo SHA/URL/luồng kiểm rồi dừng.
Sau B1 được kiểm, fetch nền mới và lập plan B2 trên work/scrum-reports: selector project/sprint, summary, burndown event, velocity snapshot, null/unavailable và quyền project.
Chỉ sửa file domain/pages được giao. File shared auth/session/BFF/shell/kit và core issue/sprint/board không sửa lẫn feature; patch chung gửi riêng cho owner.
Không làm admin/members/full dataset hoặc đổi contract/migration cũ khi chưa chốt. Mỗi phần xong báo người dùng kiểm và chờ phản hồi.
```

## 3. Đợt riêng về sau — đối chiếu và hợp nhất backend

### Đọc và lập plan trước, chưa merge/code

1. Fetch hai nhánh, xác định merge-base; lập bảng endpoint trùng/khác gồm method, path, request, response, permission, version, transaction và lỗi HTTP. Đọc cả controller/service/repository/caller/test.
2. Ưu tiên đối chiếu `PUT /api/boards/{boardId}/issues/{issueId}/move`: hai nhánh có cách trả response/kiểm version khác nhau. Giữ kiểm `expectedVersion`, WIP và workflow của baseline đã test; không để hai controller đăng ký cùng route.
3. Đối chiếu notification read-all, issue search/detail/delete, sprint lifecycle/backlog, account enabled/locked/auth_version, report snapshots và admin routes. Bảng/member/key/ID phải giữ nghĩa cũ; không suy ID từ tên.
4. Báo rõ tính năng chỉ có ở remote như worklog/project version và dịch vụ Cloudinary; đề xuất giữ API mở rộng nào, API tương đương nào dùng một implementation. Không xóa tính năng chỉ để build xanh.
5. Với schema/entity mới từ remote, liệt kê cột/index/FK thêm, dữ liệu mặc định, đường migrate từ baseline đã có và tác động tới dữ liệu hiện hữu. Không sửa `000..006` đã phát hành; cần migration mới thì dùng số kế tiếp sau khi fetch xác nhận.
6. Trình plan theo thứ tự chạy thực tế, hàm nào nhận ID của bảng nào, kiểm gì và lỗi thì dừng ở đâu, đọc/ghi bảng/trường nào, transaction/lock/retry ra sao. Chờ người dùng chốt trước khi triển khai.

### Sau khi plan đã chốt

- Tạo nhánh `work/be-reconcile` từ `origin/feat/backend-integration`. Merge `origin/master` ở nhánh công việc; giải conflict theo bảng hợp đồng đã chốt, không cherry-pick mù hoặc dùng `-X ours/theirs` cho toàn repo.
- Giữ `/api/account/context`, statusId/issueTypeId thật trên IssueResponse, sự phân biệt global role/project member role, access-token version và refresh rotation. FE phần 1 đang phụ thuộc các hợp đồng này.
- Test compile/unit, khởi động app thật để bắt duplicate route, OpenAPI, DB migrations và HTTP smoke. Chạy PostgreSQL integration theo hướng dẫn; báo rõ cái gì chưa chạy.
- Phần auth/quyền/version/transaction phức tạp phải có review độc lập theo AGENTS của người dùng, reviewer chỉ đọc. Không tự nhận đã review độc lập nếu công cụ không có.
- Commit từng nhóm đã test; push nhánh công việc, gửi compare/PR và kết quả. Agent máy hiện tại tích hợp sau khi kiểm, không push trực tiếp vào master/nhánh nền.

### Điều kiện hoàn thành A

Một bộ API duy nhất không duplicate route; phần 1 FE vẫn login/refresh/logout/context/list project đúng. Tính năng bổ sung được ghi rõ, migration có đường nâng cấp, test có bằng chứng và giới hạn. Chưa coi merge được text là đã tích hợp thành công.

## 4. Đợt riêng về sau — full dataset và bootstrap

Chưa giao ở vòng notification/report. Chỉ bắt đầu khi người dùng giao riêng và chốt rõ baseline; tạo `work/be-demo-dataset`. Dữ liệu nhỏ để test B1/B2 vẫn được phép chuẩn bị riêng như mục 2.

### Phạm vi file dự kiến

- Thêm package `com.example.jira.demo` (builder/seeder/verifier), fixture không có secret, scripts/demo và docs/DEMO_DATASET.md.
- Thêm test tương ứng dưới regression/integration-test. Nếu cần marker dataset, đề xuất bảng/cách nhận diện trong plan trước; không tự thêm entity khiến profile production bắt buộc có bảng demo.
- Chỉ sửa cấu hình profile demo hoặc migration mới đã chốt. Giữ nguyên mặc định DatabaseSeeder, security/auth, mapper/DTO, script migration hiện có và report thuật toán production; nếu thực sự cần sửa thì nêu lý do và phối hợp owner trước.

### Luồng phải mô tả trong plan

1. Người chạy chủ động bật profile/flag seed và chỉ định DB demo. Verifier đọc current_database/schema và marker/version; sai đích hoặc DB lẫn dữ liệu không thuộc bộ seed thì dừng trước ghi. Mặc định tắt, không reset tự động lúc app chạy.
2. Đọc cấu hình role/permission/type/status/workflow hiện có, map bằng code/key và lấy ID thật; không giả định ID 1 là admin hoặc TODO. Không cấp quyền quản trị từ project membership.
3. Tạo khoảng 8 user giả lập → 3 project → members → board/cột/mapping → khoảng 90 issue → sprint/link/rank → comment/notification/audit và dữ liệu báo cáo. Mọi FK cùng đúng project; key/counter không tái dùng.
4. Project chính có 3 CLOSED, 1 ACTIVE, 1 FUTURE; một project mới rỗng, một persona ngoài project. Issue có tiêu đề/mô tả nghiệp vụ, ưu tiên/estimate/assignee/reporter/parent hợp lệ, không nhét trường chưa có API để trang trí.
5. Lịch sử giả lập chỉ tạo trên DB demo với nguồn rõ và timestamp có trình tự. Commitment/completion và event burndown khớp kịch bản; không bịa lịch sử trên DB người dùng, không đặt số trong FE hoặc sửa thuật toán báo cáo để khớp fixture.
6. Password lấy từ biến môi trường hoặc sinh cục bộ, hash qua cơ chế BE; không đưa password/token/Cloudinary key vào fixture, Git hoặc log. Chạy lại cùng dataset không nhân đôi, không đổi password hoặc ghi đè việc người dùng vừa sửa.
7. Kiểm invariant: tối đa một ACTIVE/project, không hai sprint mở giữ cùng issue, status-category/mapping/WIP hợp lệ, rank đầy đủ, snapshot nhất quán, inbox đúng recipient và không FK mồ côi.
8. Sau seed, thực hiện vài mutation qua API thật rồi đọc lại board/backlog/report để chứng minh dữ liệu tiếp tục hoạt động. Có lệnh reset riêng với kiểm đúng DB, không tự xóa DB vì test lỗi.

### Test và bàn giao B

- DB mới + migration → seed lần 1 → seed lần 2 không trùng → sửa một issue → seed lại không mất sửa → verifier → API đọc thật.
- Kiểm profile tắt không seed; sai DB/version hoặc dữ liệu lẫn phải dừng. Kiểm lỗi giữa seed không để marker thành công trên dữ liệu dở.
- Attachment chỉ seed khi có storage thật được cấu hình; không tạo URL giả và báo upload thành công. Không gửi email/thông báo ra dịch vụ ngoài để dựng demo.
- Bàn giao script chạy trên máy mới, cách cấp credential cục bộ, command/test/log đã lọc secret, ảnh/API sample và luồng người dùng kiểm. Không bàn giao dump DB cá nhân.

## 5. Dựng workspace ở máy kia

```powershell
mkdir scrum-workspace
cd scrum-workspace
git clone --branch feat/backend-integration https://github.com/ngothithanhh/jira.git jira
git clone --branch feat/backlog-ui-review https://github.com/ngothithanhh/jira_fe.git jira_fe
cd jira
git switch -c work/notifications
git fetch origin
git -C ../jira_fe switch -c work/notifications
```

Đọc trước: tài liệu này → docs/RUN_AND_MIGRATE.md → docs/COLLABORATION_ADMIN_API.md và docs/SCRUM_REPORT_API.md → FE docs/BACKEND_INTEGRATION_PLAN.md, docs/CONNECTED_PART1.md và code domain. Mục hợp nhất/full dataset là kế hoạch tương lai, không phải việc chặn B1/B2.

Backend cần JDK17, Maven wrapper, PostgreSQL18 đã được kiểm trên máy gốc. Copy docs/application-example.properties sang src/main/resources/application.properties và tự đặt DB_URL/DB_USER/DB_PASSWORD/JWT_SECRET ở máy mới. Tạo DB riêng rỗng rồi dùng scripts/migrate.ps1; chạy BE với validate. Không copy cấu hình/credential/JAR từ máy gốc. Script migration hiện hướng Windows; nếu máy kia dùng Linux phải đề xuất/test bootstrap tương đương, không tự thay bằng ddl-auto=update.

`docker-compose.yml` hiện chỉ là cấu hình DB local PostgreSQL16 đã có trong workspace, được đưa vào source theo yêu cầu push hết. Nó không chạy app/migration/seed và PG16 chưa được kiểm thực thi baseline; không coi `docker compose up` là đã dựng đủ hệ thống. Ưu tiên PG18 cho baseline đã test hoặc kiểm riêng PG16 trước khi dùng.

FE: Node24, `npm ci`, đặt BACKEND_API_URL phía server trỏ tới BE của chính máy đó, chạy `npm run dev -- --hostname 127.0.0.1 --port 3127`. Ứng dụng thật `/app/sign-in`, preview `/ui-review/projects` chỉ development. Production dùng HTTPS/public APP_ORIGIN đúng địa chỉ triển khai; localhost không phải địa chỉ máy khác.

DB demo và file credentials ở máy gốc không đi theo Git. Máy mới có thể đăng ký tài khoản để kiểm phiên, nhưng tài khoản mới chưa có project là đúng; cần seeder/thiết lập membership để có dữ liệu. Không yêu cầu agent tìm file C:/Users/Admin/... từ tài liệu lịch sử.

## 6. Quy ước phối hợp

- Mỗi đợt đọc status/fetch/log trước; không reset/restore/cherry-pick đè thay đổi của người khác, không force-push và không sửa checksum migration đã có trên nhánh nền.
- Chỉ một owner merge vào mỗi nhánh nền. Người còn lại push nhánh work, gửi SHA và danh sách file; owner review/test rồi tích hợp. Khi backend đổi hợp đồng phải gửi delta trước để FE điều chỉnh, không để FE tự đoán response mới.
- Có plan được người dùng chốt trước feature/fix/refactor. Comment tiếng Việt giải thích đầu vào/ID/kiểm tra/DB/kết quả/lý do ở hàm và khối nghiệp vụ chính. Không sửa generated code thủ công.
- Test theo rủi ro, report trung thực: unit/mock khác DB/HTTP/browser thật; build không chứng minh phân quyền hoặc đồng thời. Review phức tạp theo 10 tiêu chí: correctness, readability, maintainability, performance, security, error handling, testing, standards, architecture, documentation; finding có priority/file/dòng/trigger/hậu quả/bằng chứng.
- Sau mỗi phần đã test: commit → push nhánh work → báo file thay đổi, API/schema delta, test/giới hạn và **luồng người dùng kiểm**, rồi chờ phản hồi. Không tự làm tiếp mọi phần khi người dùng đang kiểm.
- Credentials/config/log/build/DB dump không push. Không nâng framework, deploy, gọi dịch vụ ngoài hoặc thêm tính năng ngoài nhiệm vụ để “tiện làm luôn”.

## 7. Mẫu báo cáo mỗi đợt

```text
Nhánh / commit:
Phạm vi hoàn thành và file chính:
API/schema thay đổi (nếu có):
Test đã chạy + kết quả; phần chưa kiểm:
Review + finding còn mở:
Luồng bạn kiểm: URL → tài khoản tự cấp ở máy mình → các thao tác → kết quả mong đợi.
Điểm cần người dùng chốt trước đợt tiếp theo:
```

Mốc kiểm ở máy gốc trước bàn giao: phần 1 có BE378 unit/MockMVC, FE34 unit/model, 13 kịch bản Chromium qua Next production→BE→PostgreSQL18, typecheck/build và migration fresh/repeat/guard/checksum. Đây là bằng chứng của nhánh tích hợp, không áp dụng tự động cho master remote hoặc kết quả sau merge.
