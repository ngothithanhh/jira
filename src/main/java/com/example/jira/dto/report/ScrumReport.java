package com.example.jira.dto.report;
import com.example.jira.enums.SprintState;
import java.time.LocalDateTime;
import java.util.List;
public final class ScrumReport {
    private ScrumReport() { }
    public record Totals(long totalPoints,long remainingPoints,long issueCount,long remainingCount,long invalidEstimateCount) { }
    public record Summary(int sprintId,SprintState state,Totals current,Long committedPoints,Long committedCount,Long completedPoints,Long completedCount,boolean baselineAvailable) { }
    public record Point(LocalDateTime recordedAt,long totalPoints,long remainingPoints,long issueCount,long remainingCount,long invalidEstimateCount) { }
    public record Burndown(int sprintId,LocalDateTime plannedStart,LocalDateTime plannedEnd,LocalDateTime observedStart,LocalDateTime finishedAt,boolean baselineAvailable,List<Point> points) { }
    public record Velocity(int sprintId,String sprintName,Long committedPoints,Long completedPoints,Long completedCount,LocalDateTime finishedAt,boolean completionAvailable,boolean baselineAvailable) { }
}
