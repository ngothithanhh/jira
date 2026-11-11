package com.example.jira.service.impl;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.entity.*;
import com.example.jira.mapper.IssueMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.IssueService;
import com.example.jira.service.NotificationService;
import com.example.jira.enums.NotificationType;
import com.example.jira.enums.PermissionName;
import com.example.jira.enums.StatusCategory;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {
    private final ProjectSecurity projectSecurity;
    private final ProjectRepository projectRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final ProjectIssueTypeRepository projectIssueTypeRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueTransitionRepository issueTransitionRepository;
    private final NotificationService notificationService;
    private final WorkflowRuleRepository workflowRuleRepository;

    /**
     * Dùng khi người dùng tạo issue; projectId, issueTypeId và parentIssueId là ID DB từ request.
     * Giữ kiểm quyền CREATE_ISSUE, sau đó khóa bản ghi projects cho đến khi transaction kết thúc.
     * Loại issue phải được gắn với project trong project_issue_types; issue cha nếu có phải cùng project.
     * Reporter là user đang đăng nhập, không lấy từ request; trạng thái đầu là TO_DO có statusId nhỏ nhất.
     * Chỉ sau khi đủ điều kiện mới đọc MAX(issue_number), cấp số/mã và lưu issues trong cùng transaction.
     * Ví dụ hai lượt tạo trong P10 cùng chờ khóa project 10: lượt sau đọc MAX sau khi lượt trước commit.
     * Lỗi làm rollback và nhả khóa; không tự retry. Unique ở DB là lớp chặn trùng bổ sung.
     */
    @Override
    @Transactional
    public IssueResponse createIssue(CreateIssueRequest request) {
        // Chặn người không có quyền trước khi lấy khóa và đọc cấu hình tạo issue.
        if (!projectSecurity.hasPermission(request.getProjectId(), PermissionName.CREATE_ISSUE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền tạo công việc trong dự án này!");
        }

        // Khóa project thay vì khóa issue để cả project chưa có issue cũng cấp số tuần tự được.
        Project project = projectRepository.findByIdForUpdate(request.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dự án!"));
        Issue issue = new Issue();
        issue.setProject(project);

        // ID loại tồn tại chưa đủ: cần đúng cặp project/loại, tránh dùng cấu hình của project khác.
        IssueType issueType = issueTypeRepository.findById(request.getIssueTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy loại công việc!"));
        if (!projectIssueTypeRepository.existsById(new ProjectIssueTypeId(project.getProjectId(), issueType.getIssueTypeId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại công việc chưa được cấu hình cho dự án này!");
        }
        issue.setIssueType(issueType);
        issue.setSummary(request.getSummary());
        issue.setDescription(request.getDescription());
        issue.setPriority(request.getPriority());

        // parentIssueId là ID issues, không phải số issue trong project; cha khác project bị từ chối trước khi lưu.
        if (request.getParentIssueId() != null) {
            Issue parentIssue = issueRepository.findById(request.getParentIssueId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy công việc cha!"));
            if (parentIssue.getProject() == null || parentIssue.getProject().getProjectId() != project.getProjectId()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Công việc cha phải thuộc cùng dự án!");
            }
            issue.setParentIssue(parentIssue);
        }

        // Lấy reporter từ danh tính đã xác thực; user không còn tồn tại thì dừng, chưa ghi issue.
        int userId = SecurityUtils.getCurrentUserId();
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Lỗi xác thực người dùng!"));
        issue.setReporter(reporter);

        // ID 1 không bảo đảm là trạng thái đầu. Chọn TO_DO đầu tiên theo ID để kết quả ổn định.
        IssueStatus defaultStatus = issueStatusRepository.findAll(Sort.by("statusId")).stream()
                .filter(status -> status.getStatusCategory() == StatusCategory.TO_DO)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Chưa cấu hình trạng thái TO_DO trong hệ thống!"));
        issue.setStatus(defaultStatus);

        // Khóa project vẫn được giữ từ trước MAX đến hết commit; DB unique chặn các bên ghi không lấy khóa.
        int maxIssueNumber = issueRepository.findMaxIssueNumberByProjectId(project.getProjectId()).orElse(0);
        int nextIssueNumber = maxIssueNumber + 1;
        issue.setIssueNumber(nextIssueNumber);
        issue.setIssueKey(project.getProjectKey() + "-" + nextIssueNumber);
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        issue.setStoryPoints(0);

        // Lưu issue rồi map response trong transaction; lỗi lưu/map đều rollback, không trả kết quả tạo dở.
        return IssueMapper.toResponse(issueRepository.save(issue));
    }

    /**
     * Dùng khi sửa nội dung issue; issueId là khóa issues trên URL, request chứa các trường muốn đổi.
     * Đọc issue để lấy ID project và giữ kiểm quyền EDIT_ISSUE trước khi sửa dữ liệu.
     * Chỉ ghi trường khác null và updated_at; version hiện có phát hiện hai lượt cùng sửa một bản cũ.
     * Lưu và map response trong một transaction; lỗi rollback, lỗi optimistic được xử lý ở tầng HTTP chung.
     */
    @Override
    @Transactional
    public IssueResponse updateIssue(int issueId, UpdateIssueRequest request) {
        // Issue không tồn tại trả 404; người không có quyền trên project của issue trả 403.
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy công việc!"));
        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền chỉnh sửa công việc này!");
        }

        // Giữ cách cập nhật từng phần: trường null không xóa giá trị đang có.
        if (request.getSummary() != null) issue.setSummary(request.getSummary());
        if (request.getDescription() != null) issue.setDescription(request.getDescription());
        if (request.getPriority() != null) issue.setPriority(request.getPriority());
        if (request.getStoryPoints() != null) issue.setStoryPoints(request.getStoryPoints());
        issue.setUpdatedAt(LocalDateTime.now());
        return IssueMapper.toResponse(issueRepository.save(issue));
    }

    /**
     * Dùng khi giao/bỏ giao issue; issueId là ID issues, assigneeId là ID users từ request hoặc null.
     * Giữ EDIT_ISSUE theo project của issue; user nhận phải có bản ghi project_members cùng project.
     * Null chỉ xóa assignee, không gửi thông báo. Có user nhận thì giữ cách gửi thông báo giao việc hiện tại.
     * Ghi assignee/updated_at và notification DB trong cùng transaction REQUIRED với NotificationService.
     * Nếu lưu issue, notification hoặc kiểm version lỗi thì rollback cả hai, không tự retry hay thêm chống trùng mới.
     */
    @Override
    @Transactional
    public IssueResponse assignIssue(int issueId, Integer assigneeId) {
        // Không cho thay đổi người nhận khi issue không tồn tại hoặc người gọi thiếu quyền sửa.
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy công việc!"));
        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền gán công việc này!");
        }

        // Cặp project/user bảo đảm người nhận thuộc dự án của issue, không chỉ tồn tại trong bảng users.
        if (assigneeId == null) {
            issue.setAssignee(null);
        } else {
            ProjectMember member = projectMemberRepository.findByProject_ProjectIdAndUser_UserId(issue.getProject().getProjectId(), assigneeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Người dùng này không thuộc dự án, không thể gán task!"));
            issue.setAssignee(member.getUser());

            // Actor là người đăng nhập; thông báo DB tham gia transaction này và chưa commit riêng.
            int actorId = SecurityUtils.getCurrentUserId();
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getFullName() : "Hệ thống";
            String message = actorName + " đã giao công việc " + issue.getIssueKey() + " cho bạn.";
            notificationService.sendNotification(assigneeId, issueId, actorId, NotificationType.ISSUE_ASSIGNED, message);
        }

        // Version của issue chặn hai lượt cùng ghi trên một phiên bản; lỗi sẽ rollback cả thông báo đã tạo.
        issue.setUpdatedAt(LocalDateTime.now());
        return IssueMapper.toResponse(issueRepository.save(issue));
    }

    /**
     * Dùng khi chuyển trạng thái; issueId là ID issues, statusId là ID issue_statuses đích từ request.
     * Giữ EDIT_ISSUE và kiểm cặp trạng thái nguồn/đích trong workflow_rules trước khi ghi.
     * Gửi lại trạng thái hiện tại trả ngay, không ghi lịch sử/thông báo; không coi đây là chống trùng mọi request.
     * Lưu issue_transitions (issue, nguồn, đích, user, thời điểm), rồi đổi issues.status/updated_at và tạo notification.
     * Cả ba loại bản ghi dùng chung transaction; lỗi notification hoặc version làm rollback toàn bộ.
     * Hai lượt cùng đọc trạng thái cũ chỉ một lượt ghi được version đó; không tự retry lượt thua.
     */
    @Override
    @Transactional
    public IssueResponse changeIssueStatus(int issueId, int statusId) {
        // Phải tìm issue và kiểm quyền theo project của chính issue trước khi đọc trạng thái đích.
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy công việc!"));
        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thay đổi trạng thái công việc này!");
        }
        IssueStatus toStatus = issueStatusRepository.findById(statusId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ!"));

        // Trạng thái không đổi không phát sinh lịch sử; trạng thái mới phải có cạnh workflow cho phép.
        if (issue.getStatus().getStatusId() == statusId) {
            return IssueMapper.toResponse(issue);
        }
        if (!workflowRuleRepository.existsByFromStatus_StatusIdAndToStatus_StatusId(issue.getStatus().getStatusId(), statusId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chuyển đổi trạng thái không hợp lệ theo quy trình (Workflow)!");
        }

        // Lưu nguồn trước khi thay issue.status để lịch sử giữ đúng cặp trạng thái và người thao tác.
        IssueTransition transition = new IssueTransition();
        transition.setIssue(issue);
        transition.setFromStatus(issue.getStatus());
        transition.setToStatus(toStatus);
        int userId = SecurityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(userId).orElse(null);
        transition.setUser(currentUser);
        transition.setTransitionedAt(LocalDateTime.now());
        issueTransitionRepository.save(transition);

        // Lịch sử vừa lưu chưa commit riêng; lỗi khi đổi issue hoặc gửi thông báo sẽ rollback cùng lịch sử.
        issue.setStatus(toStatus);
        issue.setUpdatedAt(LocalDateTime.now());
        Issue savedIssue = issueRepository.save(issue);

        // Giữ nội dung và người gửi thông báo hiện tại, không gọi dịch vụ ngoài hay thêm retry.
        int actorId = SecurityUtils.getCurrentUserId();
        User actor = userRepository.findById(actorId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Hệ thống";
        String message = actorName + " đã cập nhật trạng thái công việc " + issue.getIssueKey() + " thành " + toStatus.getStatusName() + ".";

        // Bỏ actor; reporter và assignee trùng user chỉ nhận một thông báo như luồng cũ.
        if (issue.getReporter() != null && issue.getReporter().getUserId() != actorId) {
            notificationService.sendNotification(issue.getReporter().getUserId(), issueId, actorId, NotificationType.ISSUE_STATUS_CHANGED, message);
        }
        if (issue.getAssignee() != null && issue.getAssignee().getUserId() != actorId &&
           (issue.getReporter() == null || issue.getReporter().getUserId() != issue.getAssignee().getUserId())) {
            notificationService.sendNotification(issue.getAssignee().getUserId(), issueId, actorId, NotificationType.ISSUE_STATUS_CHANGED, message);
        }
        return IssueMapper.toResponse(savedIssue);
    }

    /**
     * Dùng khi đọc danh sách theo projectId trên URL; giữ kiểm quyền VIEW_PROJECT trước truy vấn issues.
     * Chỉ đọc issue thuộc project đó rồi map response; thiếu quyền trả 403, không ghi DB.
     */
    @Override
    public List<IssueResponse> getIssuesByProject(int projectId) {
        // Kiểm quyền xem trước để người ngoài không đọc được danh sách issue của project.
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem dự án này!");
        }
        List<Issue> issues = issueRepository.findByProject_ProjectId(projectId);
        return issues.stream().map(IssueMapper::toResponse).toList();
    }
}
