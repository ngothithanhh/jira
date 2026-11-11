package com.example.jira.repository;

import com.example.jira.entity.Sprint;
import com.example.jira.enums.SprintState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Integer> {
    List<Sprint> findByProject_ProjectIdAndState(int projectId, SprintState state);

    // Có snapshot thì dùng thời điểm đóng thật; legacy thiếu snapshot xếp sau và fallback theo ID.
    @org.springframework.data.jpa.repository.Query("""
            select s from Sprint s left join SprintMetric m on m.sprint = s
            where s.project.projectId = :projectId and s.state = com.example.jira.enums.SprintState.CLOSED
            order by m.finishedAt desc nulls last, s.sprintId desc
            """)
    List<Sprint> findClosedForVelocity(@org.springframework.data.repository.query.Param("projectId") int projectId,
            org.springframework.data.domain.Pageable pageable);
    List<Sprint> findByProject_ProjectId(int projectId);

    // Sau khi khóa hàng projects, kiểm tra dự án còn sprint ACTIVE để chặn chạy song song.
    boolean existsByProject_ProjectIdAndState(int projectId, SprintState state);
}
