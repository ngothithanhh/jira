package com.example.jira.service.impl;

import com.example.jira.enums.EntityType;

import com.example.jira.dto.sprint.CreateSprintRequest;
import com.example.jira.dto.sprint.SprintResponse;
import com.example.jira.entity.*;
import com.example.jira.enums.SprintState;
import com.example.jira.enums.PermissionName;
import com.example.jira.enums.StatusCategory;
import com.example.jira.mapper.SprintMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.SprintService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SprintServiceImpl implements SprintService {

    private static final List<SprintState> OPEN_STATES = List.of(SprintState.FUTURE, SprintState.ACTIVE);

    private final com.example.jira.service.AuditLogService audit;
    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final SprintIssueRepository sprintIssueRepository;
    private final ProjectSecurity projectSecurity;
    private final EntityManager entityManager;
    private final SprintMetricsService metrics;

    /**
     * Tạo bản nháp FUTURE trong projects.project_id do request gửi, sau khi kiểm tra MANAGE_SPRINT.
     * Ngày còn thiếu được giữ nguyên; có đủ hai ngày thì phải bắt đầu trước kết thúc.
     * Chỉ ghi hàng sprints mới, chưa thay đổi backlog hoặc bắt đầu sprint.
     */
    @Override
    @Transactional
    public SprintResponse createSprint(CreateSprintRequest request) {
        // Lấy đúng dự án của request; thiếu dự án hoặc quyền thì dừng trước khi ghi sprint.
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dự án"));
        requirePermission(project.getProjectId(), PermissionName.MANAGE_SPRINT);
        validateDates(request.getStartDate(), request.getEndDate());

        // Lưu bản nháp với ngày tùy chọn từ request, không tự chuyển sang ACTIVE.
        Sprint sprint = new Sprint();
        sprint.setProject(project);
        sprint.setSprintName(request.getSprintName());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setGoal(request.getGoal());
        sprint.setCreatedAt(LocalDateTime.now());
        sprint.setState(SprintState.FUTURE);
        sprint = sprintRepository.save(sprint);

        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("CREATE_SPRINT",EntityType.SPRINT,sprint.getSprintId(),project.getProjectId(),"Tạo sprint FUTURE");
        return SprintMapper.toResponse(sprint);
    }

    /** Đọc sprints.sprint_id từ URL; cần VIEW_PROJECT của dự án sở hữu trước khi trả DTO, không ghi DB. */
    @Override
    @Transactional(readOnly = true)
    public SprintResponse getSprint(int sprintId) {
        // ID sprint quyết định dự án cần kiểm tra quyền, không nhận project ID riêng từ người gọi.
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sprint"));
        requirePermission(sprint.getProject().getProjectId(), PermissionName.VIEW_PROJECT);
        return SprintMapper.toResponse(sprint);
    }

    /** Đọc danh sách theo projects.project_id từ URL sau khi kiểm tra dự án và VIEW_PROJECT; không ghi DB. */
    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> getSprintsByProject(int projectId) {
        // Dừng nếu dự án không tồn tại hoặc thiếu quyền, trước khi truy vấn danh sách sprint.
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dự án"));
        requirePermission(projectId, PermissionName.VIEW_PROJECT);
        return sprintRepository.findByProject_ProjectId(projectId).stream()
                .map(SprintMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Thêm issues.issue_id từ URL vào sprints.sprint_id cùng dự án, khi có MANAGE_SPRINT và sprint chưa đóng.
     * Khóa projects trước kiểm tra liên kết mở khác để hai lượt thêm cùng issue phải chạy lần lượt.
     * Giữ lịch sử CLOSED; thêm lại đúng cặp đã có thì không ghi lại added_at.
     */
    @Override
    @Transactional
    public void addIssueToSprint(int sprintId, int issueId) {
        // Khóa dự án và đọc mới trạng thái trước khi kiểm tra CLOSED và quyền sở hữu issue.
        Sprint sprint = getSprintForUpdate(sprintId);
        requireOpenSprint(sprint);
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy issue"));
        requireSameProject(sprint, issue);
        requireNoOtherOpenSprint(issueId, sprintId);

        // Cặp đã có thì giữ nguyên dữ liệu; lịch sử sprint đóng khác không cản lần thêm mới.
        if (!sprintIssueRepository.existsById(new SprintIssueId(sprintId, issueId))) {
            // Rời product backlog thì bỏ rank cũ; nếu quay lại sẽ xếp sau các vị trí người dùng đã chốt.
            issue.setBacklogPosition(null);
            saveSprintIssue(sprint, issue);
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("ADD_SPRINT_ISSUE",EntityType.SPRINT,sprintId,sprint.getProject().getProjectId(),"Thêm issue ID "+issueId);
            // Liên kết ACTIVE thay đổi scope burndown; query snapshot flush cùng transaction hiện tại.
            metrics.captureActiveProject(sprint.getProject().getProjectId());
        }
    }

    /**
     * Gỡ đúng cặp sprints.sprint_id/issues.issue_id cùng dự án sau khi kiểm tra MANAGE_SPRINT.
     * Giữ khóa project đến hết transaction để không gỡ đồng thời với đóng/chuyển sprint.
     * CLOSED bị từ chối; sprint mở chỉ xóa sprint_issues, không xóa issue hoặc lịch sử sprint khác.
     */
    @Override
    @Transactional
    public void removeIssueFromSprint(int sprintId, int issueId) {
        // Không cho API gỡ lịch sử CLOSED hoặc dùng issue của dự án khác.
        Sprint sprint = getSprintForUpdate(sprintId);
        requireOpenSprint(sprint);
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy issue"));
        requireSameProject(sprint, issue);

        // Chỉ reset rank khi thật sự gỡ một liên kết, tránh request lặp xóa thứ tự product backlog đã chốt.
        boolean existed = sprintIssueRepository.existsById(new SprintIssueId(sprintId, issueId));
        if (existed) issue.setBacklogPosition(null);
        // Filter cả hai ID giữ nguyên mọi liên kết khác của issue.
        sprintIssueRepository.deleteBySprint_SprintIdAndIssue_IssueId(sprintId, issueId);
        if (existed) {
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("REMOVE_SPRINT_ISSUE",EntityType.SPRINT,sprintId,sprint.getProject().getProjectId(),"Gỡ issue ID "+issueId);
        }
        // Ghi sự thay đổi scope sau khi gỡ, không snapshot FUTURE hoặc sửa commitment lúc bắt đầu.
        metrics.captureActiveProject(sprint.getProject().getProjectId());
    }

    /**
     * Bắt đầu sprint theo sprints.sprint_id sau khi khóa dự án và kiểm tra MANAGE_SPRINT.
     * Chỉ FUTURE được sang ACTIVE; kiểm tra cặp ngày nếu đủ và không cho dự án có hai ACTIVE.
     * Hai lượt bắt đầu cùng dự án chờ cùng khóa projects: lượt sau thấy ACTIVE đã commit và bị từ chối.
     */
    @Override
    @Transactional
    public void startSprint(int sprintId) {
        // Đọc mới sau khóa để chặn mở lại CLOSED hoặc bắt đầu lại ACTIVE bằng dữ liệu cũ.
        Sprint sprint = getSprintForUpdate(sprintId);
        if (sprint.getState() != SprintState.FUTURE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể bắt đầu sprint FUTURE");
        }
        validateDates(sprint.getStartDate(), sprint.getEndDate());
        if (sprintRepository.existsByProject_ProjectIdAndState(sprint.getProject().getProjectId(), SprintState.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dự án đã có sprint ACTIVE");
        }

        // Chỉ ghi sprints.state khi mọi điều kiện đã đạt; giữ nguyên ngày tùy chọn và backlog.
        sprint.setState(SprintState.ACTIVE);
        sprintRepository.save(sprint);
        // Chụp commitment đúng lúc bắt đầu; lỗi lưu snapshot rollback cả chuyển ACTIVE.
        metrics.start(sprint);
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("START_SPRINT",EntityType.SPRINT,sprintId,sprint.getProject().getProjectId(),"Bắt đầu sprint: FUTURE → ACTIVE");
    }

    /**
     * Hoàn tất sprint nguồn ACTIVE; moveToSprintId là ID sprint nhận việc, null nghĩa là trả về backlog.
     * Khóa dự án nguồn, kiểm tra đích khác nguồn, cùng dự án và chưa CLOSED trước mọi thay đổi.
     * Giữ liên kết DONE theo status_category; việc còn lại được gỡ/chuyển qua repository trong cùng transaction.
     * Chỉ đặt sprints.state=CLOSED sau chuyển việc; lỗi DB rollback toàn bộ, không gọi API gỡ/thêm công khai.
     */
    @Override
    @Transactional
    public void completeSprint(int sprintId, Integer moveToSprintId) {
        // Chỉ ACTIVE được đóng; lượt đóng đồng thời đọc lại CLOSED sau khi lấy khóa và dừng.
        Sprint sprint = getSprintForUpdate(sprintId);
        if (sprint.getState() != SprintState.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể hoàn tất sprint ACTIVE");
        }

        // Đích phải hợp lệ kể cả nguồn không còn việc chưa xong; chưa sửa trạng thái hoặc backlog.
        Sprint nextSprint = null;
        if (moveToSprintId != null) {
            if (moveToSprintId == sprintId) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint đích phải khác sprint nguồn");
            }
            nextSprint = sprintRepository.findById(moveToSprintId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sprint đích"));
            if (nextSprint.getProject().getProjectId() != sprint.getProject().getProjectId()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint đích không thuộc cùng dự án");
            }
            entityManager.refresh(nextSprint);
            requireOpenSprint(nextSprint);
        }

        // Kiểm tra mọi việc trước khi ghi: sai dự án hoặc liên kết mở khác làm dừng cả lượt đóng.
        List<SprintIssue> sprintIssues = sprintIssueRepository.findBySprint_SprintId(sprintId);
        List<Issue> unfinishedIssues = sprintIssues.stream().map(SprintIssue::getIssue)
                .filter(issue -> issue.getStatus() == null || issue.getStatus().getStatusCategory() != StatusCategory.DONE)
                .collect(Collectors.toList());
        for (SprintIssue sprintIssue : sprintIssues) {
            requireSameProject(sprint, sprintIssue.getIssue());
        }
        if (nextSprint != null) {
            for (Issue issue : unfinishedIssues) {
                requireNoOtherOpenSprint(issue.getIssueId(), sprintId);
            }
        }

        // Chốt completed trước khi gỡ việc chưa DONE để velocity không phụ thuộc membership sau chuyển.
        metrics.finish(sprint);
        // Chuyển trực tiếp khi nguồn còn ACTIVE; null đích chỉ gỡ liên kết để việc trở về backlog dự án.
        for (Issue issue : unfinishedIssues) {
            // Chuyển sang sprint mới hoặc trả backlog đều chưa có rank mới; không đụng thứ tự DONE lịch sử.
            issue.setBacklogPosition(null);
            sprintIssueRepository.deleteBySprint_SprintIdAndIssue_IssueId(sprintId, issue.getIssueId());
            if (nextSprint != null) {
                saveSprintIssue(nextSprint, issue);
            }
        }

        // Đóng sau cùng; transaction giữ khóa đến commit/rollback và giữ nguyên liên kết DONE.
        sprint.setState(SprintState.CLOSED);
        sprintRepository.save(sprint);
        // Event cuối phản ánh scope còn lại; đích ACTIVE nhận việc cũng được capture trong cùng transaction.
        metrics.captureClosed(sprint);
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("COMPLETE_SPRINT",EntityType.SPRINT,sprintId,sprint.getProject().getProjectId(),"Hoàn tất sprint; chuyển việc chưa xong đến sprint ID "+moveToSprintId);
        metrics.captureActiveProject(sprint.getProject().getProjectId());
    }

    /**
     * Dùng ở đầu thao tác sửa sprint/backlog; sprintId là khóa hàng sprints từ URL.
     * Kiểm tra MANAGE_SPRINT rồi dùng findByIdForUpdate khóa projects.project_id sở hữu sprint.
     * Sprint đọc trước lúc chờ khóa phải refresh để thấy trạng thái vừa commit của lượt trước.
     * Chỉ đọc/khóa; caller cùng transaction kiểm tra nghiệp vụ và ghi, khóa tự nhả khi kết thúc.
     */
    private Sprint getSprintForUpdate(int sprintId) {
        // Không giữ khóa khi thiếu quyền; dùng chung khóa project với luồng tạo issue và backlog dự án.
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sprint"));
        int projectId = sprint.getProject().getProjectId();
        requirePermission(projectId, PermissionName.MANAGE_SPRINT);
        projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dự án"));

        // Dưới READ COMMITTED, refresh bỏ bản đọc cũ trong persistence context sau khi chờ khóa project.
        entityManager.refresh(sprint);
        return sprint;
    }

    /** Kiểm tra quyền theo projects.project_id của dữ liệu đang đọc/sửa; thiếu quyền trả 403, không ghi DB. */
    private void requirePermission(int projectId, PermissionName permission) {
        // Dùng lại hasPermission để kế thừa quyền đọc của thành viên và quyền system admin trong ProjectSecurity.
        if (!projectSecurity.hasPermission(projectId, permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền thực hiện");
        }
    }

    /** So cặp ngày request hoặc hàng sprints khi đủ hai ngày; không ghi DB hay ép người gọi bổ sung ngày. */
    private void validateDates(LocalDateTime startDate, LocalDateTime endDate) {
        // Ngày bằng nhau hoặc kết thúc trước bắt đầu đều không tạo khoảng thời gian hợp lệ.
        if (startDate != null && endDate != null && !startDate.isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu phải trước ngày kết thúc");
        }
    }

    /** Dùng trước sửa backlog/nhận việc; chỉ FUTURE/ACTIVE được tiếp tục, CLOSED hoặc thiếu state phải dừng. */
    private void requireOpenSprint(Sprint sprint) {
        // Không sửa lịch sử CLOSED; dữ liệu thiếu state cũng không được coi là sprint mở.
        if (sprint.getState() != SprintState.FUTURE && sprint.getState() != SprintState.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể sửa backlog của sprint đã đóng hoặc chưa có trạng thái hợp lệ");
        }
    }

    /** So projects.project_id của issue và sprint; sai dự án trả 400 trước khi ghi liên kết. */
    private void requireSameProject(Sprint sprint, Issue issue) {
        // Ví dụ issue của project 20 không được thêm/gỡ/chuyển trong sprint project 10.
        if (issue.getProject() == null || issue.getProject().getProjectId() != sprint.getProject().getProjectId()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issue không thuộc cùng dự án với sprint");
        }
    }

    /**
     * Tra sprint_issues theo issues.issue_id và state FUTURE/ACTIVE, bỏ qua ID sprint hiện tại hoặc nguồn chuyển.
     * Caller giữ khóa project: lượt ghi sau chờ rồi mới kiểm tra, tránh cùng thêm vào hai sprint mở.
     * Có liên kết mở khác thì trả 409; CLOSED không cản, hàm chỉ đọc và không xóa lịch sử.
     */
    private void requireNoOtherOpenSprint(int issueId, int excludedSprintId) {
        // Chuyển từ 100 sang 101 bỏ qua nguồn 100 nhưng vẫn phát hiện liên kết ở sprint mở khác.
        if (sprintIssueRepository.existsByIssue_IssueIdAndSprint_StateInAndSprint_SprintIdNot(
                issueId, OPEN_STATES, excludedSprintId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Issue đã thuộc sprint FUTURE hoặc ACTIVE khác");
        }
    }

    /** Caller đã kiểm tra quyền, cùng dự án và chống trùng; ghi sprint_issues với khóa ghép và added_at mới. */
    private void saveSprintIssue(Sprint sprint, Issue issue) {
        // Dùng chung cho thêm và chuyển nội bộ, không gọi public API để kiểm tra/khóa lặp giữa transaction.
        SprintIssue sprintIssue = new SprintIssue();
        sprintIssue.setId(new SprintIssueId(sprint.getSprintId(), issue.getIssueId()));
        sprintIssue.setSprint(sprint);
        sprintIssue.setIssue(issue);
        sprintIssue.setAddedAt(LocalDateTime.now());
        sprintIssueRepository.save(sprintIssue);
    }
}
