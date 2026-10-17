package com.example.jira.repository;

import com.example.jira.entity.SprintIssue;
import com.example.jira.entity.SprintIssueId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.example.jira.enums.SprintState;

@Repository
public interface SprintIssueRepository extends JpaRepository<SprintIssue, SprintIssueId> {
    List<SprintIssue> findBySprint_SprintId(int sprintId);
    List<SprintIssue> findBySprint_SprintIdOrderByIssue_BacklogRankAscIssue_IssueIdAsc(int sprintId);
    void deleteBySprint_SprintIdAndIssue_IssueId(int sprintId, int issueId);
    boolean existsBySprint_SprintIdAndIssue_IssueId(int sprintId, int issueId);
    @org.springframework.data.jpa.repository.Query("""
            SELECT CASE WHEN COUNT(si) > 0 THEN true ELSE false END
            FROM SprintIssue si
            WHERE si.issue.issueId = :issueId AND si.sprint.state IN :states
            """)
    boolean existsOpenSprintMembership(@org.springframework.data.repository.query.Param("issueId") int issueId,
                                       @org.springframework.data.repository.query.Param("states") java.util.Collection<SprintState> states);
}
