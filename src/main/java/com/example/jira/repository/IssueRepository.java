package com.example.jira.repository;

import com.example.jira.entity.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssueRepository extends JpaRepository<Issue,Integer> {
    
    @Query("SELECT MAX(i.issueNumber) FROM Issue i WHERE i.project.projectId = :projectId")
    Optional<Integer> findMaxIssueNumberByProjectId(@Param("projectId") int projectId);

    List<Issue> findByProject_ProjectIdAndDeletedAtIsNull(int projectId);
    Optional<Issue> findByIssueIdAndDeletedAtIsNull(int issueId);
    Page<Issue> findByProject_ProjectIdAndDeletedAtIsNullAndSummaryContainingIgnoreCase(
            int projectId, String summary, Pageable pageable);

    @Query("""
            SELECT i FROM Issue i
            WHERE i.project.projectId = :projectId
              AND i.deletedAt IS NULL
              AND (:q IS NULL OR lower(i.summary) LIKE lower(concat('%', :q, '%'))
                   OR lower(coalesce(i.description, '')) LIKE lower(concat('%', :q, '%'))
                   OR lower(i.issueKey) LIKE lower(concat('%', :q, '%')))
              AND (:statusId IS NULL OR i.status.statusId = :statusId)
              AND (:assigneeId IS NULL OR i.assignee.userId = :assigneeId)
              AND (:issueTypeId IS NULL OR i.issueType.issueTypeId = :issueTypeId)
            """)
    Page<Issue> searchActiveIssues(@Param("projectId") int projectId,
                                   @Param("q") String q,
                                   @Param("statusId") Integer statusId,
                                   @Param("assigneeId") Integer assigneeId,
                                   @Param("issueTypeId") Integer issueTypeId,
                                   Pageable pageable);

    @Query("""
            SELECT i FROM Issue i
            WHERE i.project.projectId = :projectId
              AND i.deletedAt IS NULL
              AND NOT EXISTS (
                SELECT si FROM SprintIssue si
                WHERE si.issue = i AND si.sprint.state IN :openStates
              )
            ORDER BY i.backlogRank ASC NULLS LAST, i.issueId ASC
            """)
    List<Issue> findProductBacklog(@Param("projectId") int projectId,
                                   @Param("openStates") java.util.Collection<com.example.jira.enums.SprintState> openStates);

    @Query("SELECT COALESCE(MAX(i.backlogRank), 0) FROM Issue i " +
           "WHERE i.project.projectId = :projectId AND i.deletedAt IS NULL")
    long findMaxBacklogRankByProjectId(@Param("projectId") int projectId);
    boolean existsByIssueType_IssueTypeId(int id);
    boolean existsByProject_ProjectIdAndIssueType_IssueTypeId(int projectId, int issueTypeId);
    boolean existsByStatus_StatusId(int statusId);
}
