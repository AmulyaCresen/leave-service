package com.leave_service.service.impl;
import com.leave_service.service.AsyncMailSender;
import com.leave_service.client.UserServiceClient;
import com.leave_service.commons.LeaveConstants;
import com.leave_service.model.Leave;
import com.leave_service.repository.EmployeeLeaveRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.service.LeaveWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveWorkflowServiceImpl implements LeaveWorkflowService {
    private final LeaveRepository leaveRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final AsyncMailSender asyncMailSender;
    private final UserServiceClient userServiceClient;

    @Value("${app.mail.admin}")
    private String adminEmail;

    private static final DateTimeFormatter TRAIL_DT_FMT =
        DateTimeFormatter.ofPattern(LeaveConstants.TRAIL_DT_PATTERN);

    private String now() { return LocalDateTime.now().format(TRAIL_DT_FMT); }

    @Override
    public void initializeLeaveProcess(DelegateExecution execution) {
        Long leaveId = (Long) execution.getVariable(LeaveConstants.VAR_LEAVE_ID);
        String employeeEmail = (String) execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL);
        log.info("[InitializeLeaveProcess] Starting leave process for leave id={}", leaveId);
        
        String employeeName = userServiceClient.getFullName(employeeEmail);
        addTrailEntryWithEmployeeName(leaveId, LeaveConstants.STATUS_PENDING, employeeName, null, null, null);
        
        execution.setVariable("adminRequired", true); 
        log.info("[InitializeLeaveProcess] Leave process initialized for leave id={}", leaveId);
    }

    @Override
    public void scheduleReminder(DelegateExecution execution) {
        String fromDateRaw = (String) execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW);
        try {
            LocalDate fromDate = LocalDate.parse(fromDateRaw);
            LocalDate reminderDate = fromDate.minusDays(2);
            ZonedDateTime reminderTime = reminderDate.atStartOfDay(ZoneId.systemDefault());
            // ISO-8601 date-time string required by Flowable timer
            String reminderFireTime = reminderTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            execution.setVariable("reminderFireTime", reminderFireTime);
            log.info("[ScheduleReminder] Reminder scheduled for {} (leave starts {})", reminderFireTime, fromDateRaw);
        } catch (Exception e) {
            log.warn("[ScheduleReminder] Could not parse fromDate '{}', skipping reminder: {}", fromDateRaw, e.getMessage());
        }
    }

    @Override
    public void sendReminderEmail(DelegateExecution execution) {
        String employeeEmail = (String) execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL);
        String managerEmail  = (String) execution.getVariable(LeaveConstants.VAR_MANAGER_EMAIL);
        String leaveType     = (String) execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE);
        String fromDate      = (String) execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW);
        String toDate        = (String) execution.getVariable(LeaveConstants.VAR_TO_DATE_RAW);
        Long leaveId         = (Long)   execution.getVariable(LeaveConstants.VAR_LEAVE_ID);

        String employeeName = userServiceClient.getFullName(employeeEmail);
        String subject = "Reminder: Upcoming Leave in 2 Days";
        String html = buildNotificationEmail("Leave Reminder", "#6a1b9a",
            "<p>Hi " + employeeName + ",</p><p>This is a reminder that your approved leave starts in <b>2 days</b>.</p>",
            new String[][]{
                {"Leave Type", leaveType},
                {"From Date", fromDate},
                {"To Date", toDate}
            },
            "<p>Please ensure all handovers are completed before your leave begins.</p>");
        asyncMailSender.send(employeeEmail, subject, html);
        log.info("[SendReminderEmail] Sent reminder to employee={} for leave id={}", employeeEmail, leaveId);
    }

    @Override
    public void notifyManager(DelegateExecution execution) {
        String managerEmail = (String) execution.getVariable(LeaveConstants.VAR_MANAGER_EMAIL);
        String employeeEmail = (String) execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL);
        String leaveType = (String) execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE);
        String fromDate = (String) execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW);
        String toDate = (String) execution.getVariable(LeaveConstants.VAR_TO_DATE_RAW);
        String reason = (String) execution.getVariable(LeaveConstants.VAR_REASON);

        String employeeName = userServiceClient.getFullName(employeeEmail);
        String subject = "New Leave Request from " + employeeName;
        String html = buildNotificationEmail("New Leave Request Pending Your Approval", "#1565c0",
            "<p>Hi Manager,</p><p><b>" + employeeName + "</b> has applied for leave and it requires your approval.</p>",
            new String[][]{
                {"Employee", employeeName},
                {"Leave Type", leaveType},
                {"From Date", fromDate},
                {"To Date", toDate},
                {"Reason", reason}
            },
            "<p>Please log in to the <b>Leave Management System</b> to approve or reject this request.</p>");

        asyncMailSender.send(managerEmail, subject, html);
        log.info("[NotifyManager] Notified manager={} for leave by {}", managerEmail, employeeEmail);
    }

    @Override
    public void notifyAdmin(DelegateExecution execution) {
        String adminEmail = (String) execution.getVariable(LeaveConstants.VAR_ADMIN_EMAIL);
        String employeeEmail = (String) execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL);
        String leaveType = (String) execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE);
        String fromDate = (String) execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW);
        String toDate = (String) execution.getVariable(LeaveConstants.VAR_TO_DATE_RAW);

        String employeeName = userServiceClient.getFullName(employeeEmail);
        String subject = "Leave Request Approved by Manager - Final Approval Required";
        String html = buildNotificationEmail("Leave Request Pending Your Final Approval", "#f57c00",
            "<p>Hi Admin,</p><p>A leave request from <b>" + employeeName + "</b> has been approved by the manager and requires your final approval.</p>",
            new String[][]{
                {"Employee", employeeName},
                {"Leave Type", leaveType},
                {"From Date", fromDate},
                {"To Date", toDate}
            },
            "<p>Please log in to the <b>Leave Management System</b> to provide final approval.</p>");

        asyncMailSender.send(adminEmail, subject, html);
        log.info("[NotifyAdmin] Notified admin={} for leave by {}", adminEmail, employeeEmail);
    }

    @Override
    public void processManagerApproval(DelegateExecution execution) {
        Long leaveId = (Long) execution.getVariable(LeaveConstants.VAR_LEAVE_ID);
        String managerEmail = (String) execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY);
        String employeeEmail = (String) execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL);
        
        log.info("[ProcessManagerApproval] Processing manager approval for leave id={} by manager={}", leaveId, managerEmail);
        
        String managerName = userServiceClient.getFullName(managerEmail);
        markAllDaysAsReviewedBy(leaveId, managerName, LeaveConstants.STAGE_MANAGER, LeaveConstants.STATUS_APPROVED);
        saveApprovedLeaveToManager(leaveId, managerEmail);
        addTrailEntry(leaveId, LeaveConstants.STATUS_MANAGER_APPROVED, managerName, null, LeaveConstants.STAGE_MANAGER);
        addManagerReviewAuditEntry(leaveId, managerName, LeaveConstants.STATUS_MANAGER_APPROVED, null);
        
        boolean isAdminLeave = adminEmail.equals(employeeEmail);
        execution.setVariable("adminRequired", !isAdminLeave);
        
        if (isAdminLeave) {
            // Admin's own leave — manager action is final, update balance and mark as APPROVED
            markAllDaysAsReviewedBy(leaveId, managerName, LeaveConstants.STAGE_ADMIN, LeaveConstants.STATUS_APPROVED);
            addTrailEntry(leaveId, LeaveConstants.STATUS_APPROVED, managerName, null, LeaveConstants.STAGE_ADMIN);
            updateLeaveBalance(leaveId);
            notifyEmployee(execution, "Your Leave has been Approved",
                "Your leave request has been approved by your manager and is now final.");
        } else {
            notifyEmployee(execution, "Manager Approved Your Leave",
                "Your leave request has been approved by your manager and is pending final HR approval.");
        }
        
        log.info("[ProcessManagerApproval] Manager approval processed for leave id={}, admin required: {}", leaveId, !isAdminLeave);
    }

    @Override
    public void processManagerPartial(DelegateExecution execution) {
        Long leaveId = (Long) execution.getVariable(LeaveConstants.VAR_LEAVE_ID);
        String managerEmail = (String) execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY);
        String employeeEmail = (String) execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> dayDecisions = (List<Map<String, String>>) execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS);

        log.info("[ProcessManagerPartial] Processing manager partial approval for leave id={} by manager={}", leaveId, managerEmail);

        String managerName = userServiceClient.getFullName(managerEmail);

        leaveRepository.findById(leaveId).ifPresent(leave -> {
            Map<String, String> decisionMap = new HashMap<>();
            Map<String, String> reasonMap = new HashMap<>();
            for (Map<String, String> d : dayDecisions) {
                decisionMap.put(d.get("date"), d.getOrDefault("status", LeaveConstants.STATUS_APPROVED));
                if (d.get("reason") != null && !d.get("reason").isBlank())
                    reasonMap.put(d.get("date"), d.get("reason"));
            }

            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();

            if (leave.getDays() != null) {
                leave.getDays().forEach(day -> {
                    String newStatus = decisionMap.get(day.getDate());
                    if (newStatus != null) {
                        day.setStatus(newStatus);
                        day.setReviewedBy(managerName);
                        day.setReviewStage(LeaveConstants.STAGE_MANAGER);
                        String reason = reasonMap.get(day.getDate());
                        if (reason != null) day.setReason(reason);
                        Map<String, String> entry = new HashMap<>();
                        entry.put("type", LeaveConstants.TYPE_DAY_DECISION);
                        entry.put("date", now());
                        entry.put("dayDate", day.getDate());
                        entry.put("reviewedBy", managerName);
                        entry.put("stage", LeaveConstants.STAGE_MANAGER);
                        entry.put("status", newStatus);
                        if (reason != null) entry.put("rejectionReason", reason);
                        trail.add(entry);
                    }
                });
            }

            Map<String, String> summaryEntry = new HashMap<>();
            summaryEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_MANAGER_APPROVED);
            summaryEntry.put(LeaveConstants.TRAIL_DATE, now());
            summaryEntry.put(LeaveConstants.TRAIL_REVIEWED_BY, managerName);
            summaryEntry.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_MANAGER);
            trail.add(summaryEntry);

            Map<String, String> auditEntry = new HashMap<>();
            auditEntry.put("type", LeaveConstants.TYPE_MANAGER_REVIEW);
            auditEntry.put("date", now());
            auditEntry.put("reviewedBy", managerName);
            auditEntry.put("stage", LeaveConstants.STAGE_MANAGER);
            auditEntry.put("reviewStatus", LeaveConstants.STATUS_PARTIAL);
            auditEntry.put("employeeEmail", leave.getEmailId());
            auditEntry.put("leaveType", leave.getLeaveType());
            trail.add(auditEntry);

            leave.setTrail(trail);
            leaveRepository.save(leave);
        });

        savePartiallyApprovedLeaveToManager(leaveId, managerEmail, dayDecisions);

        boolean isAdminLeave = adminEmail.equals(employeeEmail);
        execution.setVariable("adminRequired", !isAdminLeave);

        if (isAdminLeave) {
            boolean allApproved = dayDecisions.stream().allMatch(d -> LeaveConstants.STATUS_APPROVED.equals(d.get("status")));
            boolean allRejected = dayDecisions.stream().allMatch(d -> LeaveConstants.STATUS_REJECTED.equals(d.get("status")));
            String finalStatus = allRejected ? LeaveConstants.STATUS_REJECTED
                : allApproved ? LeaveConstants.STATUS_APPROVED
                : LeaveConstants.STATUS_PARTIAL;
            addTrailEntry(leaveId, finalStatus, managerName, null, LeaveConstants.STAGE_ADMIN);
            updateLeaveBalancePartial(leaveId);
            notifyEmployee(execution, "Your Leave has been Partially Approved",
                "Your manager has reviewed your leave. Some days were approved and are now final.");
        } else {
            notifyEmployee(execution, "Manager Partially Approved Your Leave",
                "Your manager has reviewed your leave request. Some days were approved and are pending final HR approval.");
        }

        log.info("[ProcessManagerPartial] Manager partial approval processed for leave id={}", leaveId);
    }

    @Override
    public void processManagerRejection(DelegateExecution execution) {
        Long leaveId = (Long) execution.getVariable(LeaveConstants.VAR_LEAVE_ID);
        String managerEmail = (String) execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY);
        String rejectionReason = (String) execution.getVariable(LeaveConstants.VAR_REJECTION_REASON);
        
        log.info("[ProcessManagerRejection] Processing manager rejection for leave id={} by manager={}", leaveId, managerEmail);
        
        String managerName = userServiceClient.getFullName(managerEmail);
        
        markAllDaysAsReviewedBy(leaveId, managerName, LeaveConstants.STAGE_MANAGER, LeaveConstants.STATUS_REJECTED, rejectionReason);
        
        addTrailEntry(leaveId, LeaveConstants.STATUS_REJECTED, managerName, rejectionReason, LeaveConstants.STAGE_MANAGER);
        addManagerReviewAuditEntry(leaveId, managerName, LeaveConstants.STATUS_REJECTED, rejectionReason);
        
        execution.setVariable("adminRequired", false);
        
        notifyEmployee(execution, "Manager Rejected Your Leave",
            "Your leave request has been rejected by your manager. Reason: " + (rejectionReason != null ? rejectionReason : "No reason provided"));
        
        log.info("[ProcessManagerRejection] Manager rejection processed for leave id={}", leaveId);
    }

    @Override
    public void processAdminApproval(DelegateExecution execution) {
        Long leaveId = (Long) execution.getVariable(LeaveConstants.VAR_LEAVE_ID);
        String adminReviewer = (String) execution.getVariable(LeaveConstants.VAR_REVIEWED_BY);
        
        log.info("[ProcessAdminApproval] Processing admin approval for leave id={} by admin={}", leaveId, adminReviewer);
        
        String adminName = userServiceClient.getFullName(adminReviewer);
        markDaysAsReviewedByAdmin(leaveId, adminName, LeaveConstants.STAGE_ADMIN, LeaveConstants.STATUS_APPROVED);
        saveApprovedLeaveToAdmin(leaveId, adminReviewer);
        addTrailEntry(leaveId, LeaveConstants.STATUS_APPROVED, adminName, null, LeaveConstants.STAGE_ADMIN);
        addAdminReviewAuditEntry(leaveId, adminName, LeaveConstants.STATUS_APPROVED, null);
        
        updateLeaveBalance(leaveId);
        
        notifyEmployee(execution, "Leave Approved - Final Approval",
            "Your leave request has been given final approval by HR. Your leave is now confirmed.");
        
        log.info("[ProcessAdminApproval] Admin approval processed for leave id={}", leaveId);
    }

    @Override
    public void processAdminPartial(DelegateExecution execution) {
        Long leaveId = (Long) execution.getVariable(LeaveConstants.VAR_LEAVE_ID);
        String adminReviewer = (String) execution.getVariable(LeaveConstants.VAR_REVIEWED_BY);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> dayDecisions = (List<Map<String, String>>) execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS);
        
        log.info("[ProcessAdminPartial] Processing admin partial approval for leave id={} by admin={}", leaveId, adminReviewer);
        
        String adminName = userServiceClient.getFullName(adminReviewer);
        updateDayStatusesWithReviewer(leaveId, dayDecisions, adminName, LeaveConstants.STAGE_ADMIN);
        savePartiallyApprovedLeaveToAdmin(leaveId, adminReviewer, dayDecisions);
        addTrailEntry(leaveId, LeaveConstants.STATUS_PARTIAL, adminName, null, LeaveConstants.STAGE_ADMIN);
        addAdminReviewAuditEntry(leaveId, adminName, LeaveConstants.STATUS_PARTIAL, null);
        
        updateLeaveBalancePartial(leaveId);
        
        notifyEmployee(execution, "Leave Partially Approved by HR",
            "HR has reviewed your leave request. Some days have been approved and some rejected.");
        
        log.info("[ProcessAdminPartial] Admin partial approval processed for leave id={}", leaveId);
    }

    @Override
    public void processAdminRejection(DelegateExecution execution) {
        Long leaveId = (Long) execution.getVariable(LeaveConstants.VAR_LEAVE_ID);
        String adminReviewer = (String) execution.getVariable(LeaveConstants.VAR_REVIEWED_BY);
        String rejectionReason = (String) execution.getVariable(LeaveConstants.VAR_REJECTION_REASON);
        
        log.info("[ProcessAdminRejection] Processing admin rejection for leave id={} by admin={}", leaveId, adminReviewer);
        
        String adminName = userServiceClient.getFullName(adminReviewer);
        markDaysAsReviewedByAdmin(leaveId, adminName, LeaveConstants.STAGE_ADMIN, LeaveConstants.STATUS_REJECTED, rejectionReason);
        addTrailEntry(leaveId, LeaveConstants.STATUS_REJECTED, adminName, rejectionReason, LeaveConstants.STAGE_ADMIN);
        addAdminReviewAuditEntry(leaveId, adminName, LeaveConstants.STATUS_REJECTED, rejectionReason);
        
        
        notifyEmployee(execution, "Leave Rejected by HR",
            "Your leave request has been rejected by HR. Reason: " + (rejectionReason != null ? rejectionReason : "No reason provided"));
        
        log.info("[ProcessAdminRejection] Admin rejection processed for leave id={}", leaveId);
    }

    @Override
    public void finalizeLeaveProcess(DelegateExecution execution) {
        Long leaveId = (Long) execution.getVariable(LeaveConstants.VAR_LEAVE_ID);
        String employeeEmail = (String) execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL);
        String leaveType = (String) execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE);
        
        log.info("[FinalizeLeaveProcess] Finalizing leave process for leave id={}", leaveId);
        
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail();
            if (trail != null && !trail.isEmpty()) {
                String finalStatus = trail.get(trail.size() - 1).get(LeaveConstants.TRAIL_STATUS);
                log.info("[FinalizeLeaveProcess] Leave id={} finalized with status: {}", leaveId, finalStatus);
            }
        });
        
        log.info("[FinalizeLeaveProcess] Leave process completed for leave id={}", leaveId);
    }

    private void addTrailEntry(Long leaveId, String status, String reviewedBy, String reason, String stage) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            
            Map<String, String> entry = new HashMap<>();
            entry.put(LeaveConstants.TRAIL_STATUS, status);
            entry.put(LeaveConstants.TRAIL_DATE, now());
            if (reviewedBy != null) entry.put(LeaveConstants.TRAIL_REVIEWED_BY, reviewedBy);
            if (reason != null) entry.put(LeaveConstants.TRAIL_REJECTION_REASON, reason);
            if (stage != null) entry.put(LeaveConstants.TRAIL_STAGE, stage);
            
            trail.add(entry);
            leave.setTrail(trail);
            leaveRepository.save(leave);
            
            log.info("[AddTrailEntry] Added trail entry for leave id={}, status={}, stage={}", leaveId, status, stage);
        });
    }

    private void addTrailEntryWithEmployeeName(Long leaveId, String status, String employeeName, String reviewedBy, String reason, String stage) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            
            Map<String, String> entry = new HashMap<>();
            entry.put(LeaveConstants.TRAIL_STATUS, status);
            entry.put(LeaveConstants.TRAIL_DATE, now());
            if (employeeName != null) entry.put("employeeName", employeeName);
            if (reviewedBy != null) entry.put(LeaveConstants.TRAIL_REVIEWED_BY, reviewedBy);
            if (reason != null) entry.put(LeaveConstants.TRAIL_REJECTION_REASON, reason);
            if (stage != null) entry.put(LeaveConstants.TRAIL_STAGE, stage);
            
            trail.add(entry);
            leave.setTrail(trail);
            leaveRepository.save(leave);
            
            log.info("[AddTrailEntryWithEmployeeName] Added trail entry for leave id={}, status={}, employee={}", leaveId, status, employeeName);
        });
    }

    private void updateDayStatuses(Long leaveId, List<Map<String, String>> dayDecisions) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            if (leave.getDays() != null && !leave.getDays().isEmpty()) {
                Map<String, String> decisionMap = new HashMap<>();
                Map<String, String> reasonMap = new HashMap<>();
                
                for (Map<String, String> decision : dayDecisions) {
                    String date = decision.get("date");
                    String status = decision.getOrDefault("status", LeaveConstants.STATUS_APPROVED);
                    String reason = decision.get("reason");
                    
                    decisionMap.put(date, status);
                    if (reason != null && !reason.trim().isEmpty()) {
                        reasonMap.put(date, reason);
                    }
                }
                
                leave.getDays().forEach(day -> {
                    String newStatus = decisionMap.get(day.getDate());
                    if (newStatus != null) {
                        day.setStatus(newStatus);
                        String reason = reasonMap.get(day.getDate());
                        if (reason != null) {
                            day.setReason(reason);
                        }
                    }
                });
                
                leaveRepository.save(leave);
                log.info("[UpdateDayStatuses] Updated day statuses for leave id={}", leaveId);
            }
        });
    }

    private void updateDayStatusesWithReviewer(Long leaveId, List<Map<String, String>> dayDecisions, String reviewerName, String stage) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            if (leave.getDays() != null && !leave.getDays().isEmpty()) {
                Map<String, String> decisionMap = new HashMap<>();
                Map<String, String> reasonMap = new HashMap<>();
                
                for (Map<String, String> decision : dayDecisions) {
                    String date = decision.get("date");
                    String status = decision.getOrDefault("status", LeaveConstants.STATUS_APPROVED);
                    String reason = decision.get("reason");
                    decisionMap.put(date, status);
                    if (reason != null && !reason.trim().isEmpty()) {
                        reasonMap.put(date, reason);
                    }
                }

                List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();

                leave.getDays().forEach(day -> {
                    String newStatus = decisionMap.get(day.getDate());
                    if (newStatus != null) {
                        day.setStatus(newStatus);
                        day.setReviewedBy(reviewerName);
                        day.setReviewStage(stage);
                        String reason = reasonMap.get(day.getDate());
                        if (reason != null) day.setReason(reason);

                        Map<String, String> entry = new HashMap<>();
                        entry.put("date", now());
                        entry.put("dayDate", day.getDate());
                        entry.put("reviewedBy", reviewerName);
                        entry.put("stage", stage);
                        entry.put("status", newStatus);
                        entry.put("type", LeaveConstants.TYPE_DAY_DECISION);
                        if (reason != null) entry.put("rejectionReason", reason);
                        trail.add(entry);

                        log.info("[UpdateDayStatusesWithReviewer] Day {} decision: {} by {} at stage {}",
                            day.getDate(), newStatus, reviewerName, stage);
                    }
                });

                leave.setTrail(trail);
                leaveRepository.save(leave);
                log.info("[UpdateDayStatusesWithReviewer] Updated day statuses for leave id={} by reviewer={} at stage={}", leaveId, reviewerName, stage);
            }
        });
    }

    private void addDayWorkflowEntry(Leave leave, String date, String reviewerName, String stage, String status, String reason) {
        List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
        
        Map<String, String> entry = new HashMap<>();
        entry.put("date", now());
        entry.put("dayDate", date);
        entry.put("reviewedBy", reviewerName);
        entry.put("stage", stage);
        entry.put("status", status);
        entry.put("type", LeaveConstants.TYPE_DAY_DECISION);
        if (reason != null) {
            entry.put("rejectionReason", reason);
        }
        
        trail.add(entry);
        leave.setTrail(trail);
        
        log.info("[AddDayWorkflowEntry] Added day workflow entry for date {} by {} at stage {} with status {}", 
            date, reviewerName, stage, status);
    }

    private void updateLeaveBalance(Long leaveId) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            // Count only days with APPROVED status to handle cases where some days were rejected
            double approvedDays;
            if (leave.getDays() != null && !leave.getDays().isEmpty()) {
                approvedDays = leave.getDays().stream()
                    .filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.getStatus()))
                    .mapToDouble(d -> LeaveConstants.DAY_TYPE_HALF.equals(d.getDayType()) ? 0.5 : 1.0)
                    .sum();
            } else {
                approvedDays = calculateTotalDays(leave);
            }
            if (approvedDays > 0) {
                updateEmployeeBalance(leave.getEmailId(), leave.getLeaveType(), approvedDays);
                log.info("[UpdateLeaveBalance] Updated balance for leave id={}, approved days={}", leaveId, approvedDays);
            }
        });
    }

    private void updateLeaveBalancePartial(Long leaveId) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            if (leave.getDays() != null && !leave.getDays().isEmpty()) {
                double approvedDays = leave.getDays().stream()
                    .filter(day -> LeaveConstants.STATUS_APPROVED.equals(day.getStatus()))
                    .mapToDouble(day -> LeaveConstants.DAY_TYPE_HALF.equals(day.getDayType()) ? 0.5 : 1.0)
                    .sum();
                
                if (approvedDays > 0) {
                    updateEmployeeBalance(leave.getEmailId(), leave.getLeaveType(), approvedDays);
                    log.info("[UpdateLeaveBalancePartial] Updated balance for leave id={}, approved days={}", leaveId, approvedDays);
                }
            }
        });
    }

    private void updateEmployeeBalance(String emailId, String leaveType, double days) {
        employeeLeaveRepository.findByEmailId(emailId).ifPresent(empLeave -> {
            Map<String, Object> balances = empLeave.getLeaves();
            if (balances != null && balances.containsKey(leaveType)) {
                double current = ((Number) balances.get(leaveType)).doubleValue();
                balances.put(leaveType, Math.max(0.0, current - days));
                empLeave.setLeaves(balances);
                employeeLeaveRepository.save(empLeave);
                log.info("[UpdateEmployeeBalance] Updated balance for {}:{} by {} days", emailId, leaveType, days);
            }
        });
    }

    private double calculateTotalDays(Leave leave) {
        if (leave.getDays() != null && !leave.getDays().isEmpty()) {
            return leave.getDays().stream()
                .mapToDouble(day -> LeaveConstants.DAY_TYPE_HALF.equals(day.getDayType()) ? 0.5 : 1.0)
                .sum();
        }
        return leave.getDayType() != null && LeaveConstants.DAY_TYPE_HALF.equals(leave.getDayType()) ? 0.5 : 1.0;
    }

    private void notifyEmployee(DelegateExecution execution, String subject, String message) {
        String employeeEmail = (String) execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL);
        String leaveType = (String) execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE);
        String fromDate = (String) execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW);
        String toDate = (String) execution.getVariable(LeaveConstants.VAR_TO_DATE_RAW);

        String employeeName = userServiceClient.getFullName(employeeEmail);
        String html = buildNotificationEmail(subject, "#2e7d32",
            "<p>Hi " + employeeName + ",</p><p>" + message + "</p>",
            new String[][]{
                {"Leave Type", leaveType},
                {"From Date", fromDate},
                {"To Date", toDate}
            },
            "<p>You can view the complete status in the <b>Leave Management System</b>.</p>");

        asyncMailSender.send(employeeEmail, subject, html);
    }

    private String buildNotificationEmail(String heading, String headerColor, String intro, String[][] rows, String footer) {
        StringBuilder tableRows = new StringBuilder();
        for (String[] row : rows) {
            tableRows.append("<tr>")
                .append("<td style='padding:10px 14px;background:#f9f9f9;font-weight:600;color:#333;border:1px solid #ddd;width:160px'>")
                .append(row[0]).append("</td>")
                .append("<td style='padding:10px 14px;border:1px solid #ddd;color:#555'>")
                .append(row[1]).append("</td>")
                .append("</tr>");
        }
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden'>"
            + "<div style='background:" + headerColor + ";padding:20px 24px'>"
            + "<h2 style='color:#fff;margin:0;font-size:18px'>" + heading + "</h2></div>"
            + "<div style='padding:20px 24px'>" + intro
            + "<table style='width:100%;border-collapse:collapse;margin:16px 0'>" + tableRows + "</table>"
            + footer + "</div>"
            + "<div style='background:#f5f5f5;padding:12px 24px;font-size:12px;color:#999;text-align:center'>"
            + "Leave Management System — This is an automated email, please do not reply.</div></div>";
    }

    private void markAllDaysAsReviewedBy(Long leaveId, String reviewerName, String stage, String status) {
        markAllDaysAsReviewedBy(leaveId, reviewerName, stage, status, null);
    }

    private void markAllDaysAsReviewedBy(Long leaveId, String reviewerName, String stage, String status, String reason) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            if (leave.getDays() != null && !leave.getDays().isEmpty()) {
                List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
                leave.getDays().forEach(day -> {
                    day.setStatus(status);
                    day.setReviewedBy(reviewerName);
                    day.setReviewStage(stage);
                    if (reason != null) day.setReason(reason);
                    Map<String, String> entry = new HashMap<>();
                    entry.put("type", LeaveConstants.TYPE_DAY_DECISION);
                    entry.put("date", now());
                    entry.put("dayDate", day.getDate());
                    entry.put("reviewedBy", reviewerName);
                    entry.put("stage", stage);
                    entry.put("status", status);
                    if (reason != null) entry.put("rejectionReason", reason);
                    trail.add(entry);
                });
                leave.setTrail(trail);
                leaveRepository.save(leave);
            }
        });
    }

    private void markDaysAsReviewedByAdmin(Long leaveId, String reviewerName, String stage, String status) {
        markDaysAsReviewedByAdmin(leaveId, reviewerName, stage, status, null);
    }

    private void markDaysAsReviewedByAdmin(Long leaveId, String reviewerName, String stage, String status, String reason) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            if (leave.getDays() != null && !leave.getDays().isEmpty()) {
                List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
                leave.getDays().forEach(day -> {
                    if (LeaveConstants.STAGE_MANAGER.equals(day.getReviewStage())
                            && LeaveConstants.STATUS_APPROVED.equals(day.getStatus())) {
                        day.setStatus(status);
                        day.setReviewedBy(reviewerName);
                        day.setReviewStage(stage);
                        if (reason != null) day.setReason(reason);
                        Map<String, String> entry = new HashMap<>();
                        entry.put("type", LeaveConstants.TYPE_DAY_DECISION);
                        entry.put("date", now());
                        entry.put("dayDate", day.getDate());
                        entry.put("reviewedBy", reviewerName);
                        entry.put("stage", stage);
                        entry.put("status", status);
                        if (reason != null) entry.put("rejectionReason", reason);
                        trail.add(entry);
                        log.info("[MarkDaysAsReviewedByAdmin] Day {} marked as {} by {} at stage {}",
                            day.getDate(), status, reviewerName, stage);
                    }
                });
                leave.setTrail(trail);
                leaveRepository.save(leave);
            }
        });
    }

    private void saveApprovedLeaveToManager(Long leaveId, String managerEmail) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            employeeLeaveRepository.findByEmailId(managerEmail).ifPresentOrElse(
                managerLeave -> {
                    Map<String, Object> leaves = managerLeave.getLeaves() != null ? managerLeave.getLeaves() : new HashMap<>();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> approvedLeaves = (List<Map<String, Object>>) leaves.getOrDefault(LeaveConstants.APPROVED_LEAVES_KEY, new ArrayList<>());
                    approvedLeaves.add(buildLeaveRecord(leaveId, leave, calculateTotalDays(leave), "APPROVED"));
                    leaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
                    managerLeave.setLeaves(leaves);
                    employeeLeaveRepository.save(managerLeave);
                    log.info("[SaveApprovedLeaveToManager] Saved leave id={} to manager={}", leaveId, managerEmail);
                },
                () -> {
                    com.leave_service.model.EmployeeLeave newRecord = new com.leave_service.model.EmployeeLeave();
                    newRecord.setEmailId(managerEmail);
                    newRecord.setFullName(userServiceClient.getFullName(managerEmail));
                    List<Map<String, Object>> approvedLeaves = new ArrayList<>();
                    approvedLeaves.add(buildLeaveRecord(leaveId, leave, calculateTotalDays(leave), "APPROVED"));
                    Map<String, Object> leaves = new HashMap<>();
                    leaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
                    newRecord.setLeaves(leaves);
                    employeeLeaveRepository.save(newRecord);
                    log.info("[SaveApprovedLeaveToManager] Created new record and saved leave id={} to manager={}", leaveId, managerEmail);
                }
            );
        });
    }

    private void savePartiallyApprovedLeaveToManager(Long leaveId, String managerEmail, List<Map<String, String>> dayDecisions) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            double approvedDays = dayDecisions.stream()
                .filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.get("status")))
                .mapToDouble(d -> LeaveConstants.DAY_TYPE_HALF.equals(d.get("dayType")) ? 0.5 : 1.0)
                .sum();
            if (approvedDays <= 0) return;
            employeeLeaveRepository.findByEmailId(managerEmail).ifPresentOrElse(
                managerLeave -> {
                    Map<String, Object> leaves = managerLeave.getLeaves() != null ? managerLeave.getLeaves() : new HashMap<>();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> approvedLeaves = (List<Map<String, Object>>) leaves.getOrDefault(LeaveConstants.APPROVED_LEAVES_KEY, new ArrayList<>());
                    approvedLeaves.add(buildLeaveRecord(leaveId, leave, approvedDays, "PARTIAL"));
                    leaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
                    managerLeave.setLeaves(leaves);
                    employeeLeaveRepository.save(managerLeave);
                    log.info("[SavePartiallyApprovedLeaveToManager] Saved partial leave id={} to manager={}, days={}", leaveId, managerEmail, approvedDays);
                },
                () -> {
                    com.leave_service.model.EmployeeLeave newRecord = new com.leave_service.model.EmployeeLeave();
                    newRecord.setEmailId(managerEmail);
                    newRecord.setFullName(userServiceClient.getFullName(managerEmail));
                    List<Map<String, Object>> approvedLeaves = new ArrayList<>();
                    approvedLeaves.add(buildLeaveRecord(leaveId, leave, approvedDays, "PARTIAL"));
                    Map<String, Object> leaves = new HashMap<>();
                    leaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
                    newRecord.setLeaves(leaves);
                    employeeLeaveRepository.save(newRecord);
                    log.info("[SavePartiallyApprovedLeaveToManager] Created new record and saved partial leave id={} to manager={}", leaveId, managerEmail);
                }
            );
        });
    }

    private Map<String, Object> buildLeaveRecord(Long leaveId, Leave leave, double totalDays, String status) {
        Map<String, Object> record = new HashMap<>();
        record.put("leaveId", leaveId);
        record.put("employeeEmail", leave.getEmailId());
        record.put("leaveType", leave.getLeaveType());
        record.put("fromDate", leave.getFromDate().toString());
        record.put("toDate", leave.getToDate().toString());
        record.put("approvedDate", now());
        record.put("totalDays", totalDays);
        record.put("status", status);
        return record;
    }

    private void addManagerReviewAuditEntry(Long leaveId, String managerName, String reviewStatus, String reason) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            Map<String, String> auditEntry = new HashMap<>();
            auditEntry.put("type", LeaveConstants.TYPE_MANAGER_REVIEW);
            auditEntry.put("date", now());
            auditEntry.put("reviewedBy", managerName);
            auditEntry.put("stage", LeaveConstants.STAGE_MANAGER);
            auditEntry.put("reviewStatus", reviewStatus);
            auditEntry.put("employeeEmail", leave.getEmailId());
            auditEntry.put("leaveType", leave.getLeaveType());
            if (reason != null) auditEntry.put("rejectionReason", reason);
            trail.add(auditEntry);
            leave.setTrail(trail);
            leaveRepository.save(leave);
            log.info("[AddManagerReviewAuditEntry] Added manager review audit entry for leave id={}, status={}", leaveId, reviewStatus);
        });
    }

    private void saveApprovedLeaveToAdmin(Long leaveId, String adminEmail) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            double approvedDays = leave.getDays() != null && !leave.getDays().isEmpty()
                ? leave.getDays().stream()
                    .filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.getStatus()))
                    .mapToDouble(d -> LeaveConstants.DAY_TYPE_HALF.equals(d.getDayType()) ? 0.5 : 1.0)
                    .sum()
                : calculateTotalDays(leave);
            employeeLeaveRepository.findByEmailId(adminEmail).ifPresentOrElse(
                adminLeave -> {
                    Map<String, Object> leaves = adminLeave.getLeaves() != null ? adminLeave.getLeaves() : new HashMap<>();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> approvedLeaves = (List<Map<String, Object>>) leaves.getOrDefault(LeaveConstants.APPROVED_LEAVES_KEY, new ArrayList<>());
                    approvedLeaves.add(buildLeaveRecord(leaveId, leave, approvedDays, "APPROVED"));
                    leaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
                    adminLeave.setLeaves(leaves);
                    employeeLeaveRepository.save(adminLeave);
                    log.info("[SaveApprovedLeaveToAdmin] Saved leave id={} to admin={}", leaveId, adminEmail);
                },
                () -> {
                    com.leave_service.model.EmployeeLeave newRecord = new com.leave_service.model.EmployeeLeave();
                    newRecord.setEmailId(adminEmail);
                    newRecord.setFullName(userServiceClient.getFullName(adminEmail));
                    List<Map<String, Object>> approvedLeaves = new ArrayList<>();
                    approvedLeaves.add(buildLeaveRecord(leaveId, leave, approvedDays, "APPROVED"));
                    Map<String, Object> leaves = new HashMap<>();
                    leaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
                    newRecord.setLeaves(leaves);
                    employeeLeaveRepository.save(newRecord);
                    log.info("[SaveApprovedLeaveToAdmin] Created new record and saved leave id={} to admin={}", leaveId, adminEmail);
                }
            );
        });
    }

    private void savePartiallyApprovedLeaveToAdmin(Long leaveId, String adminEmail, List<Map<String, String>> dayDecisions) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            double approvedDays = dayDecisions.stream()
                .filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.get("status")))
                .mapToDouble(d -> LeaveConstants.DAY_TYPE_HALF.equals(d.get("dayType")) ? 0.5 : 1.0)
                .sum();
            if (approvedDays <= 0) return;
            employeeLeaveRepository.findByEmailId(adminEmail).ifPresentOrElse(
                adminLeave -> {
                    Map<String, Object> leaves = adminLeave.getLeaves() != null ? adminLeave.getLeaves() : new HashMap<>();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> approvedLeaves = (List<Map<String, Object>>) leaves.getOrDefault(LeaveConstants.APPROVED_LEAVES_KEY, new ArrayList<>());
                    approvedLeaves.add(buildLeaveRecord(leaveId, leave, approvedDays, "PARTIAL"));
                    leaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
                    adminLeave.setLeaves(leaves);
                    employeeLeaveRepository.save(adminLeave);
                    log.info("[SavePartiallyApprovedLeaveToAdmin] Saved partial leave id={} to admin={}, days={}", leaveId, adminEmail, approvedDays);
                },
                () -> {
                    com.leave_service.model.EmployeeLeave newRecord = new com.leave_service.model.EmployeeLeave();
                    newRecord.setEmailId(adminEmail);
                    newRecord.setFullName(userServiceClient.getFullName(adminEmail));
                    List<Map<String, Object>> approvedLeaves = new ArrayList<>();
                    approvedLeaves.add(buildLeaveRecord(leaveId, leave, approvedDays, "PARTIAL"));
                    Map<String, Object> leaves = new HashMap<>();
                    leaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
                    newRecord.setLeaves(leaves);
                    employeeLeaveRepository.save(newRecord);
                    log.info("[SavePartiallyApprovedLeaveToAdmin] Created new record and saved partial leave id={} to admin={}", leaveId, adminEmail);
                }
            );
        });
    }

    private void addAdminReviewAuditEntry(Long leaveId, String adminName, String reviewStatus, String reason) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            Map<String, String> auditEntry = new HashMap<>();
            auditEntry.put("type", LeaveConstants.TYPE_ADMIN_REVIEW);
            auditEntry.put("date", now());
            auditEntry.put("reviewedBy", adminName);
            auditEntry.put("stage", LeaveConstants.STAGE_ADMIN);
            auditEntry.put("reviewStatus", reviewStatus);
            auditEntry.put("employeeEmail", leave.getEmailId());
            auditEntry.put("leaveType", leave.getLeaveType());
            if (reason != null) auditEntry.put("rejectionReason", reason);
            trail.add(auditEntry);
            leave.setTrail(trail);
            leaveRepository.save(leave);
            log.info("[AddAdminReviewAuditEntry] Added admin review audit entry for leave id={}, status={}", leaveId, reviewStatus);
        });
    }
}
