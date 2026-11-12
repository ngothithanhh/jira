package com.example.jira.repository;

import com.example.jira.entity.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BoardColumnRepository extends JpaRepository<BoardColumn, Integer> {
    List<BoardColumn> findByBoard_BoardIdOrderByPositionAsc(int boardId);

    @Modifying
    @Query("update BoardColumn c set c.position = :position where c.board.boardId = :boardId and c.columnId = :columnId")
    int updatePosition(@Param("boardId") int boardId, @Param("columnId") int columnId, @Param("position") int position);
}
