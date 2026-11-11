package com.example.jira.repository;

import com.example.jira.entity.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssueRepository extends JpaRepository<Issue,Integer> {
    
    /**
     * Đọc số issue lớn nhất trong project để createIssue cấp số tiếp theo, không ghi DB.
     * Bên gọi phải khóa bản ghi projects tương ứng trong cùng transaction trước truy vấn này.
     * Khóa project vẫn bảo vệ được trường hợp chưa có issue; Optional rỗng được service coi là 0.
     */
    @Query("SELECT MAX(i.issueNumber) FROM Issue i WHERE i.project.projectId = :projectId")
    Optional<Integer> findMaxIssueNumberByProjectId(@Param("projectId") int projectId);

    List<Issue> findByProject_ProjectId(int projectId);
    boolean existsByIssueType_IssueTypeId(int id);
    boolean existsByProject_ProjectIdAndIssueType_IssueTypeId(int projectId, int issueTypeId);
    boolean existsByStatus_StatusId(int statusId);
}
