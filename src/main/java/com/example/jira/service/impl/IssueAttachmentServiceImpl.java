package com.example.jira.service.impl;

import com.example.jira.enums.EntityType;

import com.example.jira.dto.issue.IssueAttachmentResponse;
import com.example.jira.entity.*;
import com.example.jira.mapper.IssueAttachmentMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.IssueAttachmentService;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class IssueAttachmentServiceImpl implements IssueAttachmentService {
    public static final long MAX_FILE_SIZE=10L*1024*1024;
    private final com.example.jira.service.AuditLogService audit;
    private final IssueAttachmentRepository attachmentRepository;
    private final IssueAttachmentContentRepository contentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ProjectSecurity projectSecurity;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final EntityManager entityManager;

    /** issueId là issues.issue_id; kiểm EDIT_ISSUE rồi lưu metadata và bytea cùng transaction PostgreSQL, không gọi dịch vụ ngoài. */
    @Override @Transactional
    public IssueAttachmentResponse uploadAttachment(int issueId,MultipartFile file) {
        Issue issue=issue(issueId);
        projectSecurity.requirePermission(issue.getProject().getProjectId(),PermissionName.EDIT_ISSUE);
        String name=fileName(file);
        byte[] bytes;
        try { bytes=file.getBytes(); } catch(java.io.IOException failure) { throw error(HttpStatus.BAD_REQUEST,"Không đọc được nội dung tệp"); }
        if(bytes.length==0||bytes.length>MAX_FILE_SIZE)throw error(HttpStatus.PAYLOAD_TOO_LARGE,"Tệp cần có nội dung và tối đa 10 MiB");
        lock(issue);
        projectSecurity.requirePermission(issue.getProject().getProjectId(),PermissionName.EDIT_ISSUE);
        User actor=userRepository.findById(SecurityUtils.getCurrentUserId()).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Chưa xác thực"));
        IssueAttachment attachment=new IssueAttachment();
        attachment.setIssue(issue);attachment.setUser(actor);attachment.setUploadedAt(LocalDateTime.now());
        attachment.setFileName(name);attachment.setFileSize((long)bytes.length);attachment.setStorageType("LOCAL_DB");
        String mime=file.getContentType();
        // MIME từ multipart chỉ là metadata; download chỉ cho preview raster sau kiểm chữ ký byte thực.
        attachment.setContentType(mime!=null&&mime.length()<=255&&!mime.contains("\r")&&!mime.contains("\n")?mime:"application/octet-stream");
        attachment=attachmentRepository.saveAndFlush(attachment);
        attachment.setFilePath("/api/issues/"+issueId+"/attachments/"+attachment.getAttachmentId()+"/content");
        IssueAttachmentContent content=new IssueAttachmentContent();content.setAttachment(attachment);content.setContent(bytes);
        contentRepository.saveAndFlush(content);

        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("UPLOAD_ATTACHMENT",EntityType.ATTACHMENT,attachment.getAttachmentId(),issue.getProject().getProjectId(),"Tải tệp lên issue ID "+issueId);
        // Lỗi lưu bytea sẽ rollback cả metadata; không có asset ngoài DB cần bù hoặc retry.
        return IssueAttachmentMapper.toResponse(attachment);
    }

    /** GET danh sách chỉ đọc issue_attachments, không tải bytea từ bảng nội dung. */
    @Override @Transactional(readOnly=true)
    public List<IssueAttachmentResponse> getAttachments(int issueId) {
        Issue issue=issue(issueId);
        projectSecurity.requirePermission(issue.getProject().getProjectId(),PermissionName.VIEW_PROJECT);
        return attachmentRepository.findByIssue_IssueIdOrderByUploadedAtAsc(issueId).stream().map(IssueAttachmentMapper::toResponse).toList();
    }

    /** Kiểm parent và VIEW_PROJECT trước tải bytea. Tệp legacy chỉ có URL không được chuyển thành tải URL ra ngoài. */
    @Override @Transactional(readOnly=true)
    public Download download(int issueId,int attachmentId) {
        IssueAttachment attachment=attachment(attachmentId,issueId);
        projectSecurity.requirePermission(attachment.getIssue().getProject().getProjectId(),PermissionName.VIEW_PROJECT);
        if(!"LOCAL_DB".equals(attachment.getStorageType()))throw error(HttpStatus.CONFLICT,"Tệp cũ chưa có nội dung trong DB local");
        var content=contentRepository.findById(attachmentId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy nội dung tệp"));
        byte[] bytes=content.getContent();
        if(bytes==null||bytes.length>MAX_FILE_SIZE)throw error(HttpStatus.PAYLOAD_TOO_LARGE,"Nội dung tệp không hợp lệ");
        return new Download(attachment.getFileName()==null?"attachment-"+attachmentId:attachment.getFileName(),rasterMime(bytes),bytes);
    }

    /** Route cũ vẫn kiểm quyền như route mới; không xóa dữ liệu bên ngoài PostgreSQL. */
    @Override @Transactional
    public void deleteAttachment(int attachmentId) { delete(attachmentId,null); }

    /** Route FE chứa cả parent issue và attachment; sai parent404 trước khi sửa dữ liệu. */
    @Override @Transactional
    public void deleteAttachmentForIssue(int issueId,int attachmentId) { delete(attachmentId,issueId); }

    /** Metadata và bytea xóa cùng transaction; khóa project/refresh chặn hai lượt cùng xóa một file. */
    private void delete(int attachmentId,Integer issueId) {
        IssueAttachment attachment=attachment(attachmentId,issueId);
        canDelete(attachment);lock(attachment.getIssue());entityManager.refresh(attachment);canDelete(attachment);
        contentRepository.deleteForAttachment(attachmentId);
        attachmentRepository.delete(attachment);attachmentRepository.flush();
        // Lưu phạm vi project cùng thay đổi nghiệp vụ; lỗi audit làm rollback, không tự retry.
        audit.logProjectAction("DELETE_ATTACHMENT",EntityType.ATTACHMENT,attachmentId,attachment.getIssue().getProject().getProjectId(),"Xóa tệp của issue ID "+attachment.getIssue().getIssueId());
    }

    /** Parent lấy từ DB chứ không lấy issueId người gọi làm quyền cho một attachment khác. */
    private IssueAttachment attachment(int id,Integer expectedIssue) {
        IssueAttachment row=attachmentRepository.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy tệp"));
        if(row.getIssue()==null||expectedIssue!=null&&row.getIssue().getIssueId()!=expectedIssue)throw error(HttpStatus.NOT_FOUND,"Tệp không thuộc công việc này");
        return row;
    }

    /** Chủ tệp vẫn cần VIEW_PROJECT; uploader legacy null chỉ được xóa bởi người có EDIT_ISSUE. */
    private void canDelete(IssueAttachment attachment) {
        int projectId=attachment.getIssue().getProject().getProjectId();
        projectSecurity.requirePermission(projectId,PermissionName.VIEW_PROJECT);
        if(attachment.getUser()==null||attachment.getUser().getUserId()!=SecurityUtils.getCurrentUserId())
            projectSecurity.requirePermission(projectId,PermissionName.EDIT_ISSUE);
    }

    /** Refresh project và membership actor sau khi chờ khóa; không dùng role đã nạp trước lượt đổi quyền khác. */
    private void lock(Issue issue) {
        int id=issue.getProject().getProjectId();
        var project=projects.findByIdForUpdate(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy dự án"));
        entityManager.refresh(project);
        members.findByProject_ProjectIdAndUser_UserId(id,SecurityUtils.getCurrentUserId()).ifPresent(entityManager::refresh);
        entityManager.refresh(issue);
    }

    /** Kiểm file thực trước ghi, bỏ đường dẫn OS trong tên; tên chỉ phục vụ hiển thị/Content-Disposition an toàn. */
    private String fileName(MultipartFile file) {
        if(file==null||file.isEmpty())throw error(HttpStatus.BAD_REQUEST,"Tệp không được rỗng");
        if(file.getSize()>MAX_FILE_SIZE)throw error(HttpStatus.PAYLOAD_TOO_LARGE,"Tệp tối đa 10 MiB");
        String name=file.getOriginalFilename();
        if(name!=null){name=name.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).trim();}
        if(name==null||name.isBlank()||name.length()>255||name.chars().anyMatch(c->c<32||c==127))throw error(HttpStatus.BAD_REQUEST,"Tên tệp không hợp lệ");
        return name;
    }

    /** Chỉ nhận raster theo magic bytes để preview bằng img; HTML/SVG và mọi loại khác luôn tải dạng attachment/octet-stream. */
    private String rasterMime(byte[] b) {
        if(b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return "image/png";
        if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "image/jpeg";
        if(b.length>=6&&b[0]=='G'&&b[1]=='I'&&b[2]=='F'&&b[3]=='8'&&(b[4]=='7'||b[4]=='9')&&b[5]=='a')return "image/gif";
        if(b.length>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return "image/webp";
        if(b.length>=2&&b[0]=='B'&&b[1]=='M')return "image/bmp";
        return "application/octet-stream";
    }
    private Issue issue(int id){return issueRepository.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy công việc"));}
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
