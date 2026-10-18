package com.example.jira.dto.board;

import jakarta.validation.constraints.Min;
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
    @NotNull(message = "Mã cột không được để trống")
    private Integer columnId;

    @NotNull(message = "Vị trí mới không được để trống")
    @Min(0)
    private Integer position;
}
