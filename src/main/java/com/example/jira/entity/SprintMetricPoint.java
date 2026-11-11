package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="sprint_metric_points")
@Getter @Setter @NoArgsConstructor
public class SprintMetricPoint {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="point_id")
    private Long pointId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="metric_id",nullable=false)
    private SprintMetric metric;
    @Column(name="recorded_at",nullable=false) private LocalDateTime recordedAt;
    @Column(name="total_points",nullable=false) private long totalPoints;
    @Column(name="remaining_points",nullable=false) private long remainingPoints;
    @Column(name="issue_count",nullable=false) private long issueCount;
    @Column(name="remaining_count",nullable=false) private long remainingCount;
    @Column(name="invalid_estimate_count",nullable=false) private long invalidEstimateCount;
}
