package com.example.jira.dto.board;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class UpdateBoardRequest {
    @NotBlank @Size(max=100) private String boardName;
}
