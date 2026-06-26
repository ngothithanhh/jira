package com.example.jira.dto.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBoardRequest {
    @NotNull
    private Integer projectId;

    @NotBlank
    private String boardName;

    @NotBlank
    private String boardType; // KANBAN or SCRUM
}
