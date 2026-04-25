package com.leave_service.dto;
import lombok.Data;
@Data
public class CreateLeaveTypeRequest {
    private String leaveName;
    private String leaveUniqueName;
    private String description;
    private Integer maxDays;
}