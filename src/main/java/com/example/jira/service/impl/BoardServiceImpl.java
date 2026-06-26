package com.example.jira.service.impl;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.dto.board.CreateBoardRequest;
import com.example.jira.dto.board.CreateColumnRequest;
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

        List<BoardColumn> columns = boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(boardId);
        return BoardMapper.toResponse(board, columns);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoardResponse> getBoardsByProject(int projectId) {
        return boardRepository.findByProject_ProjectId(projectId).stream()
                .map(board -> {
                    List<BoardColumn> columns = boardColumnRepository.findByBoard_BoardIdOrderByPositionAsc(board.getBoardId());
                    return BoardMapper.toResponse(board, columns);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BoardResponse addColumn(CreateColumnRequest request) {
        Board board = boardRepository.findById(request.getBoardId()).orElseThrow(() -> new RuntimeException("Không tìm thấy bảng"));
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
        
        IssueStatus status = issueStatusRepository.findById(statusId).orElseThrow(() -> new RuntimeException("Không tìm thấy trạng thái"));

        BoardColumnStatus bcs = new BoardColumnStatus();
        bcs.setId(new BoardColumnStatusId(columnId, statusId));
        bcs.setBoardColumn(column);
        bcs.setIssueStatus(status);

        boardColumnStatusRepository.save(bcs);
    }
}
