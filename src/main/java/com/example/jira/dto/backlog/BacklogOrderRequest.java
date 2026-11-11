package com.example.jira.dto.backlog;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.List;

@Data
public class BacklogOrderRequest {
    // Danh sách đầy đủ ID issues theo thứ tự mới; [] hợp lệ khi backlog đang rỗng.
    @NotNull
    private List<@NotNull @Positive Integer> issueIds;
}
