package com.example.jira.controller;
import com.example.jira.service.ScrumReportService;
import com.example.jira.service.ScrumReportService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {
    private final ScrumReportService service;
    @GetMapping("/sprints/{id}/summary")
    public Summary summary(@PathVariable int id) {
        return service.summary(id);
    }
    @GetMapping("/sprints/{id}/burndown")
    public List<BurnPoint> burndown(@PathVariable int id) {
        return service.burndown(id);
    }
    @GetMapping("/projects/{projectId}/velocity")
    public List<Summary> velocity(@PathVariable int projectId) {
        return service.velocity(projectId);
    }
    @GetMapping("/projects/{projectId}/issues")
    public List<Row> issues(@PathVariable int projectId,
                            @RequestParam(required=false) Integer sprintId,
                            @RequestParam(required=false) Integer assigneeId,
                            @RequestParam(required=false) Integer statusId) {
        return service.issues(projectId, sprintId, assigneeId, statusId);
    }
    @GetMapping("/projects/{projectId}/issues.csv")
    public ResponseEntity<String> csv(@PathVariable int projectId,
                                      @RequestParam(required=false) Integer sprintId,
                                      @RequestParam(required=false) Integer assigneeId,
                                      @RequestParam(required=false) Integer statusId) {
        StringBuilder csv = new StringBuilder("\uFEFFissueId,issueKey,summary,assigneeId,statusId,storyPoints\r\n");
        for (Row row : service.issues(projectId, sprintId, assigneeId, statusId)) csv.append(row.issueId()).append(',').append(ScrumReportService.csvCell(row.issueKey())).append(',').append(ScrumReportService.csvCell(row.summary())).append(',').append(row.assigneeId() == null ? "" : row.assigneeId()).append(',').append(row.statusId() == null ? "" : row.statusId()).append(',').append(row.storyPoints()).append("\r\n");
        return ResponseEntity.ok().header("Content-Type", "text/csv; charset=UTF-8").header("Content-Disposition", "attachment; filename=project-" + projectId + "-issues.csv").body(csv.toString());
    }
}
