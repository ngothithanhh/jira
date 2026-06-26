package com.example.jira.repository;

import com.example.jira.entity.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BoardColumnRepository extends JpaRepository<BoardColumn, Integer> {
    List<BoardColumn> findByBoard_BoardIdOrderByPositionAsc(int boardId);
}
