package com.leave_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskRequest {
    private String title;
    private String description;
    private String status;
    private String priority;
    private String dueDate;
    private String managerEmail;
    private String completedAt;
    private String completionRemarks;
}
