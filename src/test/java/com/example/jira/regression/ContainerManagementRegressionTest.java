package com.example.jira.regression;

import com.example.jira.dto.board.*;
import com.example.jira.dto.sprint.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.impl.ContainerManagementService;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ContainerManagementRegressionTest {
    @Mock ProjectRepository projects;
    @Mock SprintRepository sprints;
    @Mock SprintIssueRepository links;
    @Mock BoardRepository boards;
    @Mock BoardColumnRepository columns;
    @Mock IssueRepository issues;
    @Mock ProjectSecurity security;
    @Mock EntityManager entityManager;
    @Mock Query delete;
    @InjectMocks ContainerManagementService service;
    Sprint sprint; Board board; BoardColumn column;
    @BeforeEach void scope() {
        sprint = Fixtures.sprint(100,10,SprintState.FUTURE);
        board = new Board(); board.setBoardId(50); board.setProject(Fixtures.project(10)); board.setBoardName("Team");
        column = new BoardColumn(); column.setColumnId(60); column.setBoard(board); column.setColumnName("Doing"); column.setWipLimit(3);
        lenient().when(sprints.findById(100)).thenReturn(Optional.of(sprint)); lenient().when(boards.findById(50)).thenReturn(Optional.of(board));
        lenient().when(columns.findById(60)).thenReturn(Optional.of(column)); lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(board.getProject()));
    }
    @Test void missingContainersAreNotFound() {
        fails(() -> service.deleteSprint(999),HttpStatus.NOT_FOUND); fails(() -> service.deleteBoard(999),HttpStatus.NOT_FOUND); fails(() -> service.deleteColumn(999),HttpStatus.NOT_FOUND);
        verifyNoInteractions(security);
    }
    @Test void deniedWriteDoesNotLockOrSave() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(security).requirePermission(10,PermissionName.MANAGE_BOARD);
        var request = new UpdateBoardRequest(); request.setBoardName("New");
        assertThatThrownBy(() -> service.updateBoard(50,request)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(projects,never()).findByIdForUpdate(anyInt()); verify(boards,never()).save(any());
    }
    @Test void futureDateUpdateUsesExistingOtherDate() {
        var originalStart = sprint.getStartDate();
        sprint.setEndDate(LocalDateTime.of(2026,12,2,0,0));
        var request = new UpdateSprintRequest(); request.setStartDate(LocalDateTime.of(2026,12,3,0,0));
        fails(() -> service.updateSprint(100,request),HttpStatus.BAD_REQUEST); assertThat(sprint.getStartDate()).isEqualTo(originalStart); verify(sprints,never()).save(any());
    }
    @Test void activeSprintCanChangeGoalButNotDates() {
        sprint.setState(SprintState.ACTIVE); var request = new UpdateSprintRequest(); request.setGoal("Ship");
        assertThat(service.updateSprint(100,request).getGoal()).isEqualTo("Ship");
        request.setEndDate(LocalDateTime.of(2026,12,3,0,0)); fails(() -> service.updateSprint(100,request),HttpStatus.CONFLICT);
        verify(sprints,times(1)).save(sprint);
    }
    @Test void closedStateIsRefreshedBeforeUpdateOrDelete() {
        doAnswer(call -> { sprint.setState(SprintState.CLOSED); return null; }).when(entityManager).refresh(sprint);
        fails(() -> service.updateSprint(100,new UpdateSprintRequest()),HttpStatus.CONFLICT);
        fails(() -> service.deleteSprint(100),HttpStatus.CONFLICT); verifyNoInteractions(links); verify(sprints,never()).delete(any(Sprint.class));
    }
    @Test void futureDeletionResetsRankWithoutDeletingIssue() {
        bulk(); var issue = Fixtures.issue(5,10); issue.setBacklogPosition(7); var link = new SprintIssue(); link.setIssue(issue);
        when(links.findBySprint_SprintId(100)).thenReturn(List.of(link));
        service.deleteSprint(100); assertThat(issue.getBacklogPosition()).isNull(); verify(sprints).delete(sprint); verifyNoInteractions(issues);
    }
    @Test void foreignIssueBlocksSprintDeletionBeforeRankReset() {
        var issue = Fixtures.issue(5,20); issue.setBacklogPosition(7); var link = new SprintIssue(); link.setIssue(issue);
        when(links.findBySprint_SprintId(100)).thenReturn(List.of(link));
        fails(() -> service.deleteSprint(100),HttpStatus.CONFLICT); assertThat(issue.getBacklogPosition()).isEqualTo(7); verify(entityManager,never()).createQuery(anyString());
    }
    @Test void boardRenamePreservesProject() {
        var request = new UpdateBoardRequest(); request.setBoardName(" New ");
        service.updateBoard(50,request); assertThat(board.getBoardName()).isEqualTo("New"); assertThat(board.getProject().getProjectId()).isEqualTo(10);
    }
    @Test void boardDeletionOnlyTargetsItsMetadata() {
        bulk(); service.deleteBoard(50); verify(delete,times(3)).setParameter("id",50); verify(boards).delete(board); verifyNoInteractions(issues,sprints);
    }
    @Test void loweringWipDoesNotPartiallyRenameColumn() {
        when(issues.countInBoardColumn(10,60,0)).thenReturn(2L);
        var request = new UpdateColumnRequest(); request.setColumnName("New"); request.setWipLimit(1);
        fails(() -> service.updateColumn(60,request),HttpStatus.CONFLICT); assertThat(column.getColumnName()).isEqualTo("Doing"); assertThat(column.getWipLimit()).isEqualTo(3);
    }
    @Test void equalWipAndUnlimitedAreAccepted() {
        when(issues.countInBoardColumn(10,60,0)).thenReturn(2L);
        var request = new UpdateColumnRequest(); request.setWipLimit(2); service.updateColumn(60,request); assertThat(column.getWipLimit()).isEqualTo(2);
        request.setWipLimit(0); service.updateColumn(60,request); assertThat(column.getWipLimit()).isZero();
    }
    @Test void occupiedColumnCannotBeDeleted() {
        when(issues.countInBoardColumn(10,60,0)).thenReturn(1L); fails(() -> service.deleteColumn(60),HttpStatus.CONFLICT);
        verify(entityManager,never()).createQuery(anyString()); verify(columns,never()).delete(any());
    }
    @Test void emptyColumnDeletesMappingsAndOldRanks() { bulk(); service.deleteColumn(60); verify(delete,times(2)).setParameter("id",60); verify(columns).delete(column); }
    @Test void blankNamesAndNegativeWipStopBeforeQuery() {
        var boardRequest = new UpdateBoardRequest(); boardRequest.setBoardName(" ");
        fails(() -> service.updateBoard(50,boardRequest),HttpStatus.BAD_REQUEST);
        var columnRequest = new UpdateColumnRequest(); columnRequest.setWipLimit(-1);
        fails(() -> service.updateColumn(60,columnRequest),HttpStatus.BAD_REQUEST); verifyNoInteractions(boards,columns);
    }
    private void bulk() { when(entityManager.createQuery(anyString())).thenReturn(delete); when(delete.setParameter(eq("id"),anyInt())).thenReturn(delete); }
    private void fails(Runnable action,HttpStatus status) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,error -> assertThat(error.getStatusCode()).isEqualTo(status)); }
}
