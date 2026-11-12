package com.example.jira.service.impl;
import com.example.jira.dto.admin.*;
import com.example.jira.dto.admin.AdminUserData.*;
import com.example.jira.dto.user_role.UserRoleResponse;
import com.example.jira.entity.User;
import com.example.jira.repository.*;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminUserQueryService {
    private final UserRepository users;
    private final UserRoleAssignmentRepository assignments;
    private final ProjectMemberRepository members;
    private final GlobalSecurity global;

    /** Admin tìm users theo tên/email/trạng thái. Phân trang trước khi đọc role/count; không ghi DB hoặc lộ hash. */
    public UserPage search(AdminUserSearch request) {
        global.assertSystemAdmin();
        // Kiểm cả caller nội bộ: offset lớn hoặc keyword quá dài dừng400 trước truy vấn.
        if(request==null||request.getPage()<0||request.getSize()<1||request.getSize()>100
            ||(long)request.getPage()*request.getSize()>Integer.MAX_VALUE
            ||request.getKeyword()!=null&&request.getKeyword().length()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bộ lọc người dùng không hợp lệ");
        var page=users.findAll(filter(request),PageRequest.of(request.getPage(),request.getSize(),Sort.by(Sort.Direction.DESC,"createdAt","userId")));
        return new UserPage(rows(page.getContent()),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
    }

    /** targetId là users.user_id được chọn từ bảng quản trị; kiểm admin trước tìm, thiếu404, chỉ trả DTO hiển thị. */
    public UserRow get(int targetId) {
        global.assertSystemAdmin();
        var user=users.findById(targetId).orElseThrow(()->new NotFoundException("Không tìm thấy người dùng"));
        return rows(List.of(user)).get(0);
    }

    /** Chỉ lấy role toàn cục và số project_members của user trong trang; hai query nhóm tránh nhân bản user/count. */
    private List<UserRow> rows(List<User> page) {
        if(page.isEmpty())return List.of();
        var ids=page.stream().map(User::getUserId).toList();
        Map<Integer,List<UserRoleResponse>> roles=new HashMap<>();
        for(var row:assignments.adminRolesForUsers(ids)) {
            roles.computeIfAbsent(((Number)row[0]).intValue(),ignored->new ArrayList<>())
                .add(UserRoleResponse.builder().role(((Number)row[1]).intValue()).roleName((String)row[2]).build());
        }
        Map<Integer,Long> counts=new HashMap<>();
        for(var row:members.adminMembershipCounts(ids))counts.put(((Number)row[0]).intValue(),((Number)row[1]).longValue());
        return page.stream().map(user->new UserRow(user.getUserId(),user.getUserName(),user.getFullName(),user.getEmail(),user.isEnabled(),
            user.getCreatedAt(),List.copyOf(roles.getOrDefault(user.getUserId(),List.of())),counts.getOrDefault(user.getUserId(),0L))).toList();
    }

    /** Filter dùng tham số JPA; %, _ và ký tự escape là nội dung literal, không làm mở rộng kết quả tìm kiếm. */
    private Specification<User> filter(AdminUserSearch request) {
        return (root,query,cb)->{
            var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(request.getEnabled()!=null)conditions.add(cb.equal(root.get("enabled"),request.getEnabled()));
            if(request.getKeyword()!=null&&!request.getKeyword().trim().isEmpty()){
                String term=request.getKeyword().trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
                conditions.add(cb.or(cb.like(cb.lower(root.get("userName")),"%"+term+"%",'\\'),
                    cb.like(cb.lower(root.get("fullName")),"%"+term+"%",'\\'),cb.like(cb.lower(root.get("email")),"%"+term+"%",'\\')));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
