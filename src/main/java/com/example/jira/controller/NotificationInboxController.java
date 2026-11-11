package com.example.jira.controller;
import com.example.jira.service.impl.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationInboxController {
    private final NotificationInboxService service;
    /** Count chỉ của người đang đăng nhập, để client cập nhật badge inbox. */
    @GetMapping("/unread-count") public Map<String,Long> count() { return Map.of("count",service.unreadCount()); }
    /** Đánh dấu inbox hiện tại đã đọc, trả số row thay đổi, không nhận userId. */
    @PutMapping("/read-all") public Map<String,Integer> readAll() { return Map.of("updated",service.readAll()); }
    /** Xóa thông báo của chính người nhận, giữ issue/user. */
    @DeleteMapping("/{notificationId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int notificationId) { service.delete(notificationId); }
}
