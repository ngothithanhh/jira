package com.example.jira.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class AdminUserSearch {
    @Size(max=100) private String keyword;
    private Boolean enabled;
    @Min(0) private int page;
    @Min(1) @Max(100) private int size=20;
}
