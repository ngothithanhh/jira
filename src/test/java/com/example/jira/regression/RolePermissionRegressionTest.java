package com.example.jira.regression;

import com.example.jira.dto.admin.RolePermissionData.*;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import com.example.jira.service.*;
import com.example.jira.service.impl.*;
import com.example.jira.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RolePermissionRegressionTest {
    @Mock AdminRoleLock lock;
    @Mock PermissionRepository permissions;
    @Mock RolePermissionRepository grants;
    @Mock AuditLogService audit;
    @InjectMocks RolePermissionServiceImpl service;
    @BeforeEach void baseline(){
        lenient().when(lock.target(7)).thenReturn(Fixtures.role(7,"REVIEWER"));
        lenient().when(permissions.findAll()).thenReturn(List.of(permission(1,"EDIT_ISSUE"),permission(2,"CREATE_ISSUE"),permission(3,"DELETE_USER")));
        lenient().when(grants.permissionIds(7)).thenReturn(List.of(1));
    }
    /** expected cũ không được ghi hoặc audit một phần dù desired hợp lệ. */
    @Test void staleSnapshotStopsBeforeAnyWrite(){
        assertThatThrownBy(()->service.replacePermissions(7,new ReplaceRequest(List.of(),List.of(2)))).isInstanceOf(ConflictException.class);
        verify(grants,never()).deleteSelected(anyInt(),any());verify(grants,never()).saveAllAndFlush(any());verifyNoInteractions(audit);
    }
    /** Chỉ gỡ/thêm tập chênh lệch của target, trả snapshot đã sort và audit đúng role ID. */
    @Test void replaceWritesDifferenceAndAudit(){
        var result=service.replacePermissions(7,new ReplaceRequest(List.of(1),List.of(2)));
        assertThat(result.permissionIds()).containsExactly(2);
        verify(grants).deleteSelected(7,Set.of(1));
        verify(grants).saveAllAndFlush(argThat(rows->{var list=(List<RolePermission>)rows;return list.size()==1&&list.get(0).getId().equals(new RolePermissionId(7,2));}));
        verify(audit).logAction(eq("SET_ROLE_PERMISSIONS"),eq(com.example.jira.enums.EntityType.ROLE),eq(7),contains("2"));
    }
    /** No-op vẫn kiểm expected nhưng không phát sinh ghi/audit giả. */
    @Test void sameSetHasNoWritesOrAudit(){
        assertThat(service.replacePermissions(7,new ReplaceRequest(List.of(1),List.of(1))).permissionIds()).containsExactly(1);
        verify(grants,never()).saveAllAndFlush(any());verifyNoInteractions(audit);
    }
    /** Hết grant là tập hợp lệ; không tự nhét VIEW/default trở lại. */
    @Test void emptyDesiredSetIsValid(){
        assertThat(service.replacePermissions(7,new ReplaceRequest(List.of(1),List.of())).permissionIds()).isEmpty();
        verify(grants).deleteSelected(7,Set.of(1));
    }
    @Test void missingPermissionStopsBeforeDelete(){
        assertThatThrownBy(()->service.replacePermissions(7,new ReplaceRequest(List.of(1),List.of(999)))).isInstanceOf(NotFoundException.class);
        verify(grants,never()).deleteSelected(anyInt(),any());verifyNoInteractions(audit);
    }
    @Test void systemAdminIsNotEditable(){
        when(lock.target(7)).thenReturn(Fixtures.role(7,"SYSTEM_ADMIN"));
        assertThatThrownBy(()->service.replacePermissions(7,new ReplaceRequest(List.of(1),List.of()))).isInstanceOf(ConflictException.class);
        verifyNoInteractions(permissions,grants,audit);
    }
    @Test void globalPermissionCannotBeAssignedToProjectRole(){
        assertThatThrownBy(()->service.replacePermissions(7,new ReplaceRequest(List.of(1),List.of(3)))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(grants,never()).deleteSelected(anyInt(),any());
    }
    @Test void invalidBodiesAreRejectedBeforeReadingGrants(){
        for(var request:Arrays.asList(null,new ReplaceRequest(null,List.of()),new ReplaceRequest(List.of(1),null),
                new ReplaceRequest(List.of(1,1),List.of()),new ReplaceRequest(List.of(1),List.of(0)),
                new ReplaceRequest(List.of(1),Arrays.asList((Integer)null)),new ReplaceRequest(Collections.nCopies(1001,1),List.of())))
            assertThatThrownBy(()->service.replacePermissions(7,request)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(grants,permissions,audit);
    }
    @Test void duplicateCanonicalPermissionStopsConfiguration(){
        when(permissions.findAll()).thenReturn(List.of(permission(1,"EDIT_ISSUE"),permission(5," edit_issue ")));
        assertThatThrownBy(()->service.replacePermissions(7,new ReplaceRequest(List.of(1),List.of()))).isInstanceOf(ConflictException.class);
        verify(grants,never()).deleteSelected(anyInt(),any());
    }
    /** Lưu thiếu quyền admin dừng ở điểm khóa đầu, không tra target hoặc desired. */
    @Test void deniedActorStopsBeforeCatalog(){
        doThrow(new org.springframework.security.access.AccessDeniedException("forbidden")).when(lock).begin();
        assertThatThrownBy(()->service.replacePermissions(7,new ReplaceRequest(List.of(1),List.of()))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(grants,permissions,audit);verify(lock,never()).target(anyInt());
    }
    @Test void deserializerDoesNotCoerceFloatStringOrBooleanIds()throws Exception{
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String raw:List.of("{\"expectedPermissionIds\":[1.5],\"permissionIds\":[]}","{\"expectedPermissionIds\":[],\"permissionIds\":[\"1\"]}","{\"expectedPermissionIds\":[],\"permissionIds\":[true]}"))
            assertThatThrownBy(()->mapper.readValue(raw,ReplaceRequest.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThat(mapper.readValue("{\"expectedPermissionIds\":[1],\"permissionIds\":[]}",ReplaceRequest.class).expectedPermissionIds()).containsExactly(1);
    }
    private Permission permission(int id,String name){var p=new Permission();p.setPermissionId(id);p.setPermissionName(name);return p;}
}
