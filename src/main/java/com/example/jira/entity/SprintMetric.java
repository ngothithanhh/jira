package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="sprint_metrics")
@Getter @Setter @NoArgsConstructor
public class SprintMetric {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="metric_id")
    private Long metricId;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="sprint_id",nullable=false,unique=true)
    private Sprint sprint;
    @Column(nullable=false) private String origin;
    @Column(name="started_at",nullable=false) private LocalDateTime startedAt;
    @Column(name="finished_at") private LocalDateTime finishedAt;
    @Column(name="committed_points",nullable=false) private long committedPoints;
    @Column(name="committed_count",nullable=false) private long committedCount;
    @Column(name="completed_points") private Long completedPoints;
    @Column(name="completed_count") private Long completedCount;
    @Column(name="last_total",nullable=false) private long lastTotal;
    @Column(name="last_remaining",nullable=false) private long lastRemaining;
    @Column(name="last_count",nullable=false) private long lastCount;
    @Column(name="last_remaining_count",nullable=false) private long lastRemainingCount;
    @Column(name="last_invalid_count",nullable=false) private long lastInvalidCount;
}
