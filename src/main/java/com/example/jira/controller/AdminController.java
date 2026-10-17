package com.example.jira.controller;

import com.example.jira.entity.*;
import com.example.jira.enums.EntityType;
import com.example.jira.repository.*;
import com.example.jira.service.AuditLogService;
import com.example.jira.ultils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@Transactional
public class AdminController {
    private final UserRepository users;
    private final AuditLogRepository logs;
    private final AuditLogService audit;
    public record UserView(int userId, String userName, String email, String fullName, boolean locked, LocalDateTime createdAt) {}
    public record UserUpdate(@NotBlank @Size(max=50) String userName,
                             @NotBlank @Email @Size(max=100) String email,
                             @Size(max=100) String fullName) {}
    public record LockRequest(boolean locked) {}
    public record AuditView(int auditId, Integer userId, String action,
                            EntityType entityType, int entityId, String description, LocalDateTime createdAt) {}
    private Pageable page(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page >= 0, size between 1 and 100");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
    }
    private User load(int id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private UserView view(User u) {
        return new UserView(u.getUserId(), u.getUserName(), u.getEmail(), u.getFullName(), u.isLocked(), u.getCreatedAt());
    }
    @GetMapping("/users")
    @Transactional(readOnly=true)
    public Page<UserView> users(@RequestParam(defaultValue="0") int page,
                                @RequestParam(defaultValue="20") int size) {
        return users.findAll(page(page,size,"userId")).map(this::view);
    }
    @GetMapping("/users/{id}")
    @Transactional(readOnly=true)
    public UserView user(@PathVariable int id) {
        return view(load(id));
    }
    @PutMapping("/users/{id}")
    public UserView update(@PathVariable int id, @Valid @RequestBody UserUpdate request) {
        User u = load(id);
        users.findByEmail(request.email()).filter(existing -> existing.getUserId() != id).ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists"); });
        u.setUserName(request.userName()); u.setEmail(request.email()); u.setFullName(request.fullName()); users.save(u);
        audit.logAction("USER_UPDATED", EntityType.USER, id, "Updated user profile"); return view(u);
    }
    @PutMapping("/users/{id}/lock")
    public UserView lock(@PathVariable int id, @RequestBody LockRequest request) {
        if (request.locked() && id == SecurityUtils.getCurrentUserId()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot lock your own account");
        User u = load(id); u.setLocked(request.locked()); users.save(u);
        audit.logAction(request.locked() ? "USER_LOCKED" : "USER_UNLOCKED", EntityType.USER, id, "Account lock changed"); return view(u);
    }
    @GetMapping("/audit-logs")
    @Transactional(readOnly=true)
    public Page<AuditView> audit(@RequestParam(required=false) Integer userId,
                                 @RequestParam(required=false) String action,
        @RequestParam(required=false) EntityType entityType,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        if (from != null && to != null && from.isAfter(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        Specification<AuditLog> filter = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (userId != null) predicates.add(cb.equal(root.get("user").get("userId"), userId));
            if (action != null) predicates.add(cb.equal(root.get("action"), action));
            if (entityType != null) predicates.add(cb.equal(root.get("entityType"), entityType));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable paging = page(page,size,"createdAt");
        return logs.findAll(filter, PageRequest.of(paging.getPageNumber(),paging.getPageSize(),paging.getSort().and(Sort.by(Sort.Direction.DESC,"auditId"))))
            .map(l -> new AuditView(l.getAuditId(), l.getUser() == null ? null : l.getUser().getUserId(), l.getAction(),l.getEntityType(),l.getEntityId(),l.getDescription(),l.getCreatedAt()));
    }
}
