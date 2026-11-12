# Ghép C1 project members với nền Scrum board

Người dùng yêu cầu ghép và tự test sau khi xác nhận remote. Tích hợp origin/work/project-members tại401bf66 trên nền feat/backend-integration tại2c18460; giữ guard sprint, workflow/WIP và API cũ. Không đổi schema hoặc ghép C2/account.

## Sửa trong quá trình kiểm tra

ProjectMemberController kiểm quyền trước transaction. Với Open EntityManager In View, ProjectMember của actor có thể đã được nạp trước khi chờ khóa projects. Query lại sau khóa không tự refresh role trong entity đang managed. Do đó lockProject bây giờ refresh project và đúng membership `(projectId, actorId xác thực)` trước add/remove/updateRole kiểm quyền. Member đích khác actor; không clear toàn persistence context hoặc đổi policy toàn hệ thống.

Ba integration case dùng ProjectSecurity thật, giữ role cũ trong persistence context, REQUIRES_NEW thu hồi role rồi gọi add/role/remove: đã repro fail cả ba trên bản trước sửa và pass sau refresh. Transaction ngoài của test rollback chủ động sau assertion AccessDenied; không commit transaction đã bị service đánh rollback-only. Không có audit/ghi nghiệp vụ khi bị từ chối.

FE hợp whitelist member và board, giữ Origin/Bearer và menu hai domain. Probe completion lỗi sau commit đánh cache stale và khóa resubmit đến reload; gate refetch lỗi giữ draft và retry trong modal. Hướng dẫn luồng kiểm/API tại [MEMBERS_CONNECTED.md](https://github.com/ngothithanhh/jira_fe/blob/feat/backlog-ui-review/docs/MEMBERS_CONNECTED.md).

## Kiểm chứng tại máy chính 12/11/2026

- Clean verify:390 unit và58 integration PostgreSQL, không fail/error/skip, trong cluster mới55432 do scripts/test-postgres.ps1 -WithUnit tạo và xác minh đích. Kết quả cuối đạt sau sửa code và fixture actor/target/rollback.
- FE101 unit, TypeScript và production build đạt. Browser C1 đã kiểm add/search/filter/role/remove, assignedAt/count/F5, network/double-submit, second PM/global role/lead guard, gate503 và probe503 sau commit, đổi phiên chờ khóa/response chậm, developer và outsider.
- Hồi quy52 nhóm browser auth/project/issue/backlog/board đạt. Test C1 và regression project riêng, không thay issue/board SHOP; cuối cùng SHOP vẫn9issue/1board/4cardACTIVE.
- Một reviewer chỉ đọc theo10 tiêu chí AGENTS, finding được sửa/kiểm lại; không còn finding mở trong phạm vi review. Reviewer không tự chạy test; các kết quả trên do agent chính chạy.
- Backend demo cổng8080 dùng jira_demo_ready, Hibernate validate. Log/ảnh/credential ngoài Git tại outputs/backend-integration/members-merge. Không share jar chứa config local.

## Giới hạn

Case role bị thu hồi chứng minh persistence context và transaction DB thật; không đồng nghĩa đã kiểm mọi HTTP/OEIV interleaving hoặc tải lớn. Không websocket, không thêm role toàn cục từ màn members. Quyền lead/admin và một PM mỗi project giữ nguyên. Nhánh máy kia nên lấy nền mới trước C2 để giữ các shared BFF/session guards đã có.
