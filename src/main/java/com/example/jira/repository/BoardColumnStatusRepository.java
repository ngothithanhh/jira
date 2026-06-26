package com.example.jira.repository;

import com.example.jira.entity.BoardColumnStatus;
import com.example.jira.entity.BoardColumnStatusId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardColumnStatusRepository extends JpaRepository<BoardColumnStatus, BoardColumnStatusId> {
}
