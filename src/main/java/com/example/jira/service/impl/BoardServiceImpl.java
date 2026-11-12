package com.example.jira.service.impl;

import com.example.jira.enums.EntityType;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.dto.board.ColumnOrderToken;
import com.example.jira.dto.board.CreateBoardRequest;
import com.example.jira.dto.board.CreateColumnRequest;
import com.example.jira.dto.board.UpdateColumnOrderRequest;
import com.example.jira.entity.*;
import com.example.jira.mapper.BoardMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.enums.PermissionName;
import com.example.jira.service.BoardService;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import com.example.jira.ultils.SecurityUtils;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final com.example.jira.service.AuditLogService audit;
    private final BoardRepository boardRepository;
    private final ProjectRepository projectRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final ProjectSecurity projectSecurity;
    private final jakarta.persistence.EntityManager entityManager;

    /** request.projectId là ID projects sở hữu board mới. Chỉ MANAGE_BOARD của chính dự án mới được lưu boards và ba cột mặc định; thiếu quyền thì 403. */
    @Override
    @Transactional
    public BoardResponse createBoard(CreateBoardRequest request) {
        Project project = projectRepository.findById(request.getProjectId()).orElseThrow(() -> new NotFoundException("Không tìm thấy dự án"));
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(project.getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new AccessDeniedException("Không có quyền thực hiện");
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
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("CREATE_BOARD",EntityType.BOARD,board.getBoardId(),project.getProjectId(),"Tạo board và ba cột mặc định");

        // Quyền MANAGE_BOARD đã được kiểm trên project của board; tạo response trong cùng thao tác ghi.
        return getManagedBoardResponse(board.getBoardId());
    }

    private void createDefaultColumn(Board board, String name, int position) {
        BoardColumn column = new BoardColumn();
        column.setBoard(board);
        column.setColumnName(name);
        column.setPosition(position);
        column.setWipLimit(0); 
        boardColumnRepository.save(column);
    }

    /** boardId là ID boards trong path. Tra project sở hữu từ board rồi kiểm VIEW_PROJECT trước khi đọc cột và trả DTO; không ghi DB. */
    @Override
    @Transactional(readOnly = true)
    public BoardResponse getBoard(int boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bảng"));

        // boardId thuộc boards; lấy project sở hữu từ DB, không nhận projectId khác từ client.
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(board.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new AccessDeniedException("Bạn không có quyền xem bảng của dự án này");
        }

        List<BoardColumn> columns = boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(boardId);
        return BoardMapper.toResponse(board, columns);
    }

    /** projectId là ID projects trong path. Kiểm VIEW_PROJECT trước khi đọc boards/board_columns; thiếu quyền thì 403 và không trả dữ liệu. */
    @Override
    @Transactional(readOnly = true)
    public List<BoardResponse> getBoardsByProject(int projectId) {
        // Chặn trước khi truy vấn boards/cột để người ngoài không liệt kê được dữ liệu của projectId.
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new AccessDeniedException("Bạn không có quyền xem bảng của dự án này");
        }
        return boardRepository.findByProject_ProjectId(projectId).stream()
                .map(board -> {
                    List<BoardColumn> columns = boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(board.getBoardId());
                    return BoardMapper.toResponse(board, columns);
                })
                .collect(Collectors.toList());
    }

    /** request.boardId là ID boards, không phải projectId. Tra project từ board rồi kiểm MANAGE_BOARD; đủ quyền mới lưu board_columns và trả board. */
    @Override
    @Transactional
    public BoardResponse addColumn(CreateColumnRequest request) {
        Board board = lockManagedBoard(request.getBoardId());

        BoardColumn column = new BoardColumn();
        column.setBoard(board);
        column.setColumnName(request.getColumnName());
        column.setPosition(request.getPosition());
        column.setWipLimit(0);

        boardColumnRepository.save(column);
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("CREATE_COLUMN",EntityType.BOARD,board.getBoardId(),board.getProject().getProjectId(),"Thêm cột ID "+column.getColumnId());

        // Quyền MANAGE_BOARD đã được kiểm trên project của board; tạo response trong cùng thao tác ghi.
        return getManagedBoardResponse(board.getBoardId());
    }

    /** columnId là ID board_columns, statusId là ID issue_statuses. Kiểm MANAGE_BOARD theo cột → board → project trước khi lưu cặp board_column_statuses. */
    @Override
    @Transactional
    public void mapStatusToColumn(int columnId, int statusId) {
        BoardColumn column = boardColumnRepository.findById(columnId).orElseThrow(() -> new NotFoundException("Không tìm thấy cột"));
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(column.getBoard().getProject().getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new AccessDeniedException("Không có quyền thực hiện");
        }
        
        // Mapping thay đổi cách đếm WIP và hiển thị card; dùng khóa project chung trước sửa cấu hình.
        lockColumn(column);
        IssueStatus status = issueStatusRepository.findById(statusId).orElseThrow(() -> new NotFoundException("Không tìm thấy trạng thái"));
        if (boardColumnStatusRepository.findBoardMappings(column.getBoard().getBoardId()).stream().anyMatch(mapping ->
                mapping.getIssueStatus().getStatusId() == statusId && mapping.getBoardColumn().getColumnId() != columnId)) {
            throw new ConflictException("Trạng thái đã map vào cột khác của board");
        }

        // Cặp đã tồn tại giữ hành vi lưu cũ, nhưng không tạo log gán mới giả.
        boolean existed = boardColumnStatusRepository.existsById(new BoardColumnStatusId(columnId,statusId));
        BoardColumnStatus bcs = new BoardColumnStatus();
        bcs.setId(new BoardColumnStatusId(columnId, statusId));
        bcs.setBoardColumn(column);
        bcs.setIssueStatus(status);

        boardColumnStatusRepository.save(bcs);
        if (!existed) {
            // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
            audit.logProjectAction("MAP_COLUMN_STATUS",EntityType.BOARD,column.getBoard().getBoardId(),column.getBoard().getProject().getProjectId(),"Cột ID "+columnId+", trạng thái ID "+statusId);
        }
    }

    /** So snapshot dưới khóa project rồi cập nhật vị trí của đúng tập cột trong một transaction. */
    @Override
    @Transactional
    public void updateColumnOrder(int boardId, String expected, List<UpdateColumnOrderRequest> requests) {
        if (boardId <= 0 || !ColumnOrderToken.valid(expected) || requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu board, token hoặc danh sách thứ tự cột hợp lệ");
        }
        Map<Integer, Integer> positions = new HashMap<>();
        boolean[] usedPositions = new boolean[requests.size()];
        for (UpdateColumnOrderRequest request : requests) {
            if (request == null || request.getColumnId() == null || request.getColumnId() <= 0
                    || request.getPosition() == null || request.getPosition() < 0 || request.getPosition() >= requests.size()
                    || usedPositions[request.getPosition()] || positions.putIfAbsent(request.getColumnId(), request.getPosition()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID và vị trí cột phải duy nhất, vị trí liên tục từ 0");
            }
            usedPositions[request.getPosition()] = true;
        }

        Board board = lockManagedBoard(boardId);
        List<BoardColumn> columns = boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(boardId);
        if (!ColumnOrderToken.of(boardId, columns).equals(expected)) {
            throw new ConflictException("Thứ tự hoặc tập cột đã thay đổi. Hãy tải lại board");
        }
        if (columns.size() != positions.size() || columns.stream().anyMatch(column -> !positions.containsKey(column.getColumnId()))) {
            throw new ConflictException("Danh sách phải chứa đúng toàn bộ cột của board");
        }
        // Chỉ ghi audit khi vị trí khác snapshot đã kiểm; mọi lỗi bên dưới rollback cả thứ tự và lịch sử.
        boolean changed = false;
        for (BoardColumn column : columns) {
            int position = positions.get(column.getColumnId());
            changed |= position != column.getPosition();
            if (position != column.getPosition() && boardColumnRepository.updatePosition(boardId, column.getColumnId(), position) != 1) {
                throw new ConflictException("Cột không còn thuộc board");
            }
        }
        entityManager.flush();
        if (changed) audit.logProjectAction("ORDER_BOARD_COLUMNS",EntityType.BOARD,boardId,board.getProject().getProjectId(),"Sắp xếp cột board");
    }

    /** Thêm cột và reorder dùng cùng khóa với xóa cột/board; kiểm lại membership sau khi chờ. */
    private Board lockManagedBoard(int boardId) {
        Board board = boardRepository.findById(boardId).orElseThrow(() -> new NotFoundException("Không tìm thấy bảng"));
        int projectId = board.getProject().getProjectId();
        requireManageBoard(projectId);
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dự án"));
        entityManager.refresh(project);
        ProjectMember member = entityManager.find(ProjectMember.class, new ProjectMemberId(projectId, SecurityUtils.getCurrentUserId()));
        if (member != null) {
            try {
                entityManager.refresh(member);
            } catch (EntityNotFoundException removed) {
                throw new AccessDeniedException("Bạn không còn là thành viên dự án");
            }
        }
        requireManageBoard(projectId);
        entityManager.refresh(board);
        return board;
    }

    private void requireManageBoard(int projectId) {
        if (!projectSecurity.hasPermission(projectId, PermissionName.MANAGE_BOARD)) {
            throw new AccessDeniedException("Không có quyền thực hiện");
        }
    }

    /** columnId/statusId là cặp board_column_statuses. Kiểm MANAGE_BOARD theo project của cột trước khi xóa liên kết; giữ trạng thái gốc. */
    @Override
    @Transactional
    public void unmapStatusFromColumn(int columnId, int statusId) {
        BoardColumn column = boardColumnRepository.findById(columnId).orElseThrow(() -> new NotFoundException("Không tìm thấy cột"));
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(column.getBoard().getProject().getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new AccessDeniedException("Không có quyền thực hiện");
        }

        BoardColumnStatusId id = new BoardColumnStatusId(columnId, statusId);
        // Không thay mapping giữa lúc move đang kiểm WIP hoặc đổi trạng thái.
        lockColumn(column);
        BoardColumnStatus bcs = boardColumnStatusRepository.findById(id)
                .orElseThrow(() -> new ConflictException("Trạng thái này chưa được ánh xạ vào cột"));

        boardColumnStatusRepository.delete(bcs);
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("UNMAP_COLUMN_STATUS",EntityType.BOARD,column.getBoard().getBoardId(),column.getBoard().getProject().getProjectId(),"Gỡ trạng thái ID "+statusId+" khỏi cột ID "+columnId);
    }
    /**
     * Chỉ createBoard/addColumn gọi sau khi đã kiểm MANAGE_BOARD trên project sở hữu board.
     * boardId là ID boards vừa tạo hoặc vừa thêm cột; đọc lại board/cột để giữ response như luồng cũ.
     * Không phải entry point công khai và không cấp quyền xem cho request khác; không ghi DB.
     */
    private BoardResponse getManagedBoardResponse(int boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bảng"));
        List<BoardColumn> columns = boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(boardId);
        return BoardMapper.toResponse(board, columns);
    }

    /** Caller đã kiểm MANAGE_BOARD; khóa project sở hữu cột rồi refresh để cấu hình mới nhất dùng chung với move. */
    private void lockColumn(BoardColumn column) {
        projectRepository.findByIdForUpdate(column.getBoard().getProject().getProjectId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dự án"));
        entityManager.refresh(column);
    }
}
