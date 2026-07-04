package com.example.jira.dto.board;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateColumnOrderRequest {
    @NotNull(message = "Column ID không được để trống")
    private Integer columnId;

    @NotNull(message = "Position (vị trí mới) không được để trống")
    private Integer position;
}
