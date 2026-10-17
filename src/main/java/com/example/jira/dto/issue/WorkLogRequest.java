package com.example.jira.dto.issue;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record WorkLogRequest(@Min(1) int timeSpent, @Size(max = 255) String comment,
                             @NotNull @PastOrPresent LocalDateTime loggedAt) {}
