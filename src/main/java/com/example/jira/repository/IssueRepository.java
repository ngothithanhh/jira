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
public interface IssueRepository extends JpaRepository<Issue,Integer>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Issue> {
    boolean existsByParentIssue_IssueId(int issueId);
    // Card lấy status từ issues; LEFT JOIN giữ việc chưa giao/chưa có trạng thái hoặc cha.
    @Query("""
            select i from Issue i join fetch i.project left join fetch i.status left join fetch i.issueType
            left join fetch i.reporter left join fetch i.assignee left join fetch i.parentIssue
            where i.project.projectId = :projectId
            """)
    List<Issue> findBoardIssues(@Param("projectId") int projectId);

    // Đếm mọi issue hiện thuộc cột theo mapping, kể cả chưa có rank trong board_issues.
    @Query("""
            select count(i) from Issue i where i.project.projectId = :projectId and i.issueId <> :excludedId
            and i.status.statusId in (select m.issueStatus.statusId from BoardColumnStatus m where m.boardColumn.columnId = :columnId)
            """)
    long countInBoardColumn(@Param("projectId") int projectId, @Param("columnId") int columnId, @Param("excludedId") int excludedId);
    // Giữ issue thiếu status/assignee/parent; loại DONE và mọi liên kết sprint mở ngay trong truy vấn.
    @Query("""
            select i from Issue i join fetch i.project
            left join fetch i.status st left join fetch i.issueType
            left join fetch i.reporter left join fetch i.assignee left join fetch i.parentIssue
            where i.project.projectId = :projectId
              and (st.statusCategory is null or st.statusCategory <> com.example.jira.enums.StatusCategory.DONE)
              and not exists (select si from SprintIssue si where si.issue = i
                and si.sprint.state in (com.example.jira.enums.SprintState.FUTURE, com.example.jira.enums.SprintState.ACTIVE))
            """)
    List<Issue> findProductBacklog(@Param("projectId") int projectId);
    
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
