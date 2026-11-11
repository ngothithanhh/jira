package com.example.jira.regression;

import com.example.jira.dto.board.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.IssueService;
import com.example.jira.service.impl.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class BoardCardRegressionTest {
    @Mock BoardRepository boards;
    @Mock BoardColumnRepository columns;
    @Mock BoardColumnStatusRepository mappings;
    @Mock BoardIssueRepository ranks;
    @Mock IssueRepository issues;
    @Mock ProjectRepository projects;
    @Mock ProjectSecurity security;
    @Mock IssueService issueService;
    @Mock BoardWipPolicy wip;
    @Mock EntityManager entityManager;
    @InjectMocks BoardCardService service;
    Board board; BoardColumn column; Issue issue;
    @BeforeEach void scope() {
        board = new Board(); board.setBoardId(50); board.setProject(Fixtures.project(10));
        column = new BoardColumn(); column.setColumnId(60); column.setBoard(board); column.setPosition(1);
        issue = Fixtures.issue(5,10); issue.setVersion(0L);
        // Nhánh 400/403/409 dừng trước một số truy vấn; case thành công dùng cùng fixture có khóa project.
        lenient().when(boards.findById(50)).thenReturn(Optional.of(board));
        lenient().when(issues.findById(5)).thenReturn(Optional.of(issue));
        lenient().when(columns.findById(60)).thenReturn(Optional.of(column));
        lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(board.getProject()));
    }
    @Test void missingBoardIsNotFound() { fails(() -> service.cards(99),HttpStatus.NOT_FOUND); verifyNoInteractions(issues,ranks); }
    @Test void outsiderCannotReadCards() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(security).requirePermission(10,PermissionName.VIEW_PROJECT);
        assertThatThrownBy(() -> service.cards(50)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(mappings,ranks);
    }
    @Test void unmappedIssueIsVisibleWithNullColumn() {
        when(issues.findBoardIssues(10)).thenReturn(List.of(issue));
        assertThat(service.cards(50)).singleElement().satisfies(card -> { assertThat(card.columnId()).isNull(); assertThat(card.version()).isZero(); });
    }
    @Test void currentStatusOverridesStaleColumnRank() {
        var old = new BoardColumn(); old.setColumnId(61); old.setBoard(board);
        var saved = new BoardIssue(); saved.setIssue(issue); saved.setColumn(old); saved.setPosition(99);
        when(ranks.findBoardRanks(50)).thenReturn(List.of(saved));
        when(mappings.findBoardMappings(50)).thenReturn(List.of(mapping(column,issue.getStatus())));
        when(issues.findBoardIssues(10)).thenReturn(List.of(issue));
        assertThat(service.cards(50)).singleElement().satisfies(card -> { assertThat(card.columnId()).isEqualTo(60); assertThat(card.position()).isZero(); });
    }
    @Test void duplicateMappingIsConflict() {
        var other = new BoardColumn(); other.setColumnId(61);
        when(mappings.findBoardMappings(50)).thenReturn(List.of(mapping(column,issue.getStatus()),mapping(other,issue.getStatus())));
        fails(() -> service.cards(50),HttpStatus.CONFLICT); verifyNoInteractions(ranks);
    }
    @Test void staleVersionStopsBeforeMappingOrWrite() {
        var body = request(); body.setExpectedVersion(8L);
        fails(() -> service.move(50,5,body),HttpStatus.CONFLICT);
        verifyNoInteractions(mappings,ranks,issueService);
        var sequence = inOrder(projects,entityManager); sequence.verify(projects).findByIdForUpdate(10); sequence.verify(entityManager).refresh(issue);
    }
    @Test void crossProjectIssueDoesNotLock() {
        issue.setProject(Fixtures.project(20)); fails(() -> service.move(50,5,request()),HttpStatus.BAD_REQUEST);
        verifyNoInteractions(projects,issueService,ranks);
    }
    @Test void foreignColumnIsRejected() {
        var foreign = new Board(); foreign.setBoardId(51); column.setBoard(foreign);
        fails(() -> service.move(50,5,request()),HttpStatus.BAD_REQUEST); verifyNoInteractions(issueService,ranks);
    }
    @Test void statusMustBeMappedToDestination() {
        fails(() -> service.move(50,5,request()),HttpStatus.BAD_REQUEST); verifyNoInteractions(issueService,ranks);
    }
    @Test void invalidPositionDoesNotChangeStatus() {
        var body = request(); body.setPosition(1);
        when(mappings.findBoardMappings(50)).thenReturn(List.of(mapping(column,issue.getStatus())));
        fails(() -> service.move(50,5,body),HttpStatus.BAD_REQUEST); verifyNoInteractions(issueService);
        verify(ranks,never()).saveAll(any());
    }
    @Test void sameStatusMoveChecksWipAndTouchesIssueVersion() {
        when(mappings.findBoardMappings(50)).thenReturn(List.of(mapping(column,issue.getStatus())));
        doAnswer(call -> { issue.setVersion(1L); return null; }).when(entityManager).flush();
        var result = service.move(50,5,request());
        assertThat(result.version()).isEqualTo(1); assertThat(issue.getUpdatedAt()).isNotNull();
        verify(wip).validate(10,1,5); verifyNoInteractions(issueService);
        verify(ranks).saveAll(argThat(values -> { var value = values.iterator().next(); return value.getPosition() == 0 && value.getId().equals(new BoardIssueId(50,5)); }));
    }
    @Test void changedStatusUsesExistingWorkflowService() {
        var to = Fixtures.status(2,"Doing",StatusCategory.IN_PROGRESS); var body = request(); body.setStatusId(2);
        when(mappings.findBoardMappings(50)).thenReturn(List.of(mapping(column,to)));
        service.move(50,5,body); verify(issueService).changeIssueStatus(5,2); verifyNoInteractions(wip);
    }
    @Test void deniedMoveDoesNotReadIssue() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(security).requirePermission(10,PermissionName.EDIT_ISSUE);
        assertThatThrownBy(() -> service.move(50,5,request())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(issues,projects,ranks);
    }
    @Test void invalidBodyDoesNotQueryDatabase() { fails(() -> service.move(50,5,new MoveBoardIssueRequest()),HttpStatus.BAD_REQUEST); verifyNoInteractions(boards,issues); }
    private MoveBoardIssueRequest request() { var body = new MoveBoardIssueRequest(); body.setColumnId(60); body.setStatusId(1); body.setExpectedVersion(0L); body.setPosition(0); return body; }
    private BoardColumnStatus mapping(BoardColumn column,IssueStatus status) { var m = new BoardColumnStatus(); m.setBoardColumn(column); m.setIssueStatus(status); return m; }
    private void fails(Runnable action,HttpStatus status) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,error -> assertThat(error.getStatusCode()).isEqualTo(status)); }
}
