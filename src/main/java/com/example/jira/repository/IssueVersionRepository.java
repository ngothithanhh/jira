package com.example.jira.repository;
import com.example.jira.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface IssueVersionRepository extends JpaRepository<IssueVersion, IssueVersionId> {
    List<IssueVersion> findByProjectVersion_VersionId(int versionId);
    void deleteByProjectVersion_VersionId(int versionId);
}
