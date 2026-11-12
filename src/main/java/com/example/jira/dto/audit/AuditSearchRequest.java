package com.example.jira.dto.audit;
import com.example.jira.enums.EntityType;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class AuditSearchRequest {
    private EntityType entityType;
    @Positive private Integer entityId;
    @Positive private Integer actorId;
    @Positive private Integer projectId;
    private Boolean systemOnly;
    @Size(max=50) private String action;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private LocalDateTime createdFrom;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private LocalDateTime createdTo;
    @Min(0) private int page;
    @Min(1) @Max(100) private int size=20;
}
