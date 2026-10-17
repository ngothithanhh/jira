package com.example.jira.repository;

import com.example.jira.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    Page<Notification> findByUser_UserId(int userId, Pageable pageable);
    Page<Notification> findByUser_UserIdAndReadFalse(int userId, Pageable pageable);
    @Modifying
    @Query("update Notification n set n.read = true where n.user.userId = :userId and n.read = false")
    int markAllRead(int userId);
    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(int userId);
    List<Notification> findByUser_UserIdAndReadFalse(int userId);
}
