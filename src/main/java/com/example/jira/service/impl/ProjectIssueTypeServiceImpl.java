package com.example.jira.service.impl;

import com.example.jira.dto.issue.issue_type.ProjectIssueTypeResponse;
import com.example.jira.entity.IssueType;
import com.example.jira.entity.Project;
import com.example.jira.entity.ProjectIssueType;
import com.example.jira.entity.ProjectIssueTypeId;
import com.example.jira.mapper.ProjectIssueTypeMapper;
import com.example.jira.repository.IssueRepository;
import com.example.jira.repository.IssueTypeRepository;
import com.example.jira.repository.ProjectIssueTypeRepository;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.service.ProjectIssueTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class ProjectIssueTypeServiceImpl implements ProjectIssueTypeService {
    private final ProjectIssueTypeRepository projectIssueTypeRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;

    @Override
    public List<ProjectIssueTypeResponse> getProjectIssueTypes(int projectId){

        List<ProjectIssueType> pt = projectIssueTypeRepository.findByProjectId(projectId);

        return pt.stream().map(ProjectIssueTypeMapper::toResponse).toList();

    }

    @Override
    @Transactional
    public void assignIssueType(int projectId, int issueTypeId, boolean isDefault){
        Project project = projectRepository.findById(projectId).orElseThrow(()->new RuntimeException("Không tìm thấy dự án!"));

        IssueType issueType = issueTypeRepository.findById(issueTypeId).orElseThrow(()->new RuntimeException("Không tìm thấy loại nhiệm vụ!"));

        ProjectIssueTypeId id = new ProjectIssueTypeId(projectId,issueTypeId);

        if(projectIssueTypeRepository.existsById(id))
            throw new RuntimeException("Đã được gán cho dự án rồi!");

        ProjectIssueType projectIssueType = new ProjectIssueType(id, project, issueType, isDefault);
        projectIssueTypeRepository.save(projectIssueType);
    }

    @Override
    @Transactional
    public void setDefaultIssueType(int projectId, int issueTypeId){
        List<ProjectIssueType> allTypesInProject = projectIssueTypeRepository.findByProjectId(projectId);

        boolean found = false;
        for(ProjectIssueType projectIssueType : allTypesInProject){
            if(projectIssueType.getIssueType().getIssueTypeId() == issueTypeId){
                found = true;
                projectIssueType.setDefault(true);
            }
            else {
                projectIssueType.setDefault(false);
            }
        }

        if(!found){
            throw new RuntimeException("Bảng loại nhiệm vụ này chưa được gán vào dự án!");
        }

        projectIssueTypeRepository.saveAll(allTypesInProject);


    }

    @Override
    @Transactional
    public void removeIssueType(int projectId, int issueTypeId){
        boolean hasIssues = issueRepository.existsByProject_ProjectIdAndIssueType_IssueTypeId(projectId,issueTypeId);
        if(hasIssues)
            throw new RuntimeException("Không thể xóa vì đang được sử dụng!");

        ProjectIssueTypeId id = new ProjectIssueTypeId(projectId,issueTypeId);
        ProjectIssueType projectIssueType = projectIssueTypeRepository.findById(id).orElseThrow(()->new RuntimeException("Chưa được gán vào dự án!"));

        if(projectIssueType.isDefault())
            throw new RuntimeException("Không thể gỡ bỏ vì đang được cấu hình là mặc định. Hãy chọn 1 loại khác làm mặc định trước!");

        projectIssueTypeRepository.deleteById(id);
    }
}
