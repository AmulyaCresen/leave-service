package com.leave_service.dto;
import lombok.Data;
@Data
public class LeaveDayEntry {
    private String date;
    private String dayType;
    private String halfDaySession;
    private String status;
    private String reason;
    private String reviewedBy;
    private String reviewStage;
}