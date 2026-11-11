package com.example.jira.repository;

import com.example.jira.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project,Integer> {
    boolean existsByProjectKey(String projectKey);

    /**
     * Khi tạo issue, khóa bản ghi projects theo ID project trong request trước khi đọc MAX(issue_number).
     * Khóa tồn tại đến hết transaction của createIssue, kể cả khi project chưa có issue nào.
     * Hai lượt tạo cùng project phải lần lượt đọc MAX và lưu issue; project khác có khóa riêng.
     * Không có project thì trả Optional rỗng để service báo 404, không ghi DB tại đây.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.projectId = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") int projectId);
}
