package com.example.jira.dto.board;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class UpdateColumnRequest {
    @Size(max=100) private String columnName;
    @Min(0) private Integer wipLimit;
}
