package com.example.jira.regression;
import com.example.jira.dto.admin.*;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.service.impl.AdminUserQueryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AdminUserQueryRegressionTest {
    @Mock UserRepository users;@Mock UserRoleAssignmentRepository assignments;@Mock ProjectMemberRepository members;@Mock GlobalSecurity global;
    @InjectMocks AdminUserQueryService service;
    /** Batch chỉ ghép đúng ID user, role và count; không nhân hàng hoặc lấy quyền project làm global role. */
    @Test void searchMapsBatchRolesAndMemberships() {
        var request=new AdminUserSearch();request.setPage(1);request.setSize(2);
        when(users.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(Fixtures.user(2),Fixtures.user(3)),PageRequest.of(1,2),7));
        when(assignments.adminRolesForUsers(List.of(2,3))).thenReturn(List.of(new Object[]{2,9,"SYSTEM_ADMIN"},new Object[]{2,8,"DEVELOPER"}));
        when(members.adminMembershipCounts(List.of(2,3))).thenReturn(Collections.singletonList(new Object[]{3,4L}));
        var result=service.search(request);
        assertThat(result.totalItems()).isEqualTo(7);assertThat(result.page()).isEqualTo(1);assertThat(result.totalPages()).isEqualTo(4);
        assertThat(result.items().get(0).roles()).hasSize(2);assertThat(result.items().get(0).projectMembershipCount()).isZero();
        assertThat(result.items().get(1).roles()).isEmpty();assertThat(result.items().get(1).projectMembershipCount()).isEqualTo(4);
        verify(assignments).adminRolesForUsers(List.of(2,3));verify(members).adminMembershipCounts(List.of(2,3));
    }
    @Test void emptyPageDoesNotQueryAssociations(){
        when(users.findAll(any(Specification.class),any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.search(new AdminUserSearch()).items()).isEmpty();verifyNoInteractions(assignments,members);
    }
    @Test void nonAdminCannotReadAnyUsers(){
        doThrow(new org.springframework.security.access.AccessDeniedException("Forbidden")).when(global).assertSystemAdmin();
        assertThatThrownBy(()->service.search(new AdminUserSearch())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.get(2)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(users,assignments,members);
    }
    @Test void invalidOffsetAndKeywordStopBeforeDatabase(){
        var r=new AdminUserSearch();r.setPage(Integer.MAX_VALUE);
        assertThatThrownBy(()->service.search(r)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        r.setPage(0);r.setKeyword("x".repeat(101));
        assertThatThrownBy(()->service.search(r)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);verifyNoInteractions(users);
    }
    @Test void detailMissingIs404WithoutReadingRoles(){
        assertThatThrownBy(()->service.get(7)).isInstanceOf(com.example.jira.exception.NotFoundException.class);verifyNoInteractions(assignments,members);
    }
    @Test void detailUsesOnlyTargetId(){
        when(users.findById(2)).thenReturn(Optional.of(Fixtures.user(2)));
        var row=service.get(2);assertThat(row.userId()).isEqualTo(2);assertThat(row.email()).isEqualTo("user2@test.invalid");
        verify(assignments).adminRolesForUsers(List.of(2));verify(members).adminMembershipCounts(List.of(2));
    }
}
