package com.example.jira.dto.board;

import com.example.jira.entity.BoardColumn;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class ColumnOrderToken {
    public static final String HEADER = "X-Expected-Column-Order";

    private ColumnOrderToken() { }

    public static boolean valid(String token) {
        return token != null && token.matches("[0-9a-f]{64}");
    }

    /** Cùng tập ID và vị trí tạo cùng token, kể cả khi thứ tự hàng trả về từ DB khác nhau. */
    public static String of(int boardId, List<BoardColumn> columns) {
        StringBuilder snapshot = new StringBuilder().append(boardId).append(';');
        columns.stream().sorted(Comparator.comparingInt(BoardColumn::getColumnId)).forEach(column ->
                snapshot.append(column.getColumnId()).append(':').append(column.getPosition()).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 không khả dụng", unavailable);
        }
    }
}
