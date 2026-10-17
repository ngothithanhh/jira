package com.example.jira.dto.board;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class UpdateColumnRequest {
    @NotBlank
    @Size(max=50)
    private String columnName;
    @Min(0)
    private int wipLimit;
}
