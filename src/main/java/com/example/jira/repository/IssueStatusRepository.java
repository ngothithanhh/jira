package com.example.jira.repository;

import com.example.jira.entity.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IssueStatusRepository extends JpaRepository<IssueStatus, Integer> {
    boolean existsByStatusName(String statusName);

    /** FOR UPDATE tuần tự hóa cấu hình và xung đột với khóa FK của issue đang gắn status này. */
    @Query(value = "select * from issue_statuses where status_id = :id for update", nativeQuery = true)
    Optional<IssueStatus> findByIdForUpdate(@Param("id") int id);
}
