package com.example.jira.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRuleRequest {
    private int fromStatusId;
    private int toStatusId;
}
