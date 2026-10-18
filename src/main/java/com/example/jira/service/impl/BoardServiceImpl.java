package com.example.jira.service.impl;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.dto.board.CreateBoardRequest;
import com.example.jira.dto.board.CreateColumnRequest;
import com.example.jira.dto.board.UpdateColumnOrderRequest;
import com.example.jira.entity.*;
import com.example.jira.mapper.BoardMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.enums.PermissionName;
import com.example.jira.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardIssueRepository boardIssueRepository;
    private final IssueRepository issueRepository;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final IssueTransitionRepository issueTransitionRepository;
    private final UserRepository userRepository;
    private final com.example.jira.service.AuditLogService auditLogService;
    private final BoardRepository boardRepository;
    private final ProjectRepository projectRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final ProjectSecurity projectSecurity;

    @Override
    @Transactional
    public BoardResponse createBoard(CreateBoardRequest request) {
        Project project = projectRepository.findById(request.getProjectId()).orElseThrow(() -> new RuntimeException("Không tìm thấy dự án"));
        if (!projectSecurity.hasPermission(project.getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }

        Board board = new Board();
        board.setProject(project);
        board.setBoardName(request.getBoardName());
        board.setBoardType(request.getBoardType());
        board.setCreatedAt(LocalDateTime.now());

        board = boardRepository.save(board);

        createDefaultColumn(board, "To Do", 1);
        createDefaultColumn(board, "In Progress", 2);
        createDefaultColumn(board, "Done", 3);

        return getBoard(board.getBoardId());
    }

    private void createDefaultColumn(Board board, String name, int position) {
        BoardColumn column = new BoardColumn();
        column.setBoard(board);
        column.setColumnName(name);
        column.setPosition(position);
        column.setWipLimit(0); 
        boardColumnRepository.save(column);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardResponse getBoard(int boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bảng"));
        if (!projectSecurity.hasPermission(board.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem bảng này");
        }

        List<BoardColumn> columns = boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(boardId);
        return response(board, columns);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoardResponse> getBoardsByProject(int projectId) {
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem bảng của dự án này");
        }
        return boardRepository.findByProject_ProjectId(projectId).stream()
                .map(board -> {
                    List<BoardColumn> columns = boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(board.getBoardId());
                    return response(board, columns);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BoardResponse addColumn(CreateColumnRequest request) {
        Board board = lockedBoard(request.getBoardId(), PermissionName.MANAGE_BOARD);
        if (!projectSecurity.hasPermission(board.getProject().getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }

        BoardColumn column = new BoardColumn();
        column.setBoard(board);
        column.setColumnName(request.getColumnName());
        column.setPosition(request.getPosition());
        column.setWipLimit(0);

        boardColumnRepository.save(column);

        return getBoard(board.getBoardId());
    }

    @Override
    @Transactional
    public void mapStatusToColumn(int columnId, int statusId) {
        BoardColumn column = boardColumnRepository.findById(columnId).orElseThrow(() -> new RuntimeException("Không tìm thấy cột"));
        if (!projectSecurity.hasPermission(column.getBoard().getProject().getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }
        
        lockedBoard(column.getBoard().getBoardId(), PermissionName.MANAGE_BOARD);
        IssueStatus status = issueStatusRepository.findById(statusId).orElseThrow(() -> new RuntimeException("Không tìm thấy trạng thái"));

        BoardColumnStatus bcs = new BoardColumnStatus();
        bcs.setId(new BoardColumnStatusId(columnId, statusId));
        bcs.setBoardColumn(column);
        bcs.setIssueStatus(status);

        boardColumnStatusRepository.save(bcs);
    }

    @Override
    @Transactional
    public void updateColumnOrder(int boardId, List<UpdateColumnOrderRequest> requests) {
        Board board = lockedBoard(boardId, PermissionName.MANAGE_BOARD);
        if (!projectSecurity.hasPermission(board.getProject().getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }

        if (requests == null || requests.stream().anyMatch(r -> r == null || r.getPosition() == null || r.getPosition() < 0)
                || requests.stream().map(UpdateColumnOrderRequest::getColumnId).distinct().count() != requests.size())
            throw new RuntimeException("Thứ tự cột không hợp lệ");
        for (UpdateColumnOrderRequest request : requests) {
            BoardColumn column = boardColumnRepository.findById(request.getColumnId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cột với ID: " + request.getColumnId()));
            
            // Kiểm tra cột có thuộc bảng này không
            if (column.getBoard().getBoardId() != boardId) {
                throw new RuntimeException("Cột không thuộc bảng này");
            }
            
            column.setPosition(request.getPosition());
            boardColumnRepository.save(column);
        }
    }

    @Override
    @Transactional
    public void unmapStatusFromColumn(int columnId, int statusId) {
        BoardColumn column = boardColumnRepository.findById(columnId).orElseThrow(() -> new RuntimeException("Không tìm thấy cột"));
        if (!projectSecurity.hasPermission(column.getBoard().getProject().getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }

        lockedBoard(column.getBoard().getBoardId(), PermissionName.MANAGE_BOARD);
        if (boardIssueRepository.findByBoard_BoardIdOrderByPositionAsc(column.getBoard().getBoardId()).stream()
                .anyMatch(bi -> bi.getColumn().getColumnId() == columnId && bi.getIssue().getStatus().getStatusId() == statusId))
            throw new RuntimeException("Liên kết trạng thái đang được sử dụng bởi công việc trên bảng");
        BoardColumnStatusId id = new BoardColumnStatusId(columnId, statusId);
        BoardColumnStatus bcs = boardColumnStatusRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trạng thái này chưa được ánh xạ vào cột"));

        boardColumnStatusRepository.delete(bcs);
    }

    private Board lockedBoard(int id, PermissionName permission) {
        Board board = boardRepository.findLockedById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy bảng công việc"));
        if (!projectSecurity.hasPermission(board.getProject().getProjectId(), permission)) throw new RuntimeException("Bạn không có quyền thực hiện thao tác này");
        return board;
    }
    private BoardResponse response(Board board, List<BoardColumn> columns) {
        BoardResponse result = BoardMapper.toResponse(board, columns);
        for (BoardIssue bi : boardIssueRepository.findByBoard_BoardIdOrderByPositionAsc(board.getBoardId())) {
            if (bi.getIssue().getDeletedAt() != null) continue;
            BoardResponse.BoardIssueDto item = new BoardResponse.BoardIssueDto();
            item.setPosition(bi.getPosition()); item.setIssue(com.example.jira.mapper.IssueMapper.toResponse(bi.getIssue()));
            result.getColumns().stream().filter(c -> c.getColumnId() == bi.getColumn().getColumnId()).findFirst().orElseThrow().getIssues().add(item);
        }
        return result;
    }
    @Override @Transactional
    public BoardResponse updateBoard(int id, com.example.jira.dto.board.UpdateBoardRequest request) {
        Board board = lockedBoard(id, PermissionName.MANAGE_BOARD);
        board.setBoardName(request.getBoardName()); board.setBoardType(request.getBoardType()); return getBoard(id);
    }
    @Override @Transactional
    public void deleteBoard(int id) {
        Board board = lockedBoard(id, PermissionName.MANAGE_BOARD);
        boardIssueRepository.deleteByBoard_BoardId(id);
        boardIssueRepository.flush();
        for (BoardColumn column : boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(id)) {
            if (column.getBoardColumnStatuses() != null) boardColumnStatusRepository.deleteAll(column.getBoardColumnStatuses());
            boardColumnRepository.delete(column);
        }
        boardRepository.delete(board);
    }
    @Override @Transactional
    public BoardResponse updateColumn(int id, com.example.jira.dto.board.UpdateColumnRequest request) {
        BoardColumn column = boardColumnRepository.findById(id).orElseThrow();
        int boardId = column.getBoard().getBoardId(); lockedBoard(boardId, PermissionName.MANAGE_BOARD);
        long count = boardIssueRepository.findByBoard_BoardIdOrderByPositionAsc(boardId).stream()
                .filter(b -> b.getColumn().getColumnId() == id && b.getIssue().getDeletedAt() == null).count();
        if (request.getWipLimit() < 0 || (request.getWipLimit() > 0 && count > request.getWipLimit())) throw new RuntimeException("Giới hạn công việc đang thực hiện nhỏ hơn số công việc hiện có");
        column.setColumnName(request.getColumnName()); column.setWipLimit(request.getWipLimit()); return getBoard(boardId);
    }
    @Override @Transactional
    public void deleteColumn(int id) {
        BoardColumn column = boardColumnRepository.findById(id).orElseThrow();
        lockedBoard(column.getBoard().getBoardId(), PermissionName.MANAGE_BOARD);
        if (boardIssueRepository.findByBoard_BoardIdOrderByPositionAsc(column.getBoard().getBoardId()).stream()
                .anyMatch(b -> b.getColumn().getColumnId() == id)) throw new RuntimeException("Vui lòng di chuyển công việc khỏi cột trước khi xóa");
        if (column.getBoardColumnStatuses() != null) boardColumnStatusRepository.deleteAll(column.getBoardColumnStatuses());
        boardColumnRepository.delete(column);
    }
    @Override @Transactional
    public BoardResponse moveIssue(int boardId, int issueId, com.example.jira.dto.board.MoveBoardIssueRequest request, boolean adding) {
        Issue issue = issueRepository.findLockedActiveById(issueId).orElseThrow(() -> new RuntimeException("Không tìm thấy công việc"));
        Board board = lockedBoard(boardId, PermissionName.EDIT_ISSUE);
        if (issue.getProject().getProjectId() != board.getProject().getProjectId()) throw new RuntimeException("Công việc thuộc dự án khác");
        BoardColumn target = boardColumnRepository.findById(request.getColumnId()).orElseThrow();
        if (target.getBoard().getBoardId() != boardId) throw new RuntimeException("Cột thuộc bảng công việc khác");
        if (!boardColumnStatusRepository.existsById(new BoardColumnStatusId(target.getColumnId(), request.getStatusId()))) throw new RuntimeException("Trạng thái chưa được liên kết với cột này");
        List<BoardIssue> all = boardIssueRepository.findByBoard_BoardIdOrderByPositionAsc(boardId);
        BoardIssue member = all.stream().filter(b -> b.getIssue().getIssueId() == issueId).findFirst().orElse(null);
        if (adding == (member != null)) throw new RuntimeException(adding ? "Công việc đã có trên bảng" : "Công việc không có trên bảng");
        IssueStatus previous = issue.getStatus();
        boolean changed = previous.getStatusId() != request.getStatusId();
        if (changed && !workflowRuleRepository.existsByProject_ProjectIdAndFromStatus_StatusIdAndToStatus_StatusId(board.getProject().getProjectId(), previous.getStatusId(), request.getStatusId())) throw new RuntimeException("Quy trình không cho phép chuyển sang trạng thái này");
        if (changed) for (BoardIssue other : boardIssueRepository.findByIssue_IssueId(issueId)) {
            if (other.getBoard().getBoardId() != boardId) throw new RuntimeException("Trạng thái xung đột với cột trên bảng công việc khác");
        }
        int oldColumn = member == null ? 0 : member.getColumn().getColumnId();
        int oldPosition = member == null ? -1 : member.getPosition();
        List<BoardIssue> ordered = all.stream().filter(b -> b.getColumn().getColumnId() == target.getColumnId() && b.getIssue().getIssueId() != issueId).collect(Collectors.toCollection(ArrayList::new));
        long occupancy = ordered.stream().filter(b -> b.getIssue().getDeletedAt() == null).count();
        if (request.getPosition() < 0 || request.getPosition() > ordered.size()) throw new RuntimeException("Vị trí không hợp lệ");
        if (oldColumn != target.getColumnId() && target.getWipLimit() > 0 && occupancy >= target.getWipLimit()) throw new RuntimeException("Đã đạt giới hạn công việc đang thực hiện của cột");
        if (member == null) {
            member = new BoardIssue(); member.setId(new BoardIssueId(boardId, issueId)); member.setBoard(board); member.setIssue(issue);
        }
        member.setColumn(target); ordered.add(request.getPosition(), member);
        for (int i=0; i<ordered.size(); i++) ordered.get(i).setPosition(i);
        boardIssueRepository.saveAll(ordered);
        if (oldColumn != 0 && oldColumn != target.getColumnId()) normalize(all, oldColumn, issueId);
        if (changed) {
            IssueStatus status = issueStatusRepository.findById(request.getStatusId()).orElseThrow();
            IssueTransition transition = new IssueTransition(); transition.setIssue(issue); transition.setFromStatus(previous); transition.setToStatus(status);
            transition.setUser(userRepository.findById(com.example.jira.ultils.SecurityUtils.getCurrentUserId()).orElseThrow());
            transition.setTransitionedAt(LocalDateTime.now()); issueTransitionRepository.save(transition); issue.setStatus(status);
        }
        issue.setUpdatedAt(LocalDateTime.now());
        auditLogService.logAction("BOARD_ISSUE_MOVED", com.example.jira.enums.EntityType.ISSUE, issueId,
                "board="+boardId+"; column="+oldColumn+"->"+target.getColumnId()+"; position="+oldPosition+"->"+request.getPosition()+"; status="+previous.getStatusId()+"->"+request.getStatusId());
        return getBoard(boardId);
    }
    private void normalize(List<BoardIssue> all, int columnId, int excludedId) {
        int position=0;
        for (BoardIssue b : all) if (b.getColumn().getColumnId()==columnId && b.getIssue().getIssueId()!=excludedId) b.setPosition(position++);
    }
    @Override @Transactional
    public void removeIssue(int boardId, int issueId) {
        issueRepository.findLockedById(issueId).orElseThrow(); lockedBoard(boardId, PermissionName.EDIT_ISSUE);
        BoardIssue member = boardIssueRepository.findById(new BoardIssueId(boardId, issueId)).orElseThrow();
        int columnId=member.getColumn().getColumnId(); boardIssueRepository.delete(member);
        normalize(boardIssueRepository.findByBoard_BoardIdOrderByPositionAsc(boardId), columnId, issueId);
        auditLogService.logAction("BOARD_ISSUE_REMOVED", com.example.jira.enums.EntityType.ISSUE, issueId, "board="+boardId);
    }
}
