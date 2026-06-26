package com.example.jira.dto.board;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MapColumnStatusRequest {
    @NotNull
    private Integer columnId;

    @NotNull
    private Integer statusId;
}
