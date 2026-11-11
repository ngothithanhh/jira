package com.example.jira.repository;

import com.example.jira.entity.SprintIssue;
import com.example.jira.entity.SprintIssueId;
import com.example.jira.enums.SprintState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

@Repository
public interface SprintIssueRepository extends JpaRepository<SprintIssue, SprintIssueId> {
    List<SprintIssue> findBySprint_SprintId(int sprintId);
    void deleteBySprint_SprintIdAndIssue_IssueId(int sprintId, int issueId);

    // Tra issue ở sprint mở khác; bỏ qua sprint hiện tại/nguồn và lịch sử CLOSED.
    // Caller giữ khóa project trong transaction để hai lượt thêm/chuyển phải kiểm tra lần lượt.
    boolean existsByIssue_IssueIdAndSprint_StateInAndSprint_SprintIdNot(
            int issueId, Collection<SprintState> states, int excludedSprintId);
}
