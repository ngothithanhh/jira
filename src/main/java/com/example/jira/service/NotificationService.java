package com.example.jira.service;

import com.example.jira.dto.notification.NotificationResponse;
import com.example.jira.enums.NotificationType;
import org.springframework.data.domain.Page;

import java.util.List;

public interface NotificationService {
    void sendNotification(int userId, Integer issueId, Integer actorId, NotificationType type, String message);
    List<NotificationResponse> getNotificationsForCurrentUser();
    List<NotificationResponse> getUnreadNotificationsForCurrentUser();
    Page<NotificationResponse> getPage(boolean unreadOnly, int page, int size);
    void markAllAsRead();
    void markAsRead(int notificationId);
}
