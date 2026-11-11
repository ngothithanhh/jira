package com.example.jira.repository;

import com.example.jira.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BoardIssueRepository extends JpaRepository<BoardIssue, BoardIssueId> {
    @Query("select bi from BoardIssue bi left join fetch bi.column join fetch bi.issue where bi.board.boardId = :boardId")
    List<BoardIssue> findBoardRanks(@Param("boardId") int boardId);
}
