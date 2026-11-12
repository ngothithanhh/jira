package com.example.jira.dto.board;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateColumnOrderRequest {
    @NotNull(message = "Column ID không được để trống")
    private Integer columnId;

    @NotNull(message = "Position (vị trí mới) không được để trống")
    private Integer position;

    /** Parse riêng endpoint để không ép chuỗi hoặc số thập phân thành số nguyên. */
    public static List<UpdateColumnOrderRequest> parse(JsonNode input) {
        if (input == null || !input.isArray() || input.isEmpty()) throw invalidOrder();
        List<UpdateColumnOrderRequest> rows = new ArrayList<>(input.size());
        for (JsonNode row : input) {
            if (!row.isObject() || row.size() != 2) throw invalidOrder();
            rows.add(new UpdateColumnOrderRequest(integer(row.get("columnId")), integer(row.get("position"))));
        }
        return rows;
    }

    private static int integer(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw invalidOrder();
        return value.intValue();
    }

    private static ResponseStatusException invalidOrder() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách thứ tự cột không hợp lệ");
    }
}
