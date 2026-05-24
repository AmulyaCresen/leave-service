package com.leave_service.service;
import org.flowable.engine.delegate.DelegateExecution;
public interface LeaveWorkflowService {
    void initializeLeaveProcess(DelegateExecution execution);

    void notifyManager(DelegateExecution execution);
    void notifyAdmin(DelegateExecution execution);
    void sendReminderEmail(DelegateExecution execution);
    void scheduleReminder(DelegateExecution execution);

    void processManagerApproval(DelegateExecution execution);
    void processManagerPartial(DelegateExecution execution);
    void processManagerRejection(DelegateExecution execution);

    void processAdminApproval(DelegateExecution execution);
    void processAdminPartial(DelegateExecution execution);
    void processAdminRejection(DelegateExecution execution);

    void finalizeLeaveProcess(DelegateExecution execution);
}