package com.example.jira.dto.workflow;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRuleRequest {
    @Positive
    private int fromStatusId;
    @Positive
    private int toStatusId;
}
