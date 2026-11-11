package com.example.jira.service.impl;

import com.example.jira.dto.issue.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.mapper.IssueMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IssueQueryService {
    private final IssueRepository issues;
    private final ProjectRepository projects;
    private final SprintRepository sprints;
    private final ProjectSecurity security;
    private final EntityManager entityManager;
    private final AuditLogService audit;
    private final SprintMetricsService metrics;
    private static final Set<String> SORTS = Set.of("issueNumber","summary","priority","createdAt","updatedAt");

    /** Tra issues.issue_id từ URL, kiểm VIEW_PROJECT theo issue rồi map trong transaction đọc; không ghi DB. */
    @Transactional(readOnly = true)
    public IssueResponse detail(int issueId) {
        Issue issue = issue(issueId);
        security.requirePermission(issue.getProject().getProjectId(),PermissionName.VIEW_PROJECT);
        return IssueMapper.toResponse(issue);
    }

    /**
     * projectId là projects trên URL; filter ID thuộc status/type/users/sprints, không thay phạm vi project.
     * Kiểm tồn tại/quyền rồi AND filter tại DB; count dùng cùng điều kiện và không fetch quan hệ.
     * Chỉ đọc, trả page DTO ổn định; ID sprint khác project hoặc query không hợp lệ trả 400 trước query dữ liệu.
     */
    @Transactional(readOnly = true)
    public IssuePageResponse search(int projectId,IssueSearchRequest request) {
        validate(request);
        projects.findById(projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy dự án"));
        security.requirePermission(projectId,PermissionName.VIEW_PROJECT);
        if (request.getSprintId() != null) {
            Sprint sprint = sprints.findById(request.getSprintId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy sprint"));
            if (sprint.getProject().getProjectId() != projectId) throw error(HttpStatus.BAD_REQUEST,"Sprint không thuộc dự án");
        }
        // Tên field sort thuộc whitelist nên client không thể chọn property nội bộ hoặc tạo join ngoài dự kiến.
        Sort.Direction direction = Sort.Direction.valueOf(request.getDirection().toUpperCase(Locale.ROOT));
        Pageable page = PageRequest.of(request.getPage(),request.getSize(),Sort.by(direction,request.getSort()).and(Sort.by(direction,"issueId")));
        Page<Issue> result = issues.findAll(specification(projectId,request),page);
        return new IssuePageResponse(result.getContent().stream().map(IssueMapper::toResponse).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    /**
     * Xóa một issue sau DELETE_ISSUE, khóa project và refresh/so expectedVersion client đã tải.
     * Có con/attachment trả 409 để xử lý riêng; không xóa cây, không gọi Cloudinary trong transaction.
     * Xóa metadata phụ thuộc đúng issue, rồi issues và ghi audit cùng transaction; lỗi rollback tất cả.
     */
    @Transactional
    public void delete(int issueId,Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) throw error(HttpStatus.BAD_REQUEST,"Cần expectedVersion hợp lệ");
        Issue issue = issue(issueId); int projectId = issue.getProject().getProjectId();
        security.requirePermission(projectId,PermissionName.DELETE_ISSUE);
        projects.findByIdForUpdate(projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy dự án"));
        entityManager.refresh(issue);
        if (!Objects.equals(issue.getVersion(),expectedVersion)) throw error(HttpStatus.CONFLICT,"Issue đã thay đổi, vui lòng tải lại");
        if (issues.existsByParentIssue_IssueId(issueId)) throw error(HttpStatus.CONFLICT,"Cần xử lý issue con trước khi xóa");
        Long attachmentCount = entityManager.createQuery("select count(a) from IssueAttachment a where a.issue.issueId = :id",Long.class).setParameter("id",issueId).getSingleResult();
        if (attachmentCount > 0) throw error(HttpStatus.CONFLICT,"Cần xóa attachment riêng trước khi xóa issue");
        // Flush trước bulk delete; mỗi filter chỉ dùng ID issue đã kiểm quyền, không xóa dữ liệu chủ của quan hệ.
        entityManager.flush();
        for (String entity : List.of("Notification","IssueComment","IssueTransition","BoardIssue","SprintIssue","WorkLog","IssueVersion")) {
            entityManager.createQuery("delete from " + entity + " child where child.issue.issueId = :id").setParameter("id",issueId).executeUpdate();
        }
        String key = issue.getIssueKey(); issues.delete(issue); entityManager.flush();
        audit.logAction("DELETE",EntityType.ISSUE,issueId,"Xóa issue: " + key);
        // Gỡ issue khỏi ACTIVE thay đổi scope; ghi event cùng transaction delete/audit, không sửa snapshot CLOSED.
        metrics.captureActiveProject(projectId);
    }

    /** Dựng predicate dùng chung cho trang/count; chỉ fetch các quan hệ đơn trên query trả Issue. */
    private Specification<Issue> specification(int projectId,IssueSearchRequest request) {
        return (root,query,cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                for (String relation : List.of("project","status","issueType","reporter","assignee","parentIssue")) root.fetch(relation,JoinType.LEFT);
            }
            List<Predicate> filters = new ArrayList<>();
            filters.add(cb.equal(root.get("project").get("projectId"),projectId));
            if (request.getStatusId() != null) filters.add(cb.equal(root.get("status").get("statusId"),request.getStatusId()));
            if (request.getTypeId() != null) filters.add(cb.equal(root.get("issueType").get("issueTypeId"),request.getTypeId()));
            if (request.getAssigneeId() != null) filters.add(cb.equal(root.get("assignee").get("userId"),request.getAssigneeId()));
            if (request.getReporterId() != null) filters.add(cb.equal(root.get("reporter").get("userId"),request.getReporterId()));
            if (request.isUnassigned()) filters.add(cb.isNull(root.get("assignee")));
            if (request.getPriority() != null && !request.getPriority().isBlank()) filters.add(cb.equal(cb.upper(root.get("priority")),request.getPriority().trim().toUpperCase(Locale.ROOT)));
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                String pattern = "%" + request.getKeyword().trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
                filters.add(cb.or(cb.like(cb.lower(root.get("summary")),pattern,'\\'),cb.like(cb.lower(root.get("issueKey")),pattern,'\\'),cb.like(cb.lower(root.get("description")),pattern,'\\')));
            }
            if (request.getSprintId() != null && query != null) {
                Subquery<Integer> linked = query.subquery(Integer.class); Root<SprintIssue> link = linked.from(SprintIssue.class);
                linked.select(link.get("issue").get("issueId")).where(cb.equal(link.get("sprint").get("sprintId"),request.getSprintId()));
                filters.add(root.get("issueId").in(linked));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

    /** Chặn query sai trước DB, kể cả caller nội bộ không đi qua Bean Validation. */
    private void validate(IssueSearchRequest request) {
        if (request == null || request.getPage() < 0 || request.getSize() < 1 || request.getSize() > 100 || (long)request.getPage()*request.getSize()>Integer.MAX_VALUE
                || request.getSort() == null || !SORTS.contains(request.getSort()) || request.getDirection() == null
                || !Set.of("ASC","DESC").contains(request.getDirection().toUpperCase(Locale.ROOT))
                || (request.getKeyword() != null && request.getKeyword().length() > 200)
                || (request.getPriority() != null && request.getPriority().length() > 50)
                || (request.isUnassigned() && request.getAssigneeId() != null)) throw error(HttpStatus.BAD_REQUEST,"Bộ lọc/phân trang không hợp lệ");
        for (Integer id : Arrays.asList(request.getStatusId(),request.getTypeId(),request.getAssigneeId(),request.getReporterId(),request.getSprintId())) {
            if (id != null && id <= 0) throw error(HttpStatus.BAD_REQUEST,"ID bộ lọc phải dương");
        }
    }
    /** Không có issues.issue_id thì 404; caller tự kiểm quyền theo project của kết quả, chưa ghi DB. */
    private Issue issue(int id) { return issues.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy issue")); }
    /** Tạo lỗi có HTTP status, không ghi DB hoặc lộ nội dung SQL. */
    private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
