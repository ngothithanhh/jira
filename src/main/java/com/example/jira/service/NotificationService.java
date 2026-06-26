package com.example.jira.service;

import com.example.jira.dto.notification.NotificationResponse;
import com.example.jira.enums.NotificationType;

import java.util.List;

public interface NotificationService {
    void sendNotification(int userId, Integer issueId, Integer actorId, NotificationType type, String message);
    List<NotificationResponse> getNotificationsForCurrentUser();
    List<NotificationResponse> getUnreadNotificationsForCurrentUser();
    void markAsRead(int notificationId);
}
