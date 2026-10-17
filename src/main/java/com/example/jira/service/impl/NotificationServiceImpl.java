package com.example.jira.service.impl;

import com.example.jira.dto.notification.NotificationResponse;
import com.example.jira.entity.Issue;
import com.example.jira.entity.Notification;
import com.example.jira.entity.User;
import com.example.jira.enums.NotificationType;
import com.example.jira.mapper.NotificationMapper;
import com.example.jira.repository.IssueRepository;
import com.example.jira.repository.NotificationRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.service.NotificationService;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final IssueRepository issueRepository;

    @Override
    @Transactional
    public void sendNotification(int userId, Integer issueId, Integer actorId, NotificationType type, String message) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        Notification notification = new Notification();
        notification.setUser(user);
        
        if (issueId != null) {
            Issue issue = issueRepository.findById(issueId).orElse(null);
            notification.setIssue(issue);
        }
        if (actorId != null) {
            User actor = userRepository.findById(actorId).orElse(null);
            notification.setActor(actor);
        }
        
        notification.setNotificationType(type);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForCurrentUser() {
        int userId = SecurityUtils.getCurrentUserId();
        return notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotificationsForCurrentUser() {
        int userId = SecurityUtils.getCurrentUserId();
        return notificationRepository.findByUser_UserIdAndReadFalse(userId)
                .stream()
                .map(NotificationMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getPage(boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination (size 1..100)");
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "notificationId"));
        int userId = SecurityUtils.getCurrentUserId();
        return (unreadOnly ? notificationRepository.findByUser_UserIdAndReadFalse(userId, pageable)
                : notificationRepository.findByUser_UserId(userId, pageable)).map(NotificationMapper::toResponse);
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        notificationRepository.markAllRead(SecurityUtils.getCurrentUserId());
    }

    @Override
    @Transactional
    public void markAsRead(int notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo"));
        
        int userId = SecurityUtils.getCurrentUserId();
        if (notification.getUser().getUserId() != userId) {
            throw new RuntimeException("Không có quyền thao tác trên thông báo này");
        }
        
        notification.setRead(true);
        notificationRepository.save(notification);
    }
}
