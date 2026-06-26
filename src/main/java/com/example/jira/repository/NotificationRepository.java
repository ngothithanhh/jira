package com.example.jira.repository;

import com.example.jira.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(int userId);
    List<Notification> findByUser_UserIdAndReadFalse(int userId);
}
