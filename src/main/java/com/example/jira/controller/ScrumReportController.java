package com.example.jira.controller;
import com.example.jira.dto.report.ScrumReport.*;
import com.example.jira.service.impl.ScrumReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor
public class ScrumReportController {
    private final ScrumReportService service;
    /** Tổng hiện tại và số liệu snapshot được phân biệt rõ trong response. */
    @GetMapping("/api/sprints/{sprintId}/reports/summary") public Summary summary(@PathVariable int sprintId) { return service.summary(sprintId); }
    /** Chuỗi event để FE vẽ burndown dạng step chart, không giả lập các ngày chưa được quan sát. */
    @GetMapping("/api/sprints/{sprintId}/reports/burndown") public Burndown burndown(@PathVariable int sprintId) { return service.burndown(sprintId); }
    /** Velocity dùng snapshot đóng sprint, không đổi theo lần sửa issue về sau. */
    @GetMapping("/api/projects/{projectId}/reports/velocity") public List<Velocity> velocity(@PathVariable int projectId,@RequestParam(defaultValue="10") int limit) { return service.velocity(projectId,limit); }
}
