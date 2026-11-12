package com.example.jira.repository;

import com.example.jira.enums.SprintState;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ProjectStatsRepository {
    private final EntityManager entityManager;
    public record Counts(long issues, long members, long sprints, String activeSprintName) {}

    /**
     * Dùng sau khi service lọc quyền project; ids là projects.project_id được phép trả cho người gọi.
     * Đọc count issues/project_members/sprints theo tập ID và tên sprint ACTIVE, không ghi DB.
     * Không có dòng group sau query thành công là số0; query lỗi được đẩy lên, không fallback số giả.
     */
    public Map<Integer, Counts> read(List<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        // Group tại DB để một trang nhiều dự án vẫn chỉ dùng bốn query, không tải toàn bộ issue/member.
        var issues = counts("select i.project.projectId, count(i) from Issue i where i.project.projectId in :ids group by i.project.projectId", ids);
        var members = counts("select m.project.projectId, count(m) from ProjectMember m where m.project.projectId in :ids group by m.project.projectId", ids);
        var sprints = counts("select s.project.projectId, count(s) from Sprint s where s.project.projectId in :ids group by s.project.projectId", ids);
        var active = entityManager.createQuery("select s.project.projectId, s.sprintName from Sprint s where s.project.projectId in :ids and s.state = :state order by s.sprintId", Object[].class)
                .setParameter("ids", ids).setParameter("state", SprintState.ACTIVE).getResultList();
        // Legacy nhiều ACTIVE vẫn hiện đủ tên, không chọn ngẫu nhiên một sprint rồi che sai khác dữ liệu.
        Map<Integer, String> names = new HashMap<>();
        for (var row : active) names.merge((Integer) row[0], row[1] == null ? "Sprint chưa đặt tên" : (String) row[1], (a, b) -> a + ", " + b);
        Map<Integer, Counts> result = new HashMap<>();
        for (int id : ids) result.put(id, new Counts(issues.getOrDefault(id, 0L), members.getOrDefault(id, 0L), sprints.getOrDefault(id, 0L), names.get(id)));
        return result;
    }

    /** Query là hằng nội bộ, ids là các project đã lọc quyền; chỉ map kết quả count, không nhận JPQL từ client. */
    private Map<Integer, Long> counts(String query, List<Integer> ids) {
        Map<Integer, Long> result = new HashMap<>();
        for (var row : entityManager.createQuery(query, Object[].class).setParameter("ids", ids).getResultList())
            result.put((Integer) row[0], (Long) row[1]);
        return result;
    }
}
