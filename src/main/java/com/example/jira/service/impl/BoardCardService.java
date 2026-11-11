package com.example.jira.service.impl;

import com.example.jira.dto.board.*;
import com.example.jira.entity.*;
import com.example.jira.enums.PermissionName;
import com.example.jira.mapper.IssueMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.IssueService;
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
public class BoardCardService {
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final BoardColumnStatusRepository mappings;
    private final BoardIssueRepository ranks;
    private final IssueRepository issues;
    private final ProjectRepository projects;
    private final ProjectSecurity security;
    private final IssueService issueService;
    private final BoardWipPolicy wip;
    private final EntityManager entityManager;

    /**
     * Đọc card của toàn project sở hữu boardId sau VIEW_PROJECT; không ghi DB.
     * Cột từ status hiện tại; rank cũ chỉ dùng nếu cột đã lưu khớp mapping hiện tại.
     * Nhờ đó đổi status từ API issue cũng được hiển thị ở cột mới dù chưa kéo trên board.
     */
    @Transactional(readOnly = true)
    public List<BoardCardResponse> cards(int boardId) {
        Board board = board(boardId);
        security.requirePermission(board.getProject().getProjectId(),PermissionName.VIEW_PROJECT);
        Map<Integer,BoardColumn> byStatus = mapping(boardId);
        Map<Integer,BoardIssue> saved = rankMap(boardId);
        var current = new ArrayList<>(issues.findBoardIssues(board.getProject().getProjectId()));
        current.sort(Comparator.comparing((Issue issue) -> columnPosition(issue,byStatus),Comparator.nullsLast(Integer::compareTo))
                .thenComparing(issue -> savedPosition(issue,byStatus,saved),Comparator.nullsLast(Integer::compareTo))
                .thenComparingInt(Issue::getIssueNumber).thenComparingInt(Issue::getIssueId));
        Map<Integer,Integer> positions = new HashMap<>(); List<BoardCardResponse> response = new ArrayList<>();
        for (Issue issue : current) {
            var column = effectiveColumn(issue,byStatus); Integer columnId = column == null ? null : column.getColumnId();
            int position = positions.getOrDefault(columnId,0); positions.put(columnId,position+1);
            response.add(new BoardCardResponse(IssueMapper.toResponse(issue),columnId,position,issue.getVersion()));
        }
        return response;
    }

