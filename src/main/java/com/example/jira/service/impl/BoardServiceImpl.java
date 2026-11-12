package com.example.jira.service.impl;

import com.example.jira.enums.EntityType;

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
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
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
        Board board = boardRepository.findById(request.getBoardId()).orElseThrow(() -> new NotFoundException("Không tìm thấy bảng"));
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(board.getProject().getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new AccessDeniedException("Không có quyền thực hiện");
        }

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

    /** boardId là ID boards; từng columnId phải thuộc board này. Kiểm MANAGE_BOARD của project sở hữu trước cập nhật position; thiếu quyền thì 403. */
    @Override
    @Transactional
    public void updateColumnOrder(int boardId, List<UpdateColumnOrderRequest> requests) {
        Board board = boardRepository.findById(boardId).orElseThrow(() -> new NotFoundException("Không tìm thấy bảng"));
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(board.getProject().getProjectId(), PermissionName.MANAGE_BOARD)) {
            throw new AccessDeniedException("Không có quyền thực hiện");
        }

        boolean changed = false;
        for (UpdateColumnOrderRequest request : requests) {
            BoardColumn column = boardColumnRepository.findById(request.getColumnId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy cột với ID: " + request.getColumnId()));
            
            // Kiểm tra cột có thuộc bảng này không
            if (column.getBoard().getBoardId() != boardId) {
                throw new ConflictException("Cột không thuộc bảng này");
            }
            
            changed |= !java.util.Objects.equals(column.getPosition(),request.getPosition());
            column.setPosition(request.getPosition());
            boardColumnRepository.save(column);
        }
        if (changed) {
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("ORDER_BOARD_COLUMNS",EntityType.BOARD,boardId,board.getProject().getProjectId(),"Sắp xếp cột board");
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
