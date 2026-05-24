package com.leave_service.service;
import com.leave_service.model.Leave;
public interface LeaveProcessService {
    void startLeaveProcess(Leave leave, String managerEmail, String adminEmail);
    void approveLeave(Long leaveId, String reviewedBy);
    void rejectLeave(Long leaveId, String reviewedBy, String reason);
    void partialReview(Long leaveId, String reviewedBy, java.util.List<java.util.Map<String, String>> dayDecisions);
    java.util.List<Long> getActiveLeaveIdsForTask(String taskDefKey);
}