    /**
     * boardId/issueId là ID DB từ URL; request chứa cột, status đích, vị trí và version client đã tải.
     * Kiểm quyền/project rồi khóa project, refresh issue và so version trước thay đổi.
     * Dùng lại IssueService để kiểm workflow/lưu lịch sử/notification; tất cả cùng transaction với rank.
     * Mỗi move tăng version kể cả chỉ reorder; lượt thứ hai cùng version trả 409, không tự retry.
     */
    @Transactional
    public BoardCardResponse move(int boardId, int issueId, MoveBoardIssueRequest request) {
        validate(request);
        Board board = board(boardId); int projectId = board.getProject().getProjectId();
        security.requirePermission(projectId,PermissionName.EDIT_ISSUE);
        Issue issue = issues.findById(issueId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy issue"));
        if (issue.getProject() == null || issue.getProject().getProjectId() != projectId) throw error(HttpStatus.BAD_REQUEST,"Issue không thuộc project của board");
        projects.findByIdForUpdate(projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy dự án"));
        entityManager.refresh(issue);
        // Chặn snapshot cũ ngay sau khóa; chưa ghi status, lịch sử hoặc rank.
        if (!Objects.equals(issue.getVersion(),request.getExpectedVersion())) throw error(HttpStatus.CONFLICT,"Issue đã thay đổi, vui lòng tải lại board");
        BoardColumn target = columns.findById(request.getColumnId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy cột"));
        if (target.getBoard().getBoardId() != boardId) throw error(HttpStatus.BAD_REQUEST,"Cột không thuộc board");
        Map<Integer,BoardColumn> byStatus = mapping(boardId);
        if (!byStatus.containsKey(request.getStatusId()) || byStatus.get(request.getStatusId()).getColumnId() != target.getColumnId()) {
            throw error(HttpStatus.BAD_REQUEST,"Trạng thái chưa được map vào cột đích");
        }
        Map<Integer,BoardIssue> saved = rankMap(boardId);
        var destination = new ArrayList<>(issues.findBoardIssues(projectId).stream()
                .filter(candidate -> candidate.getIssueId() != issueId && effectiveColumn(candidate,byStatus) != null
                        && effectiveColumn(candidate,byStatus).getColumnId() == target.getColumnId())
                .sorted(Comparator.comparing((Issue candidate) -> savedPosition(candidate,byStatus,saved),Comparator.nullsLast(Integer::compareTo))
                        .thenComparingInt(Issue::getIssueNumber).thenComparingInt(Issue::getIssueId)).toList());
        if (request.getPosition() > destination.size()) throw error(HttpStatus.BAD_REQUEST,"Vị trí vượt kích thước cột đích");

        // Status thay đổi dùng lại workflow/transaction cũ; giữ status thì vẫn xét WIP trước reorder.
        if (issue.getStatus() == null || issue.getStatus().getStatusId() != request.getStatusId()) {
            issueService.changeIssueStatus(issueId,request.getStatusId());
        } else wip.validate(projectId,request.getStatusId(),issueId);
        destination.add(request.getPosition(),issue);
        List<BoardIssue> changed = new ArrayList<>();
        for (int position = 0; position < destination.size(); position++) {
            Issue card = destination.get(position); BoardIssue rank = saved.get(card.getIssueId());
            if (rank == null) { rank = new BoardIssue(); rank.setId(new BoardIssueId(boardId,card.getIssueId())); rank.setBoard(board); rank.setIssue(card); }
            rank.setColumn(target); rank.setPosition(position); changed.add(rank);
        }
        ranks.saveAll(changed);
        // Đảm bảo timestamp đổi ít nhất 1 microsecond để move chỉ đổi rank cũng làm Hibernate tăng version.
        LocalDateTime now = LocalDateTime.now();
        issue.setUpdatedAt(issue.getUpdatedAt() != null && !now.isAfter(issue.getUpdatedAt().plusNanos(1000)) ? issue.getUpdatedAt().plusNanos(1000) : now);
        issues.save(issue); entityManager.flush();
        return new BoardCardResponse(IssueMapper.toResponse(issue),target.getColumnId(),request.getPosition(),issue.getVersion());
    }

    /** Tra board từ URL, không nhận project từ client; thiếu trả 404 và không ghi DB. */
    private Board board(int id) { return boards.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy board")); }

    /** Một status chỉ có một cột trong cùng board; dữ liệu legacy trùng trả 409 thay vì chọn ngẫu nhiên. */
    private Map<Integer,BoardColumn> mapping(int boardId) {
        Map<Integer,BoardColumn> result = new HashMap<>();
        for (var mapping : mappings.findBoardMappings(boardId)) {
            BoardColumn previous = result.put(mapping.getIssueStatus().getStatusId(),mapping.getBoardColumn());
            if (previous != null && previous.getColumnId() != mapping.getBoardColumn().getColumnId()) throw error(HttpStatus.CONFLICT,"Một trạng thái đang map vào nhiều cột của board");
        }
        return result;
    }

    /** Đọc rank theo đúng board, không dùng vị trí đã lưu của board khác. */
    private Map<Integer,BoardIssue> rankMap(int boardId) {
        Map<Integer,BoardIssue> result = new HashMap<>();
        ranks.findBoardRanks(boardId).forEach(rank -> result.put(rank.getIssue().getIssueId(),rank)); return result;
    }

    /** Status null/chưa map trả null để hiển thị nhóm chưa phân cột, không mất issue. */
    private BoardColumn effectiveColumn(Issue issue, Map<Integer,BoardColumn> mapping) { return issue.getStatus() == null ? null : mapping.get(issue.getStatus().getStatusId()); }

    /** Cột chưa map xếp sau các cột có position; chỉ đọc để sort response. */
    private Integer columnPosition(Issue issue, Map<Integer,BoardColumn> mapping) {
        var column = effectiveColumn(issue,mapping); return column == null ? null : column.getPosition();
    }

    /** Rank của cột cũ không dùng sau khi status/mapping đổi; card đó xếp sau rank đúng cột hiện tại. */
    private Integer savedPosition(Issue issue, Map<Integer,BoardColumn> mapping, Map<Integer,BoardIssue> saved) {
        var column = effectiveColumn(issue,mapping); var rank = saved.get(issue.getIssueId());
        return column == null || rank == null || rank.getColumn() == null || rank.getColumn().getColumnId() != column.getColumnId() ? null : rank.getPosition();
    }

    /** Service cũng kiểm body để caller nội bộ không bỏ qua validation HTTP; chưa đọc/ghi DB. */
    private void validate(MoveBoardIssueRequest request) {
        if (request == null || request.getColumnId() == null || request.getColumnId() <= 0 || request.getStatusId() == null || request.getStatusId() <= 0
                || request.getPosition() == null || request.getPosition() < 0 || request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw error(HttpStatus.BAD_REQUEST,"Thiếu hoặc sai cột/trạng thái/vị trí/version");
        }
    }
    /** Tạo lỗi có status để advice chung trả JSON, không lộ SQL hoặc ghi DB. */
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status,message); }
}
