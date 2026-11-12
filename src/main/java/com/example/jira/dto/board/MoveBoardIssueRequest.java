package com.example.jira.dto.board;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class MoveBoardIssueRequest {
    @NotNull @Positive private Integer columnId;
    @NotNull @Positive private Integer statusId;
    @NotNull @PositiveOrZero private Integer position;
    @NotNull @PositiveOrZero private Long expectedVersion;
    // ID sprint đang xem trên Scrum board; client cũ có thể bỏ qua để giữ luồng board toàn project.
    @Positive private Integer sprintId;
}
