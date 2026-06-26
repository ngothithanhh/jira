package com.example.jira.controller;

import com.example.jira.dto.notification.NotificationResponse;
import com.example.jira.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        
        List<NotificationResponse> responses;
        if (unreadOnly) {
            responses = notificationService.getUnreadNotificationsForCurrentUser();
        } else {
            responses = notificationService.getNotificationsForCurrentUser();
        }
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable int id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}
