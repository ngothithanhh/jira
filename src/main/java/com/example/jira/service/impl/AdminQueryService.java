package com.example.jira.service.impl;

import com.example.jira.dto.audit.*;
import com.example.jira.dto.audit.AdminData.*;
import com.example.jira.entity.AuditLog;
import com.example.jira.enums.SprintState;
import com.example.jira.repository.*;
import com.example.jira.security.GlobalSecurity;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminQueryService {
    private final AuditLogRepository audits;
    private final UserRepository users;
    private final UserRoleAssignmentRepository assignments;
    private final ProjectRepository projects;
    private final IssueRepository issues;
    private final SprintRepository sprints;
    private final GlobalSecurity global;

    /** Chỉ SYSTEM_ADMIN đọc log mọi project; filter ID luôn kèm loại entity, page/count chạy cùng predicate tại DB. */
    public AuditPage audit(AuditSearchRequest request) {
        global.assertSystemAdmin(); validate(request);
        return queryAudit(request,request.getProjectId());
    }
    /** Chỉ ProjectAuditQueryService gọi sau kiểm UPDATE_PROJECT; URL quyết định scope, không nhận projectId thứ hai. */
    AuditPage projectAudit(int projectId,AuditSearchRequest request) {
        validate(request);
        if (request.getProjectId()!=null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Project đã được chọn bằng URL");
        return queryAudit(request,projectId);
    }
    /** Page và count dùng chung predicate; actor null vẫn được giữ, projectId là snapshot chứ không JOIN entity đã xóa. */
    private AuditPage queryAudit(AuditSearchRequest request,Integer projectId) {
        var page=audits.findAll(specification(request,projectId),PageRequest.of(request.getPage(),request.getSize(),Sort.by(Sort.Direction.DESC,"createdAt","auditId")));
        return new AuditPage(page.getContent().stream().map(log -> new AuditEntry(log.getAuditId(),log.getUser()==null?null:log.getUser().getUserId(),
                log.getUser()==null?null:log.getUser().getUserName(),log.getAction(),log.getEntityType(),log.getEntityId(),log.getDescription(),log.getCreatedAt(),log.getProjectId())).toList(),
                page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
    }
    /** Tổng quan chỉ có số lượng; kiểm admin trước các count, không trả entity hoặc credential. */
    public Overview overview() {
        global.assertSystemAdmin();
        return new Overview(users.count(),users.countByEnabledTrue(),assignments.countEnabledAdmins(),projects.count(),issues.count(),
                sprints.countByState(SprintState.ACTIVE),sprints.countByState(SprintState.CLOSED));
    }
    /** Dữ liệu lấy actor bằng LEFT FETCH giữ log hệ thống user=null; count query không fetch. */
    private Specification<AuditLog> specification(AuditSearchRequest request,Integer projectId) {
        return (root,query,cb) -> {
            if (query!=null && query.getResultType()!=Long.class && query.getResultType()!=long.class) root.fetch("user",JoinType.LEFT);
            List<Predicate> filters=new ArrayList<>();
            // Scope bắt buộc của URL được AND với mọi filter; actorId chỉ lọc, không thay danh tính đã xác thực.
            if (projectId!=null) filters.add(cb.equal(root.get("projectId"),projectId));
            if (Boolean.TRUE.equals(request.getSystemOnly())) filters.add(cb.isNull(root.get("user")));
            if (request.getEntityType()!=null) filters.add(cb.equal(root.get("entityType"),request.getEntityType()));
            if (request.getEntityId()!=null) filters.add(cb.equal(root.get("entityId"),request.getEntityId()));
            if (request.getActorId()!=null) filters.add(cb.equal(root.get("user").get("userId"),request.getActorId()));
            if (request.getAction()!=null && !request.getAction().isBlank()) filters.add(cb.equal(cb.upper(root.get("action")),request.getAction().trim().toUpperCase(Locale.ROOT)));
            if (request.getCreatedFrom()!=null) filters.add(cb.greaterThanOrEqualTo(root.get("createdAt"),request.getCreatedFrom()));
            if (request.getCreatedTo()!=null) filters.add(cb.lessThanOrEqualTo(root.get("createdAt"),request.getCreatedTo()));
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }
    /** Chặn filter mơ hồ, ngày đảo và offset vượt giới hạn trước query, không phụ thuộc riêng Bean Validation HTTP. */
    private void validate(AuditSearchRequest request) {
        if (request==null || request.getPage()<0 || request.getSize()<1 || request.getSize()>100 || (long)request.getPage()*request.getSize()>Integer.MAX_VALUE
                || (request.getProjectId()!=null && request.getProjectId()<=0) || (Boolean.TRUE.equals(request.getSystemOnly()) && request.getActorId()!=null)
                || (request.getEntityId()!=null && (request.getEntityId()<=0 || request.getEntityType()==null)) || (request.getActorId()!=null && request.getActorId()<=0)
                || (request.getAction()!=null && request.getAction().length()>50) || (request.getCreatedFrom()!=null && request.getCreatedTo()!=null && request.getCreatedFrom().isAfter(request.getCreatedTo()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bộ lọc audit không hợp lệ");
        }
    }
}
