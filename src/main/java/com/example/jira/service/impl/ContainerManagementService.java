package com.example.jira.service.impl;

import com.example.jira.dto.board.*;
import com.example.jira.dto.sprint.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.mapper.SprintMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ContainerManagementService {
    private final ProjectRepository projects;
    private final SprintRepository sprints;
    private final SprintIssueRepository links;
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final IssueRepository issues;
    private final ProjectSecurity security;
    private final EntityManager entityManager;

    /**
     * sprintId là sprints trên URL; lấy project để kiểm MANAGE_SPRINT, khóa/refresh trước xét state.
     * CLOSED bất biến; ACTIVE chỉ đổi tên/goal, FUTURE có thể sửa ngày nếu cặp ngày hợp lệ.
     * Ghi sprints, không đổi state/backlog; lỗi rollback, không tự retry hoặc sửa lịch sử.
     */
    @Transactional
    public SprintResponse updateSprint(int sprintId,UpdateSprintRequest request) {
        if (request == null) throw error(HttpStatus.BAD_REQUEST,"Thiếu nội dung cập nhật");
        if (request.getSprintName() != null) name(request.getSprintName());
        Sprint sprint = lockedSprint(sprintId);
        if (sprint.getState() != SprintState.FUTURE && sprint.getState() != SprintState.ACTIVE) throw error(HttpStatus.CONFLICT,"Không sửa sprint đã đóng");
        if (sprint.getState() == SprintState.ACTIVE && (request.getStartDate() != null || request.getEndDate() != null)) throw error(HttpStatus.CONFLICT,"Không đổi ngày của sprint đã bắt đầu");
        LocalDateTime start = request.getStartDate() == null ? sprint.getStartDate() : request.getStartDate();
        LocalDateTime end = request.getEndDate() == null ? sprint.getEndDate() : request.getEndDate();
        if (start != null && end != null && !start.isBefore(end)) throw error(HttpStatus.BAD_REQUEST,"Ngày bắt đầu phải trước ngày kết thúc");
        // Mọi điều kiện đã đạt; giữ null là không thay đổi, goal rỗng được dùng để xóa nội dung goal.
        if (request.getSprintName() != null) sprint.setSprintName(request.getSprintName().trim());
        if (request.getGoal() != null) sprint.setGoal(request.getGoal());
        sprint.setStartDate(start); sprint.setEndDate(end); sprints.save(sprint);
        return SprintMapper.toResponse(sprint);
    }

    /** Chỉ xóa FUTURE sau quyền/khóa; gỡ link và reset rank các issue cùng project, không xóa issue hay lịch sử CLOSED. */
    @Transactional
    public void deleteSprint(int sprintId) {
        Sprint sprint = lockedSprint(sprintId);
        if (sprint.getState() != SprintState.FUTURE) throw error(HttpStatus.CONFLICT,"Chỉ xóa sprint FUTURE");
        List<SprintIssue> current = links.findBySprint_SprintId(sprintId);
        if (current.stream().anyMatch(link -> link.getIssue().getProject() == null || link.getIssue().getProject().getProjectId() != sprint.getProject().getProjectId())) {
            throw error(HttpStatus.CONFLICT,"Sprint có issue không thuộc dự án");
        }
        // Kiểm toàn bộ scope trước ghi; bulk delete tự flush rank mới trong cùng transaction.
        current.forEach(link -> link.getIssue().setBacklogPosition(null));
        entityManager.flush();
        remove("delete from SprintIssue link where link.sprint.sprintId = :id",sprintId);
        // Bulk delete không loại entity đã đọc khỏi persistence context. Detach link để MapsId không persist lại sprint đã xóa ở flush sau.
        current.forEach(entityManager::detach);
        sprints.delete(sprint); entityManager.flush();
    }

    /** boardId là boards; MANAGE_BOARD/khóa project trước ghi tên, không đổi project/type/mapping. */
    @Transactional
    public void updateBoard(int boardId,UpdateBoardRequest request) {
        if (request == null) throw error(HttpStatus.BAD_REQUEST,"Thiếu tên board"); name(request.getBoardName());
        Board board = lockedBoard(boardId); board.setBoardName(request.getBoardName().trim()); boards.save(board);
    }

    /** Xóa metadata/rank đúng board dưới khóa project; giữ issue, status, sprint và project, lỗi rollback toàn bộ. */
    @Transactional
    public void deleteBoard(int boardId) {
        Board board = lockedBoard(boardId);
        // Xóa theo thứ tự FK: rank -> mapping -> cột -> board; mọi filter đều có boardId đã kiểm quyền.
        remove("delete from BoardIssue rank where rank.board.boardId = :id",boardId);
        remove("delete from BoardColumnStatus mapping where mapping.boardColumn.board.boardId = :id",boardId);
        remove("delete from BoardColumn column where column.board.boardId = :id",boardId);
        boards.delete(board); entityManager.flush();
    }

    /** Cột lấy project qua board; kiểm WIP mới bằng issues thật trước khi ghi tên/giới hạn, không đụng rank. */
    @Transactional
    public void updateColumn(int columnId,UpdateColumnRequest request) {
        if (request == null || (request.getWipLimit() != null && request.getWipLimit() < 0)) throw error(HttpStatus.BAD_REQUEST,"Giới hạn WIP không hợp lệ");
        if (request.getColumnName() != null) name(request.getColumnName());
        BoardColumn column = lockedColumn(columnId); int projectId = column.getBoard().getProject().getProjectId();
        // 0 không giới hạn; không được hạ WIP dương dưới số việc đang thuộc cột theo status mapping.
        if (request.getWipLimit() != null && request.getWipLimit() > 0 && issues.countInBoardColumn(projectId,columnId,0) > request.getWipLimit()) {
            throw error(HttpStatus.CONFLICT,"WIP mới nhỏ hơn số issue hiện có");
        }
        if (request.getColumnName() != null) column.setColumnName(request.getColumnName().trim());
        if (request.getWipLimit() != null) column.setWipLimit(request.getWipLimit());
        columns.save(column);
    }

    /** Cột có issue theo status thì 409; cột rỗng xóa rank cũ/mapping/cột trong một transaction, giữ status/issue. */
    @Transactional
    public void deleteColumn(int columnId) {
        BoardColumn column = lockedColumn(columnId);
        if (issues.countInBoardColumn(column.getBoard().getProject().getProjectId(),columnId,0) > 0) throw error(HttpStatus.CONFLICT,"Cần chuyển hết issue trước khi xóa cột");
        remove("delete from BoardIssue rank where rank.column.columnId = :id",columnId);
        remove("delete from BoardColumnStatus mapping where mapping.boardColumn.columnId = :id",columnId);
        columns.delete(column); entityManager.flush();
    }

    /** Tra sprint để lấy project, kiểm quyền trước khóa; refresh sau khóa để không dùng state cũ lúc chờ. */
    private Sprint lockedSprint(int id) {
        Sprint sprint = sprints.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy sprint"));
        lock(sprint.getProject().getProjectId(),PermissionName.MANAGE_SPRINT); entityManager.refresh(sprint); return sprint;
    }
    /** Tra board để kiểm MANAGE_BOARD đúng project, khóa rồi refresh trước sửa/xóa. */
    private Board lockedBoard(int id) {
        Board board = boards.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy board"));
        lock(board.getProject().getProjectId(),PermissionName.MANAGE_BOARD); entityManager.refresh(board); return board;
    }
    /** columnId là khóa board_columns; cột quyết định board/project kiểm quyền, không nhận project từ client. */
    private BoardColumn lockedColumn(int id) {
        BoardColumn column = columns.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy cột"));
        lock(column.getBoard().getProject().getProjectId(),PermissionName.MANAGE_BOARD); entityManager.refresh(column); return column;
    }
    /** Dùng chung khóa projects với move/status/backlog; thiếu quyền trả 403 trước lấy khóa, thiếu project trả 404. */
    private void lock(int projectId,PermissionName permission) {
        security.requirePermission(projectId,permission);
        projects.findByIdForUpdate(projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy dự án"));
    }
    /** Tên từ request không được blank/quá độ dài cột DB; chưa ghi dữ liệu. */
    private void name(String value) { if (value == null || value.isBlank() || value.length() > 100) throw error(HttpStatus.BAD_REQUEST,"Tên phải có 1..100 ký tự"); }
    /** JPQL là hằng trong service, ID đã kiểm quyền được bind; caller giữ transaction để lỗi rollback cả luồng. */
    private void remove(String jpql,int id) { entityManager.createQuery(jpql).setParameter("id",id).executeUpdate(); }
    /** Lỗi nghiệp vụ có status cụ thể cho advice chung; không lộ SQL hay tự retry. */
    private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
