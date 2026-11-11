package com.example.jira.regression;

import com.example.jira.dto.issue.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.*;
import com.example.jira.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollaborationRegressionTest {
    @BeforeEach void identifyCaller() { Fixtures.login(1); }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Nested @ExtendWith(MockitoExtension.class)
    class Comments {
        @Mock IssueCommentRepository comments;
        @Mock IssueRepository issues;
        @Mock UserRepository users;
        @Mock ProjectSecurity security;
        @Mock NotificationService notifications;
        @InjectMocks IssueCommentServiceImpl service;
        // Bình luận gắn đúng issue và người gọi, chỉ gửi một lần nếu reporter cũng là assignee.
        @Test void commentNotifiesUniqueRecipients() {
            var i = Fixtures.issue(5, 10); i.setAssignee(i.getReporter());
            when(issues.findById(5)).thenReturn(Optional.of(i));
            when(security.hasPermission(10, PermissionName.VIEW_PROJECT)).thenReturn(true);
            when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            when(comments.save(any())).thenAnswer(inv -> inv.getArgument(0));
            var r = new CreateCommentRequest(); r.setCommentText("Review please"); service.addComment(5, r);
            verify(comments).save(argThat(c -> c.getUser().getUserId() == 1 && c.getIssue() == i
                    && c.getCommentText().equals("Review please") && c.getCreatedAt() != null));
            verify(notifications).sendNotification(eq(3), eq(5), eq(1), eq(NotificationType.ISSUE_COMMENTED), anyString());
            verifyNoMoreInteractions(notifications);
        }
        // Người ngoài không được ghi bình luận hoặc gửi notification.
        @Test void deniedCommentDoesNotWrite() {
            when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
            assertThatThrownBy(() -> service.addComment(5, new CreateCommentRequest())).hasMessageContaining("quyền");
            verifyNoInteractions(comments, notifications);
        }
        // Không có issue không được tạo bình luận mồ côi.
        @Test void missingIssueStopsComment() {
            assertThatThrownBy(() -> service.addComment(5, new CreateCommentRequest())).hasMessageContaining("công việc");
            verifyNoInteractions(comments, notifications);
        }
        // Danh sách bình luận cần quyền xem trước khi truy vấn theo thời gian.
        @Test void readCommentsUsesIssueScope() {
            when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
            when(security.hasPermission(10, PermissionName.VIEW_PROJECT)).thenReturn(true);
            assertThat(service.getCommentsByIssue(5)).isEmpty(); verify(comments).findByIssue_IssueIdOrderByCreatedAtAsc(5);
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Attachments {
        @Mock IssueAttachmentRepository attachments;
        @Mock IssueRepository issues;
        @Mock UserRepository users;
        @Mock CloudinaryService cloud;
        @Mock ProjectSecurity security;
        @InjectMocks IssueAttachmentServiceImpl service;
        private final MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", new byte[]{1, 2});
        // Upload lưu URL do Cloudinary trả, không lưu nội dung file vào DB.
        @Test void uploadPersistsReturnedUrlAndOwner() throws Exception {
            when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
            when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
            when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            when(cloud.uploadFile(file)).thenReturn("https://example.invalid/file.txt");
            when(attachments.save(any())).thenAnswer(i -> i.getArgument(0));
            service.uploadAttachment(5, file);
            verify(attachments).save(argThat(a -> a.getUser().getUserId() == 1 && a.getIssue().getIssueId() == 5
                    && a.getFilePath().equals("https://example.invalid/file.txt") && a.getUploadedAt() != null));
        }
        // Thiếu quyền upload phải chặn trước mọi lời gọi Cloudinary.
        @Test void deniedUploadNeverCallsCloudinary() {
            when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
            assertThatThrownBy(() -> service.uploadAttachment(5, file)).hasMessageContaining("quyền");
            verifyNoInteractions(cloud, attachments);
        }
        // Cloudinary lỗi phải không lưu URL hoặc attachment giả.
        @Test void cloudFailureDoesNotPersistAttachment() throws Exception {
            when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
            when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
            when(cloud.uploadFile(file)).thenThrow(new IOException("network unavailable"));
            assertThatThrownBy(() -> service.uploadAttachment(5, file)).hasMessageContaining("upload");
            verifyNoInteractions(attachments);
        }
        // Người khác không có quyền EDIT_ISSUE không được xóa tệp của chủ sở hữu.
        @Test void outsiderCannotDeleteAnotherUsersFile() {
            var a = attachment(2); when(attachments.findById(8)).thenReturn(Optional.of(a));
            assertThatThrownBy(() -> service.deleteAttachment(8)).hasMessageContaining("quyền");
            verify(attachments, never()).delete(any()); verifyNoInteractions(cloud);
        }
        // Người upload xóa đúng URL trước khi xóa metadata.
        @Test void ownerDeletesRemoteFileThenMetadata() throws Exception {
            var a = attachment(1); when(attachments.findById(8)).thenReturn(Optional.of(a));
            service.deleteAttachment(8); var order = inOrder(cloud, attachments);
            order.verify(cloud).deleteFile(a.getFilePath()); order.verify(attachments).delete(a);
        }
        // Ghi nhận chính sách hiện có: xóa từ xa lỗi vẫn bỏ metadata; chưa có retry/bù tự động.
        @Test void remoteDeletionFailureStillRemovesMetadataUnderCurrentPolicy() throws Exception {
            var a = attachment(1); when(attachments.findById(8)).thenReturn(Optional.of(a));
            doThrow(new IOException("storage unavailable")).when(cloud).deleteFile(a.getFilePath());
            service.deleteAttachment(8); verify(attachments).delete(a);
        }
        // Đọc tệp chỉ lấy bản ghi thuộc issue đã được cấp quyền xem.
        @Test void listAttachmentsChecksProjectPermission() {
            when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
            when(security.hasPermission(10, PermissionName.VIEW_PROJECT)).thenReturn(true);
            assertThat(service.getAttachments(5)).isEmpty(); verify(attachments).findByIssue_IssueIdOrderByUploadedAtAsc(5);
        }
        private IssueAttachment attachment(int owner) {
            var a = new IssueAttachment(); a.setAttachmentId(8); a.setUser(Fixtures.user(owner));
            a.setIssue(Fixtures.issue(5, 10)); a.setFilePath("https://example.invalid/file.txt"); return a;
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Notifications {
        @Mock NotificationRepository repository;
        @Mock UserRepository users;
        @Mock IssueRepository issues;
        @InjectMocks NotificationServiceImpl service;
        // Người nhận không tồn tại thì không tạo notification mồ côi.
        @Test void missingRecipientDoesNotWrite() {
            service.sendNotification(99, 5, 1, NotificationType.ISSUE_ASSIGNED, "message");
            verify(repository, never()).save(any());
        }
        // Thông báo mới gắn người nhận, issue, actor và mặc định chưa đọc.
        @Test void notificationPersistsCorrectParticipants() {
            when(users.findById(2)).thenReturn(Optional.of(Fixtures.user(2)));
            when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
            service.sendNotification(2, 5, 1, NotificationType.ISSUE_ASSIGNED, "assigned");
            verify(repository).save(argThat(n -> n.getUser().getUserId() == 2 && n.getActor().getUserId() == 1
                    && n.getIssue().getIssueId() == 5 && !n.isRead() && n.getCreatedAt() != null));
        }
        // Danh sách luôn lọc theo user trong context, không nhận user ID từ client.
        @Test void listIsScopedToCurrentUser() {
            service.getNotificationsForCurrentUser(); service.getUnreadNotificationsForCurrentUser();
            verify(repository).findByUser_UserIdOrderByCreatedAtDesc(1);
            verify(repository).findByUser_UserIdAndReadFalse(1);
        }
        // Người khác không được đổi trạng thái đã đọc của thông báo.
        @Test void cannotReadAnotherUsersNotification() {
            var n = new Notification(); n.setUser(Fixtures.user(2));
            when(repository.findById(8)).thenReturn(Optional.of(n));
            assertThatThrownBy(() -> service.markAsRead(8)).hasMessageContaining("quyền");
            assertThat(n.isRead()).isFalse(); verify(repository, never()).save(any());
        }
        // Chủ thông báo được đánh dấu đọc, không sửa người nhận.
        @Test void markOwnedNotificationRead() {
            var n = new Notification(); n.setUser(Fixtures.user(1)); when(repository.findById(8)).thenReturn(Optional.of(n));
            service.markAsRead(8); assertThat(n.isRead()).isTrue(); verify(repository).save(n);
        }
        // Notification không tồn tại trả lỗi và không ghi dữ liệu mới.
        @Test void missingNotificationIsRejected() {
            assertThatThrownBy(() -> service.markAsRead(8)).hasMessageContaining("thông báo");
            verify(repository, never()).save(any());
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Audit {
        @Mock AuditLogRepository repository;
        @Mock UserRepository users;
        @InjectMocks AuditLogServiceImpl service;
        // Audit lưu actor và ID đối tượng, không nhầm entity_id với user_id.
        @Test void recordsActorEntityAndTime() {
            when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            service.logAction("UPDATE", EntityType.PROJECT, 10, "changed");
            verify(repository).save(argThat(a -> a.getUser().getUserId() == 1 && a.getEntityId() == 10
                    && a.getEntityType() == EntityType.PROJECT && a.getCreatedAt() != null));
        }
        // Tác vụ hệ thống chưa đăng nhập vẫn lưu audit với actor null.
        @Test void systemActionAllowsAbsentActor() {
            SecurityContextHolder.clearContext(); service.logAction("SEED", EntityType.USER, 1, "system");
            verify(repository).save(argThat(a -> a.getUser() == null && a.getAction().equals("SEED")));
        }
    }
}
