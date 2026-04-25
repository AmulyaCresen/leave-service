package com.leave_service.dto;
import lombok.Data;
@Data
public class UpdateLeaveRequest {
    private String leaveType;
    private String fromDate;
    private String toDate;
    private String reason;
    private String comments;
    private String dayType;
    private String halfDaySession;
}