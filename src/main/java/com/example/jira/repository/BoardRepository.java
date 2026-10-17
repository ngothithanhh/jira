package com.example.jira.repository;

import com.example.jira.entity.Board;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoardRepository extends JpaRepository<Board, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Board b where b.boardId = :id")
    Optional<Board> findLockedById(@Param("id") int id);
    List<Board> findByProject_ProjectId(int projectId);
}
