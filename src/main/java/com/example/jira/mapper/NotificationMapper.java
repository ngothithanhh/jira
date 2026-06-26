package com.example.jira.mapper;

import com.example.jira.dto.notification.NotificationResponse;
import com.example.jira.entity.Notification;

public class NotificationMapper {

    public static NotificationResponse toResponse(Notification notification) {
        if (notification == null) return null;

        NotificationResponse res = new NotificationResponse();
        res.setNotificationId(notification.getNotificationId());
        res.setUserId(notification.getUser().getUserId());
        if (notification.getIssue() != null) {
            res.setIssueId(notification.getIssue().getIssueId());
        }
        res.setNotificationType(notification.getNotificationType());
        if (notification.getActor() != null) {
            res.setActorId(notification.getActor().getUserId());
            res.setActorName(notification.getActor().getFullName());
        }
        res.setMessage(notification.getMessage());
        res.setRead(notification.isRead());
        res.setCreatedAt(notification.getCreatedAt());

        return res;
    }
}
