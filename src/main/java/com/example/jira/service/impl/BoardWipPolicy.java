package com.example.jira.service.impl;

import com.example.jira.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BoardWipPolicy {
    private final BoardColumnStatusRepository mappings;
    private final IssueRepository issues;

    /**
     * Caller đã khóa project trước chuyển status/kéo card. projectId là projects, statusId là trạng thái đích.
     * Xét mọi cột của board thuộc project có map status đích; WIP=0 nghĩa không giới hạn.
     * Đếm issues thật theo mapping, trừ issue đang chuyển để việc ở sẵn cột không bị tính hai lần.
     * Đủ chỗ mới cho caller ghi; vượt trả 409 trước ghi. Hàm chỉ đọc, không tự retry hay sửa cấu hình.
     */
    public void validate(int projectId, int statusId, int issueId) {
        for (var mapping : mappings.findStatusColumns(projectId,statusId)) {
            var column = mapping.getBoardColumn();
            if (column.getWipLimit() > 0 && issues.countInBoardColumn(projectId,column.getColumnId(),issueId) >= column.getWipLimit()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Cột đã đạt giới hạn WIP: " + column.getColumnName());
            }
        }
    }
}
