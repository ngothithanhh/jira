package com.example.jira.service.impl;

import com.example.jira.entity.UserRole;
import com.example.jira.repository.UserRoleRepository;
import com.example.jira.service.UserRoleService;
import com.example.jira.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository userRoleRepository;

    @Override
    public List<UserRole> getAllUserRoles(){
        return userRoleRepository.findAll()
                .stream().toList();
    }
}
