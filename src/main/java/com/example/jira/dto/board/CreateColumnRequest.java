package com.example.jira.dto.board;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateColumnRequest {
    @NotNull
    private Integer boardId;

    @NotBlank
    @Size(max=50)
    private String columnName;

    @Min(0)
    private int position;
}
