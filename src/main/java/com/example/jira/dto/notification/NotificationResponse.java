package com.example.jira.dto.notification;

import com.example.jira.enums.NotificationType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private int notificationId;
    private int userId;
    private Integer issueId;
    private NotificationType notificationType;
    private Integer actorId;
    private String actorName;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}
