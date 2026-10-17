package com.example.jira.repository;
import com.example.jira.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BoardIssueRepository extends JpaRepository<BoardIssue, BoardIssueId> {
 List<BoardIssue> findByBoard_BoardIdOrderByPositionAsc(int boardId);
 List<BoardIssue> findByIssue_IssueId(int issueId);
 boolean existsByIssue_IssueId(int issueId);
 void deleteByBoard_BoardId(int boardId);
}
