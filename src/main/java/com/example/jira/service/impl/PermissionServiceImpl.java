package com.example.jira.service.impl;
import com.example.jira.dto.permission.*;
import com.example.jira.entity.Permission;
import com.example.jira.enums.PermissionName;
import com.example.jira.mapper.PermissionMapper;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import com.example.jira.service.PermissionService;
import com.example.jira.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class PermissionServiceImpl implements PermissionService {
    private final GlobalSecurity globalSecurity;
    private final ProjectSecurity projectSecurity;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final AdminRoleLock adminLock;

    /** Chỉ kiểm danh tính hiện tại và quyền ở đúng project; không mượn userId của người khác. */
    @Override @Transactional(readOnly=true)
    public boolean checkPermission(int userId,String permissionName,int projectId){
        if(userId!=globalSecurity.currentUserId())throw new AccessDeniedException("Không được kiểm quyền bằng danh tính người khác");
        return projectSecurity.hasPermission(projectId,PermissionName.valueOf(permissionName));
    }
    /** Danh mục giữ DTO cũ; mã canonical trùng phải được xử lý dữ liệu, không tự chọn một ID rồi tạo ma trận sai. */
    @Override @Transactional(readOnly=true)
    public List<PermissionResponse> getAllPermissions(){
        var rows=permissionRepository.findAll();
        Set<String> seen=new HashSet<>();
        for(var p:rows)if(!seen.add(RolePolicy.canonical(p.getPermissionName())))
            throw new ConflictException("Danh mục quyền có mã trùng; cần kiểm tra dữ liệu trước khi cấu hình");
        return rows.stream().map(PermissionMapper::toResponse).toList();
    }
    /** API legacy chỉ bổ sung mã enum còn thiếu; khóa admin chung chống hai lượt tạo trùng khi DB chưa unique tên. */
    @Override public PermissionResponse createPermission(PermissionRequest request){
        adminLock.begin();
        String name=name(request);
        if(!RolePolicy.supported(name))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chưa hỗ trợ mã quyền này");
        unique(name,0);
        var p=new Permission();p.setPermissionName(name);
        return PermissionMapper.toResponse(permissionRepository.saveAndFlush(p));
    }
    /** Không đổi mã chương trình sử dụng; chỉ cho đổi mã legacy chưa được gán, giữ nguyên permission_id. */
    @Override public PermissionResponse updatePermission(int id,PermissionRequest request){
        adminLock.begin();var p=permissionRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy quyền"));
        String name=name(request);
        if(RolePolicy.supported(RolePolicy.canonical(p.getPermissionName()))||RolePolicy.supported(name))
            throw new ConflictException("Không đổi mã quyền cố định của hệ thống");
        if(rolePermissionRepository.existsByPermission_PermissionId(id))throw new ConflictException("Quyền đang được sử dụng");
        unique(name,id);p.setPermissionName(name);
        return PermissionMapper.toResponse(permissionRepository.saveAndFlush(p));
    }
    /** Chỉ xóa mã legacy chưa dùng; mã enum cố định luôn409 để startup/guard không bị phá bởi API trực tiếp. */
    @Override public void deletePermission(int id){
        adminLock.begin();var p=permissionRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy quyền"));
        if(RolePolicy.supported(RolePolicy.canonical(p.getPermissionName())))throw new ConflictException("Không xóa mã quyền cố định của hệ thống");
        if(rolePermissionRepository.existsByPermission_PermissionId(id))throw new ConflictException("Quyền đang được sử dụng");
        permissionRepository.delete(p);permissionRepository.flush();
    }
    /** Tên từ request được chuẩn hóa và kiểm trước ghi; không sửa lại danh mục legacy khi chỉ đọc. */
    private String name(PermissionRequest request){
        String name=RolePolicy.canonical(request==null?null:request.getPermissionName());
        if(name.length()>255||!name.matches("[A-Z][A-Z0-9_]*"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã quyền không hợp lệ");
        return name;
    }
    /** So canonical toàn danh mục để chặn alias chữ hoa/thường/space, trừ chính ID đang đổi. */
    private void unique(String name,int excluded){
        if(permissionRepository.findAll().stream().anyMatch(p->p.getPermissionId()!=excluded&&RolePolicy.canonical(p.getPermissionName()).equals(name)))
            throw new ConflictException("Quyền đã tồn tại");
    }
}
