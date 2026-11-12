package com.example.jira.controller;

import com.example.jira.dto.account.AccountContext;
import com.example.jira.service.impl.AccountContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AccountContextController {
    private final AccountContextService service;

    /** FE hỏi quyền của phiên JWT hiện tại; projectId tùy chọn thuộc projects, không phải ID người dùng. */
    @GetMapping("/api/account/context")
    public AccountContext context(@RequestParam(required = false) Integer projectId) {
        return service.getAccountContext(projectId);
    }
}
