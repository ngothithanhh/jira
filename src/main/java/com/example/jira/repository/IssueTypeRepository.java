package com.example.jira.repository;

import com.example.jira.entity.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueTypeRepository extends JpaRepository<IssueType, Integer> {
    boolean existsByTypeName(String name);
    boolean existsByTypeNameAndIssueTypeIdNot(String name,int id);
    // Bulk delete không chạy cascade JPA vào project_issue_types; FK DB chặn khi đang được gán đồng thời.
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from IssueType t where t.issueTypeId=:id")
    int deleteCatalogRow(@org.springframework.data.repository.query.Param("id") int id);
}
