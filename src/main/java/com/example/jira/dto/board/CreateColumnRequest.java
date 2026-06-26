package com.example.jira.dto.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateColumnRequest {
    @NotNull
    private Integer boardId;

    @NotBlank
    private String columnName;

    private int position;
}
