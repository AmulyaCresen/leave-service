package com.leave_service.dto;
import lombok.Data;
import java.util.List;
@Data
public class CreateLeaveRequest {
    private String leaveType;
    private String fromDate;
    private String toDate;
    private String reason;
    private String comments;
    private String dayType;
    private String halfDaySession;
    private String managerEmail;
    private List<LeaveDayEntry> days;
}