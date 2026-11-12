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
        @Mock ProjectRepository projects;
        @Mock jakarta.persistence.EntityManager entityManager;
        @Mock AuditLogService audit;
        @BeforeEach void projectLock() { lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10))); }
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
        @Mock IssueAttachmentRepository attachments; @Mock IssueAttachmentContentRepository contents;
        @Mock IssueRepository issues; @Mock UserRepository users; @Mock ProjectSecurity security; @Mock ProjectRepository projects;
        @Mock ProjectMemberRepository members; @Mock jakarta.persistence.EntityManager entityManager;
        @Mock com.example.jira.service.AuditLogService audit;
        @InjectMocks IssueAttachmentServiceImpl service;
        private final MockMultipartFile file=new MockMultipartFile("file","sample.txt","text/plain",new byte[]{1,2});
        @BeforeEach void setup() {
            lenient().when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5,10)));
            lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));
            lenient().when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            lenient().when(attachments.saveAndFlush(any())).thenAnswer(inv->{IssueAttachment row=inv.getArgument(0);row.setAttachmentId(8);return row;});
        }
        // Metadata và nội dung đều là dữ liệu local; không có dependency Cloudinary trong service.
        @Test void uploadPersistsMetadataAndBytesForJwtOwner() {
            var result=service.uploadAttachment(5,file);
            assertThat(result.getFileName()).isEqualTo("sample.txt");assertThat(result.getFileSize()).isEqualTo(2L);assertThat(result.isDownloadAvailable()).isTrue();
            verify(attachments).saveAndFlush(argThat(a->a.getUser().getUserId()==1&&"LOCAL_DB".equals(a.getStorageType())&&a.getCloudPublicId()==null));
            verify(contents).saveAndFlush(argThat(c->c.getAttachment().getAttachmentId()==8&&java.util.Arrays.equals(c.getContent(),new byte[]{1,2})));
        }
        @Test void deniedUploadNeverWrites() {
            doThrow(new org.springframework.security.access.AccessDeniedException("quyền")).when(security).requirePermission(10,PermissionName.EDIT_ISSUE);
            assertThatThrownBy(()->service.uploadAttachment(5,file)).hasMessageContaining("quyền");verifyNoInteractions(contents,attachments);
        }
        @Test void permissionRevokedWhileWaitingStopsUpload() {
            doNothing().doThrow(new org.springframework.security.access.AccessDeniedException("revoked")).when(security).requirePermission(10,PermissionName.EDIT_ISSUE);
            assertThatThrownBy(()->service.uploadAttachment(5,file)).hasMessageContaining("revoked");verifyNoInteractions(contents,attachments);
        }
        @Test void invalidEmptyAndOversizedFilesStopBeforeWrite() {
            assertThatThrownBy(()->service.uploadAttachment(5,new MockMultipartFile("file","empty.txt","text/plain",new byte[0]))).hasMessageContaining("rỗng");
            assertThatThrownBy(()->service.uploadAttachment(5,new MockMultipartFile("file","large.txt","text/plain",new byte[10*1024*1024+1]))).hasMessageContaining("10 MiB");
            verifyNoInteractions(contents,attachments);
        }
        @Test void ownerStillNeedsViewPermission() {
            when(attachments.findById(8)).thenReturn(Optional.of(attachment(1)));
            doThrow(new org.springframework.security.access.AccessDeniedException("no view")).when(security).requirePermission(10,PermissionName.VIEW_PROJECT);
            assertThatThrownBy(()->service.deleteAttachment(8)).hasMessageContaining("no view");verifyNoInteractions(contents);verify(attachments,never()).delete(any());
        }
        @Test void nullableOwnerNeedsEditorPermission() {
            var row=attachment(1);row.setUser(null);when(attachments.findById(8)).thenReturn(Optional.of(row));
            lenient().doThrow(new org.springframework.security.access.AccessDeniedException("no edit")).when(security).requirePermission(10,PermissionName.EDIT_ISSUE);
            assertThatThrownBy(()->service.deleteAttachment(8)).hasMessageContaining("no edit");verifyNoInteractions(contents);
        }
        @Test void deleteRemovesBytesBeforeMetadata() {
            var row=attachment(1);when(attachments.findById(8)).thenReturn(Optional.of(row));
            service.deleteAttachmentForIssue(5,8);var order=inOrder(contents,attachments);
            order.verify(contents).deleteForAttachment(8);order.verify(attachments).delete(row);
        }
        @Test void wrongParentStopsBeforeBinaryReadOrDelete() {
            when(attachments.findById(8)).thenReturn(Optional.of(attachment(1)));
            assertThatThrownBy(()->service.deleteAttachmentForIssue(99,8)).hasMessageContaining("không thuộc");
            assertThatThrownBy(()->service.download(99,8)).hasMessageContaining("không thuộc");verifyNoInteractions(contents);
        }
        @Test void downloadRequiresViewBeforeLoadingBytes() {
            when(attachments.findById(8)).thenReturn(Optional.of(attachment(1)));
            doThrow(new org.springframework.security.access.AccessDeniedException("no view")).when(security).requirePermission(10,PermissionName.VIEW_PROJECT);
            assertThatThrownBy(()->service.download(5,8)).hasMessageContaining("no view");verifyNoInteractions(contents);
        }
        @Test void htmlClaimedAsImageStillDownloadsAsOctetStream() {
            var row=attachment(1);row.setStorageType("LOCAL_DB");row.setFileName("unsafe.html");row.setContentType("image/png");when(attachments.findById(8)).thenReturn(Optional.of(row));
            var data=new IssueAttachmentContent();data.setAttachment(row);data.setContent("<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));when(contents.findById(8)).thenReturn(Optional.of(data));
            var result=service.download(5,8);assertThat(result.contentType()).isEqualTo("application/octet-stream");assertThat(result.content()).isEqualTo(data.getContent());
        }
        @Test void legacyRemoteUrlIsNotFetched() {
            var row=attachment(1);row.setFilePath("https://example.invalid/legacy");when(attachments.findById(8)).thenReturn(Optional.of(row));
            assertThatThrownBy(()->service.download(5,8)).hasMessageContaining("DB local");verifyNoInteractions(contents);
        }
        @Test void listDoesNotLoadBinaryContents() {
            assertThat(service.getAttachments(5)).isEmpty();verify(security).requirePermission(10,PermissionName.VIEW_PROJECT);
            verify(attachments).findByIssue_IssueIdOrderByUploadedAtAsc(5);verifyNoInteractions(contents);
        }
        private IssueAttachment attachment(int owner) {
            var a=new IssueAttachment();a.setAttachmentId(8);a.setUser(Fixtures.user(owner));a.setIssue(Fixtures.issue(5,10));return a;
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
                    && a.getEntityType() == EntityType.PROJECT && a.getProjectId() == 10 && a.getCreatedAt() != null));
        }
        @Test void failedActorLookupCannotBecomeASystemAction() {
            when(users.findById(1)).thenThrow(new IllegalStateException("database unavailable"));
            assertThatThrownBy(() -> service.logProjectAction("DELETE", EntityType.ISSUE, 7, 10, "deleted"))
                    .hasMessage("database unavailable");
            verifyNoInteractions(repository);
        }
        // Tác vụ hệ thống chưa đăng nhập vẫn lưu audit với actor null.
        @Test void systemActionAllowsAbsentActor() {
            SecurityContextHolder.clearContext(); service.logAction("SEED", EntityType.USER, 1, "system");
            verify(repository).save(argThat(a -> a.getUser() == null && a.getAction().equals("SEED")));
        }
    }
}
