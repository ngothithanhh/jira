package com.example.jira.dto.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBoardRequest {
    @NotNull
    private Integer projectId;

    @NotBlank
    @Size(max=100)
    private String boardName;

    @NotBlank
    @Pattern(regexp="KANBAN|SCRUM")
    private String boardType; // KANBAN or SCRUM
}
