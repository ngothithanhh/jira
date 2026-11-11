package com.example.jira.service.impl;
import com.example.jira.repository.NotificationRepository;
import com.example.jira.security.GlobalSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class NotificationInboxService {
    private final NotificationRepository notifications;
    private final GlobalSecurity identity;
    /** Chỉ đếm notification chưa đọc của users trong phiên, không nhận recipient ID từ client. */
    @Transactional(readOnly=true)
    public long unreadCount() { return notifications.countByUser_UserIdAndReadFalse(identity.currentUserId()); }
    /** Bulk update đúng user và read=false; gọi lại trả 0, không sửa inbox người khác hoặc issue liên quan. */
    @Transactional
    public int readAll() { return notifications.markAllRead(identity.currentUserId()); }
    /** ID notifications từ URL; thiếu 404, không phải người nhận 403, đủ quyền mới xóa metadata của chính thông báo. */
    @Transactional
    public void delete(int id) {
        int userId=identity.currentUserId();
        var notification=notifications.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo"));
        if (notification.getUser()==null || notification.getUser().getUserId()!=userId) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có quyền xóa thông báo này");
        notifications.delete(notification);
    }
}
