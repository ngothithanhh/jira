package com.example.jira.repository;

import com.example.jira.entity.BoardColumnStatus;
import com.example.jira.entity.BoardColumnStatusId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardColumnStatusRepository extends JpaRepository<BoardColumnStatus, BoardColumnStatusId> {
    @org.springframework.data.jpa.repository.Query("select m from BoardColumnStatus m join fetch m.boardColumn c join fetch m.issueStatus where c.board.boardId = :boardId")
    java.util.List<BoardColumnStatus> findBoardMappings(@org.springframework.data.repository.query.Param("boardId") int boardId);

    @org.springframework.data.jpa.repository.Query("select m from BoardColumnStatus m join fetch m.boardColumn c where c.board.project.projectId = :projectId and m.issueStatus.statusId = :statusId")
    java.util.List<BoardColumnStatus> findStatusColumns(@org.springframework.data.repository.query.Param("projectId") int projectId,
            @org.springframework.data.repository.query.Param("statusId") int statusId);
}
