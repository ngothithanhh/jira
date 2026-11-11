package com.example.jira.repository;
import com.example.jira.entity.SprintMetricPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SprintMetricPointRepository extends JpaRepository<SprintMetricPoint,Long> {
    List<SprintMetricPoint> findByMetric_MetricIdOrderByRecordedAtAscPointIdAsc(Long metricId);
}
