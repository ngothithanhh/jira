package com.example.jira.repository;

import com.example.jira.entity.Sprint;
import com.example.jira.enums.SprintState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Integer> {
    List<Sprint> findByProject_ProjectId(int projectId);

    // Sau khi khóa hàng projects, kiểm tra dự án còn sprint ACTIVE để chặn chạy song song.
    boolean existsByProject_ProjectIdAndState(int projectId, SprintState state);
}
