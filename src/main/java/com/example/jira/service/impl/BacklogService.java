package com.example.jira.service.impl;

import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.mapper.IssueMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BacklogService {
    private final ProjectRepository projects;
    private final SprintRepository sprints;
    private final IssueRepository issues;
    private final SprintIssueRepository links;
    private final ProjectSecurity security;
    private final EntityManager entityManager;
    private static final List<String> PRIORITIES = List.of("HIGHEST", "HIGH", "MEDIUM", "LOW", "LOWEST");

    /** Đọc issues chưa DONE và chưa thuộc sprint mở của projectId sau VIEW_PROJECT; không ghi DB. */
    @Transactional(readOnly = true)
    public List<IssueResponse> product(int projectId) {
        // Kiểm sự tồn tại và quyền trước truy vấn nội dung; ID không tồn tại trả 404.
        requireProject(projectId);
        security.requirePermission(projectId, PermissionName.VIEW_PROJECT);
        return orderedIssues(issues.findProductBacklog(projectId), Issue::getBacklogPosition);
    }

    /**
     * Lưu toàn bộ thứ tự product backlog của projects.project_id từ URL.
     * Cần EDIT_ISSUE hoặc MANAGE_SPRINT; khóa project chung với membership/status trước đọc scope mới.
     * Request phải chứa đúng toàn bộ ID issues hiện có trong backlog; lệch trả 409 trước ghi.
     * Ghi issues.backlog_position 0..N-1 trong một transaction; lỗi rollback, không tự retry.
     */
    @Transactional
    public List<IssueResponse> orderProduct(int projectId, List<Integer> ids) {
        // Kiểm body trước quyền/khóa để không giữ tài nguyên cho dữ liệu không thể dùng.
        validateIds(ids);
        requireProject(projectId);
        if (!security.hasPermission(projectId, PermissionName.EDIT_ISSUE)
                && !security.hasPermission(projectId, PermissionName.MANAGE_SPRINT)) {
            throw error(HttpStatus.FORBIDDEN, "Không có quyền sắp xếp backlog");
        }
        lockProject(projectId);
        List<Issue> current = issues.findProductBacklog(projectId);
        validateScope(ids, current.stream().map(Issue::getIssueId).toList());

        // Mọi điều kiện đã đạt; cập nhật đúng các entity vừa đọc sau khóa, không ghi từng ID chưa kiểm scope.
        Map<Integer, Issue> byId = new HashMap<>();
        current.forEach(issue -> byId.put(issue.getIssueId(), issue));
        for (int position = 0; position < ids.size(); position++) byId.get(ids.get(position)).setBacklogPosition(position);
        issues.saveAll(current);
        return orderedIssues(current, Issue::getBacklogPosition);
    }

    /** Đọc mọi issue của sprint kể cả CLOSED, giữ lịch sử DONE; sai project trong dữ liệu cũ trả 409. */
    @Transactional(readOnly = true)
    public List<IssueResponse> sprint(int sprintId) {
        Sprint sprint = requireSprint(sprintId);
        security.requirePermission(sprint.getProject().getProjectId(), PermissionName.VIEW_PROJECT);
        List<SprintIssue> current = sprintLinks(sprint);
        return orderedSprint(current);
    }

    /**
     * sprintId là ID sprints; từng ids là issues.issue_id, không phải issue_number.
     * Sau MANAGE_SPRINT, khóa project rồi refresh sprint để thấy CLOSED vừa commit trong lúc chờ.
     * So toàn bộ scope trước ghi sprint_issues.position; không đổi added_at, không sửa lịch sử sprint khác.
     */
    @Transactional
    public List<IssueResponse> orderSprint(int sprintId, List<Integer> ids) {
        validateIds(ids);
        Sprint sprint = requireSprint(sprintId);
        int projectId = sprint.getProject().getProjectId();
        security.requirePermission(projectId, PermissionName.MANAGE_SPRINT);
        lockProject(projectId);
        entityManager.refresh(sprint);
        // CLOSED/thiếu state không còn là backlog có thể chỉnh sửa, dừng trước khi đọc/ghi liên kết.
        if (sprint.getState() != SprintState.FUTURE && sprint.getState() != SprintState.ACTIVE) {
            throw error(HttpStatus.CONFLICT, "Không thể sắp xếp sprint đã đóng");
        }
        List<SprintIssue> current = sprintLinks(sprint);
        validateScope(ids, current.stream().map(link -> link.getIssue().getIssueId()).toList());
        Map<Integer, SprintIssue> byId = new HashMap<>();
        current.forEach(link -> byId.put(link.getIssue().getIssueId(), link));
        for (int position = 0; position < ids.size(); position++) byId.get(ids.get(position)).setPosition(position);
        links.saveAll(current);
        return orderedSprint(current);
    }

    /** Lấy các cặp sprint/issue và từ chối dữ liệu chéo project trước khi mapper trả nội dung; không ghi DB. */
    private List<SprintIssue> sprintLinks(Sprint sprint) {
        List<SprintIssue> current = links.findBacklog(sprint.getSprintId());
        if (current.stream().anyMatch(link -> link.getIssue().getProject() == null
                || link.getIssue().getProject().getProjectId() != sprint.getProject().getProjectId())) {
            throw error(HttpStatus.CONFLICT, "Backlog có issue không thuộc dự án của sprint");
        }
        return current;
    }

    /** Body null/ID sai/trùng là 400; list rỗng vẫn được so với scope thật ở bước sau. */
    private void validateIds(List<Integer> ids) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) {
            throw error(HttpStatus.BAD_REQUEST, "Danh sách issue phải có ID dương và không trùng");
        }
    }

    /** Scope thay đổi hoặc gửi ID dự án khác thì 409 trước ghi; client cần tải lại toàn bộ backlog. */
    private void validateScope(List<Integer> requested, List<Integer> actual) {
        if (requested.size() != actual.size() || !new HashSet<>(requested).equals(new HashSet<>(actual))) {
            throw error(HttpStatus.CONFLICT, "Backlog đã thay đổi, vui lòng tải lại danh sách");
        }
    }

    /** Chỉ đọc projects để báo 404; không lấy khóa hoặc sửa project. */
    private void requireProject(int projectId) {
        projects.findById(projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy dự án"));
    }

    /** Khóa projects tới cuối transaction; dùng chung thứ tự khóa với sprint và issue mutation. */
    private void lockProject(int projectId) {
        projects.findByIdForUpdate(projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy dự án"));
    }

    /** Tra sprints.sprint_id để lấy đúng project kiểm quyền; thiếu thì 404, không ghi DB. */
    private Sprint requireSprint(int sprintId) {
        return sprints.findById(sprintId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy sprint"));
    }

    /** Rank đã lưu đứng trước; chỉ việc chưa có rank mới dùng priority, sau đó number và ID để ổn định. */
    private Comparator<Issue> order(Function<Issue, Integer> rank) {
        return Comparator.comparing(rank, Comparator.nullsLast(Integer::compareTo))
                .thenComparingInt(issue -> rank.apply(issue) == null ? priority(issue.getPriority()) : 0)
                .thenComparingInt(Issue::getIssueNumber).thenComparingInt(Issue::getIssueId);
    }

    /** Priority không xác định/null xếp sau LOWEST; không thay đổi chuỗi đã lưu trong issues. */
    private int priority(String value) {
        int index = value == null ? -1 : PRIORITIES.indexOf(value.toUpperCase(Locale.ROOT));
        return index < 0 ? PRIORITIES.size() : index;
    }

    /** Sắp xếp bản stream để trả DTO, không sửa collection/entity và không ghi DB. */
    private List<IssueResponse> orderedIssues(List<Issue> current, Function<Issue, Integer> rank) {
        return current.stream().sorted(order(rank)).map(IssueMapper::toResponse).toList();
    }

    /** Rank thuộc sprint_issues, không lấy backlog_position của product cho sprint. */
    private List<IssueResponse> orderedSprint(List<SprintIssue> current) {
        Map<Integer, Integer> ranks = new HashMap<>();
        current.forEach(link -> ranks.put(link.getIssue().getIssueId(), link.getPosition()));
        return orderedIssues(current.stream().map(SprintIssue::getIssue).toList(), issue -> ranks.get(issue.getIssueId()));
    }

    /** Tạo lỗi nghiệp vụ có HTTP status cụ thể để advice dùng chung trả JSON, không ghi DB. */
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
