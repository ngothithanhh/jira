package com.example.jira.service.impl;
import com.example.jira.dto.issue.issue_type.*;
import com.example.jira.entity.IssueType;
import com.example.jira.mapper.IssueTypeMapper;
import com.example.jira.repository.*;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.service.IssueTypeService;
import com.example.jira.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@Service @RequiredArgsConstructor
public class IssueTypeServiceImpl implements IssueTypeService {
    private final IssueTypeRepository issueTypeRepository;
    private final GlobalSecurity globalSecurity;
    private final ProjectIssueTypeRepository projectIssueTypeRepository;
    private final IssueRepository issueRepository;

    /** Danh mục dùng chung cho các project; chỉ đọc tên/ID, không có usage count giả hoặc quyền sửa từ project role. */
    @Override @Transactional(readOnly=true)
    public List<IssueTypeResponse> getAllIssueTypes(){return issueTypeRepository.findAll().stream().map(IssueTypeMapper::toResponse).toList();}

    /** Admin tạo loại toàn hệ thống từ typeName; tên chuẩn hóa theo giới hạn cột50, DB unique chặn cả hai tạo đồng thời. */
    @Override @Transactional
    public IssueTypeResponse createIssueType(IssueTypeRequest request){
        globalSecurity.assertSystemAdmin();String name=name(request);
        if(issueTypeRepository.existsByTypeName(name))throw new ConflictException("Tên loại công việc đã có");
        var row=new IssueType();row.setTypeName(name);
        return IssueTypeMapper.toResponse(issueTypeRepository.saveAndFlush(row));
    }

    /** id là issue_types.issue_type_id, không phải projectId; đổi tên giữ toàn bộ liên kết project/issue. */
    @Override @Transactional
    public IssueTypeResponse updateIssueType(int id,IssueTypeRequest request){
        globalSecurity.assertSystemAdmin();String name=name(request);
        var row=issueTypeRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy Issue Type"));
        if(issueTypeRepository.existsByTypeNameAndIssueTypeIdNot(name,id))throw new ConflictException("Tên loại công việc đã có");
        row.setTypeName(name);return IssueTypeMapper.toResponse(issueTypeRepository.saveAndFlush(row));
    }

    /** Chỉ xóa loại chưa dùng. Bulk delete tránh cascade JPA; FK giữ cấu hình nếu có lượt gán/tạo issue đồng thời. */
    @Override @Transactional
    public void deleteIssueType(int id){
        globalSecurity.assertSystemAdmin();
        issueTypeRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy Issue Type"));
        if(projectIssueTypeRepository.existsByIssueType_IssueTypeId(id))throw new ConflictException("Loại công việc đang được gán cho dự án");
        if(issueRepository.existsByIssueType_IssueTypeId(id))throw new ConflictException("Loại công việc đang có công việc sử dụng");
        if(issueTypeRepository.deleteCatalogRow(id)==0)throw new NotFoundException("Không tìm thấy Issue Type");
    }
    /** Service kiểm cả caller nội bộ không đi qua Bean Validation; trả400 trước ghi tên rỗng/quá dài. */
    private String name(IssueTypeRequest request){
        if(request==null||request.getTypeName()==null||request.getTypeName().trim().isEmpty()||request.getTypeName().trim().length()>50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên loại công việc cần 1..50 ký tự");
        return request.getTypeName().trim();
    }
}
