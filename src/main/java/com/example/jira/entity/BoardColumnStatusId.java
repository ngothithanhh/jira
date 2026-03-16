package com.example.jira.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Embeddable
@Getter @Setter
public class BoardColumnStatusId {
    private int columnId, statusId;

    public BoardColumnStatusId() {
    }

    public BoardColumnStatusId(int columnId, int statusId) {
        this.columnId = columnId;
        this.statusId = statusId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BoardColumnStatusId that)) return false;
        return columnId == that.columnId && statusId == that.statusId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnId, statusId);
    }
}
