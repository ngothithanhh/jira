package com.example.jira.service.impl;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.example.jira.service.*;
import com.example.jira.security.*;
import com.example.jira.enums.EntityType;
import com.example.jira.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional
public class RolePermissionServiceImpl implements RolePermissionService {
    private final AdminRoleLock adminLock;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditLogService audit;

    /**
     * Admin lưu toàn bộ grant của roleId từ user_roles. expected là tập lúc mở form, desired là các permissions ID muốn giữ.
     * Khóa/recheck actor rồi target role; mọi kiểm tra hoàn tất trước delete/insert, audit cùng transaction để lỗi rollback cả set.
     */
    @Override public com.example.jira.dto.admin.RolePermissionData.RoleSnapshot replacePermissions(int roleId,
            com.example.jira.dto.admin.RolePermissionData.ReplaceRequest request){
        adminLock.begin();var role=adminLock.target(roleId);
        if(RolePolicy.system(role.getRoleName()))throw new ConflictException("SYSTEM_ADMIN luôn toàn quyền");
        // Chặn caller nội bộ giống API: không nhận null, ID âm, trùng hoặc tập không có giới hạn.
        if(request==null)throw invalidSet();
        var expected=checkedIds(request.expectedPermissionIds());
        var desired=checkedIds(request.permissionIds());
        var current=new java.util.TreeSet<>(rolePermissionRepository.permissionIds(roleId));
        var catalog=permissionRepository.findAll();RolePolicy.assertUniquePermissions(catalog);
        if(!expected.equals(current))throw new ConflictException("Quyền đã được thay đổi; tải cấu hình mới trước khi lưu");
        var byId=catalog.stream().collect(java.util.stream.Collectors.toMap(Permission::getPermissionId,p->p));
        for(int id:desired){
            var permission=byId.get(id);
            if(permission==null)throw new NotFoundException("Không tìm thấy quyền ID "+id);
            RolePolicy.projectGrant(role.getRoleName(),permission.getPermissionName());
        }
        var removed=new java.util.TreeSet<>(current);removed.removeAll(desired);
        var added=new java.util.TreeSet<>(desired);added.removeAll(current);
        // Tập không đổi là no-op sau so expected/validate, không tạo lịch sử thay đổi giả.
        if(removed.isEmpty()&&added.isEmpty())
            return new com.example.jira.dto.admin.RolePermissionData.RoleSnapshot(roleId,role.getRoleName(),java.util.List.copyOf(current));
        if(!removed.isEmpty())rolePermissionRepository.deleteSelected(roleId,removed);
        var insert=new java.util.ArrayList<RolePermission>();
        for(int id:added){
            var grant=new RolePermission();grant.setId(new RolePermissionId(roleId,id));grant.setRole(role);grant.setPermission(byId.get(id));insert.add(grant);
        }
        rolePermissionRepository.saveAllAndFlush(insert);
        audit.logAction("SET_ROLE_PERMISSIONS",EntityType.ROLE,roleId,"Thêm permission IDs "+added+"; gỡ IDs "+removed);
        return new com.example.jira.dto.admin.RolePermissionData.RoleSnapshot(roleId,role.getRoleName(),java.util.List.copyOf(desired));
    }

    /** ID từ request thuộc permissions; thứ tự không quan trọng nhưng mỗi ID chỉ xuất hiện một lần. */
    private java.util.SortedSet<Integer> checkedIds(java.util.List<Integer> ids){
        if(ids==null||ids.size()>1000||ids.stream().anyMatch(id->id==null||id<=0))throw invalidSet();
        var set=new java.util.TreeSet<>(ids);
        if(set.size()!=ids.size())throw invalidSet();
        return set;
    }
    /** Body sai trả400 trước ghi; không mượn409 để che ID/parser không hợp lệ. */
    private org.springframework.web.server.ResponseStatusException invalidSet(){
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Tập quyền phải gồm các ID nguyên dương không trùng");
    }

    /** API grant cũ: roleId thuộc user_roles, permissionId thuộc permissions; khóa/recheck actor và role trước kiểm phạm vi. */
    @Override public void addPermissionToRole(int roleId,int permissionId){
        adminLock.begin();var role=adminLock.target(roleId);
        var permission=permissionRepository.findById(permissionId).orElseThrow(()->new NotFoundException("Không tìm thấy quyền"));
        RolePolicy.projectGrant(role.getRoleName(),permission.getPermissionName());
        // Không tự chọn giữa các bản ghi permission alias; danh mục sai phải được kiểm tra trước cấp quyền.
        if(permissionRepository.findAll().stream().filter(p->RolePolicy.canonical(p.getPermissionName()).equals(permission.getPermissionName())).count()!=1)
            throw new ConflictException("Danh mục quyền có mã trùng");
        var id=new RolePermissionId(roleId,permissionId);
        if(rolePermissionRepository.existsById(id))throw new ConflictException("Quyền đã được gán");
        var grant=new RolePermission();grant.setId(id);grant.setRole(role);grant.setPermission(permission);
        rolePermissionRepository.saveAndFlush(grant);
        audit.logAction("GRANT_ROLE_PERMISSION",EntityType.ROLE,roleId,"Thêm quyền ID "+permissionId);
    }
    /** Gỡ đúng cặp kể cả grant legacy sai phạm vi; SYSTEM_ADMIN luôn toàn quyền nên không cho sửa cấu hình giả hiệu lực. */
    @Override public void removePermissionToRole(int roleId,int permissionId){
        adminLock.begin();var role=adminLock.target(roleId);
        if(RolePolicy.system(role.getRoleName()))throw new ConflictException("SYSTEM_ADMIN luôn toàn quyền");
        var grant=rolePermissionRepository.findByRole_RoleIdAndPermission_PermissionId(roleId,permissionId)
            .orElseThrow(()->new NotFoundException("Không tìm thấy quyền trong vai trò"));
        rolePermissionRepository.delete(grant);rolePermissionRepository.flush();
        audit.logAction("REVOKE_ROLE_PERMISSION",EntityType.ROLE,roleId,"Gỡ quyền ID "+permissionId);
    }
}
