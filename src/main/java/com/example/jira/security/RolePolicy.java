package com.example.jira.security;

import com.example.jira.enums.PermissionName;
import com.example.jira.exception.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

public final class RolePolicy {
    private RolePolicy() {}
    private static final Set<String> RESERVED=Set.of("SYSTEM_ADMIN","PROJECT_MANAGER","SCRUM_MASTER","DEVELOPER");
    private static final Set<String> GLOBAL_ONLY=Set.of("CREATE_PROJECT","DELETE_PROJECT","DELETE_USER","CREATE_ISSUE_TYPE");

    /** Chuẩn hóa mã nhập mới; không ghi ngược tên legacy khi chỉ đọc danh mục. */
    public static String canonical(String name) {return name==null?"":name.trim().toUpperCase(Locale.ROOT);}
    /** Bốn tên do chương trình seed được giữ ổn định để guard theo tên không bị thay đổi. */
    public static boolean reserved(String name) {return RESERVED.contains(canonical(name));}
    /** SYSTEM_ADMIN là role duy nhất được gán toàn cục; mọi tên còn lại dùng trong project_members. */
    public static boolean system(String name) {return "SYSTEM_ADMIN".equalsIgnoreCase(name);}
    /** Tên từ form/API phải là mã 1..50 ký tự; lỗi400 trước ghi DB, không nhận scope từ caller. */
    public static String roleName(String value) {
        String name=canonical(value);
        if(name.length()>50||!name.matches("[A-Z][A-Z0-9_]*"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên vai trò cần 1–50 ký tự A–Z, 0–9 hoặc _, bắt đầu bằng chữ");
        return name;
    }
    /** Mã DB chỉ có hành vi nếu khớp enum thật; alias khác hoa/thường không được tự coi là grant hợp lệ. */
    public static boolean supported(String name) {
        return Arrays.stream(PermissionName.values()).anyMatch(p->p.name().equals(name));
    }
    /** Chỉ cấp grant được xét theo role dự án; CREATE_PROJECT là khả năng chung, DELETE_PROJECT theo ownership, không cấp ở đây. */
    public static void projectGrant(String roleName,String permission) {
        if(system(roleName))throw new ConflictException("SYSTEM_ADMIN luôn toàn quyền; không sửa từng quyền của vai trò này");
        if(!supported(permission)||GLOBAL_ONLY.contains(permission))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã quyền không hợp lệ cho vai trò dự án");
    }
}
