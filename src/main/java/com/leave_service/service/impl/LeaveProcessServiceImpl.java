package com.leave_service.service.impl;
import com.leave_service.service.AsyncMailSender;
import com.leave_service.client.UserServiceClient;
import com.leave_service.commons.LeaveConstants;
import com.leave_service.model.Leave;
import com.leave_service.repository.EmployeeLeaveRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.service.LeaveProcessService;
import com.leave_service.service.LeaveWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveProcessServiceImpl implements LeaveProcessService {
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final LeaveRepository leaveRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final AsyncMailSender asyncMailSender;
    private final UserServiceClient userServiceClient;
    private final LeaveWorkflowService leaveWorkflowService;
    
    @Value("${app.mail.admin}")
    private String adminEmail;
    @Override
    public void startLeaveProcess(Leave leave, String managerEmail, String adminEmail) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(LeaveConstants.VAR_LEAVE_ID,          leave.getId());
        variables.put(LeaveConstants.VAR_EMPLOYEE_EMAIL,    leave.getEmailId());
        variables.put(LeaveConstants.VAR_LEAVE_TYPE,        leave.getLeaveType());
        variables.put(LeaveConstants.VAR_FROM_DATE_RAW,     leave.getFromDate().toString());
        variables.put(LeaveConstants.VAR_TO_DATE_RAW,       leave.getToDate().toString());
        variables.put(LeaveConstants.VAR_REASON,            leave.getReason());
        variables.put(LeaveConstants.VAR_TOTAL_DAYS,        leave.getTotalDays() != null ? leave.getTotalDays() : 1.0);
        variables.put(LeaveConstants.VAR_MANAGER_EMAIL,     managerEmail);
        variables.put(LeaveConstants.VAR_ADMIN_EMAIL,       adminEmail);
        
        variables.put("managerDecision", "");
        variables.put("adminDecision", "");
        variables.put("adminRequired", true);
        
        String processInstanceId = runtimeService
            .startProcessInstanceByKey(
                LeaveConstants.PROCESS_KEY,
                LeaveConstants.PROCESS_BUSINESS_KEY + leave.getId(),
                variables)
            .getId();
        log.info("[StartLeaveProcess] Started process {} for leave id={}", processInstanceId, leave.getId());
    }
    @Override
    public void approveLeave(Long leaveId, String reviewedBy) {
        log.info("[ApproveLeave] Approving leave id={} by reviewer={}", leaveId, reviewedBy);
        
        Task task = getManagerTask(leaveId);
        boolean isManagerStage = task != null;
        if (task == null) task = getAdminTask(leaveId);
        
        if (task != null) {
            try {
                Map<String, Object> vars = new HashMap<>();
                if (isManagerStage) {
                    vars.put("managerDecision", "APPROVE_ALL");
                    vars.put(LeaveConstants.VAR_MANAGER_REVIEWED_BY, reviewedBy);
                } else {
                    vars.put("adminDecision", "APPROVE_ALL");
                    vars.put(LeaveConstants.VAR_REVIEWED_BY, reviewedBy);
                }
                taskService.complete(task.getId(), vars);
                log.info("[ApproveLeave] Leave id={} approved at {} stage by {}", leaveId, isManagerStage ? "MANAGER" : "ADMIN", reviewedBy);
                return;
            } catch (FlowableException e) {
                log.warn("[ApproveLeave] Flowable error approving leave id={}: {}. Falling back.", leaveId, e.getMessage());
            }
        } else {
            log.warn("[ApproveLeave] No Flowable task for leave id={}, applying direct DB approval", leaveId);
        }
        directApprove(leaveId, reviewedBy);
    }
    @Override
    public void rejectLeave(Long leaveId, String reviewedBy, String reason) {
        log.info("[RejectLeave] Rejecting leave id={} by reviewer={} with reason: {}", leaveId, reviewedBy, reason);
        
        Task task = getManagerTask(leaveId);
        boolean isManagerStage = task != null;
        if (task == null) task = getAdminTask(leaveId);
        
        if (task != null) {
            try {
                Map<String, Object> vars = new HashMap<>();
                if (isManagerStage) {
                    vars.put("managerDecision", "REJECT_ALL");
                    vars.put(LeaveConstants.VAR_MANAGER_REVIEWED_BY, reviewedBy);
                    vars.put(LeaveConstants.VAR_REJECTION_REASON, reason);
                } else {
                    vars.put("adminDecision", "REJECT_ALL");
                    vars.put(LeaveConstants.VAR_REVIEWED_BY, reviewedBy);
                    vars.put(LeaveConstants.VAR_REJECTION_REASON, reason);
                }
                taskService.complete(task.getId(), vars);
                log.info("[RejectLeave] Leave id={} rejected at {} stage by {}", leaveId, isManagerStage ? "MANAGER" : "ADMIN", reviewedBy);
                return;
            } catch (FlowableException e) {
                log.warn("[RejectLeave] Flowable error rejecting leave id={}: {}. Falling back.", leaveId, e.getMessage());
            }
        } else {
            log.warn("[RejectLeave] No Flowable task for leave id={}, applying direct DB rejection", leaveId);
        }
        directReject(leaveId, reviewedBy, reason);
    }
    @Override
    public void partialReview(Long leaveId, String reviewedBy, List<Map<String, String>> dayDecisions) {
        log.info("[PartialReview] Starting partial review for leave id={} by reviewer={}", leaveId, reviewedBy);
        log.info("[PartialReview] Day decisions: {}", dayDecisions);
        
        String reviewer = reviewedBy != null ? reviewedBy : LeaveConstants.SYSTEM_USER;
        Task task = getManagerTask(leaveId);
        boolean isManagerStage = task != null;
        
        log.info("[PartialReview] Manager task found: {}, task id: {}", isManagerStage, task != null ? task.getId() : "null");
        
        if (task == null) {
            task = getAdminTask(leaveId);
            log.info("[PartialReview] Admin task found: {}, task id: {}", task != null, task != null ? task.getId() : "null");
        }
        
        if (task != null) {
            try {
                log.info("[PartialReview] Processing Flowable task for leave id={} at {} stage", leaveId, isManagerStage ? "MANAGER" : "ADMIN");
                Map<String, Object> vars = new HashMap<>();
                
                // Determine decision type based on day decisions
                boolean allApproved = dayDecisions.stream().allMatch(d -> LeaveConstants.STATUS_APPROVED.equals(d.get("status")));
                boolean allRejected = dayDecisions.stream().allMatch(d -> LeaveConstants.STATUS_REJECTED.equals(d.get("status")));
                
                if (isManagerStage) {
                    vars.put(LeaveConstants.VAR_MANAGER_REVIEWED_BY, reviewer);
                    vars.put(LeaveConstants.VAR_MANAGER_DAY_DECISIONS, new ArrayList<>(dayDecisions));
                    
                    if (allApproved) {
                        vars.put("managerDecision", "APPROVE_ALL");
                    } else if (allRejected) {
                        vars.put("managerDecision", "REJECT_ALL");
                        String reason = dayDecisions.get(0).getOrDefault("reason", LeaveConstants.NO_REASON_PROVIDED);
                        vars.put(LeaveConstants.VAR_REJECTION_REASON, reason);
                    } else {
                        vars.put("managerDecision", "PARTIAL");
                    }
                } else {
                    vars.put(LeaveConstants.VAR_REVIEWED_BY, reviewer);
                    vars.put(LeaveConstants.VAR_DAY_DECISIONS, new ArrayList<>(dayDecisions));
                    
                    if (allApproved) {
                        vars.put("adminDecision", "APPROVE_ALL");
                    } else if (allRejected) {
                        vars.put("adminDecision", "REJECT_ALL");
                        String reason = dayDecisions.get(0).getOrDefault("reason", LeaveConstants.NO_REASON_PROVIDED);
                        vars.put(LeaveConstants.VAR_REJECTION_REASON, reason);
                    } else {
                        vars.put("adminDecision", "PARTIAL");
                    }
                }
                
                taskService.complete(task.getId(), vars);
                log.info("[PartialReview] Flowable task completed successfully for leave id={} at {} stage by {}", leaveId, isManagerStage ? "MANAGER" : "ADMIN", reviewer);
            } catch (FlowableException e) {
                log.warn("[PartialReview] Flowable error partial review id={}: {}. Falling back to direct method.", leaveId, e.getMessage());
                directPartialReview(leaveId, reviewer, dayDecisions);
            }
        } else {
            log.warn("[PartialReview] No Flowable task found for leave id={}, applying direct partial review", leaveId);
            directPartialReview(leaveId, reviewer, dayDecisions);
        }
    }
    public void directManagerPartialNotify(Long leaveId, String managerReviewer,
                                            List<Map<String, String>> dayDecisions, String adminEmail) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            String managerName  = userServiceClient.getFullName(managerReviewer != null ? managerReviewer : LeaveConstants.SYSTEM_USER);
            String employeeName = userServiceClient.getFullName(leave.getEmailId());
            String leaveType    = leave.getLeaveType();
            String fromDate     = leave.getFromDate().toString();
            String toDate       = leave.getToDate().toString();
            Map<String, String> decisionMap = dayDecisions.stream()
                .collect(Collectors.toMap(d -> d.get("date"), d -> d.getOrDefault("status", LeaveConstants.STATUS_APPROVED), (a, b) -> b));
            Map<String, String> reasonMap = dayDecisions.stream()
                .filter(d -> d.get("reason") != null && !d.get("reason").isBlank())
                .collect(Collectors.toMap(d -> d.get("date"), d -> d.get("reason"), (a, b) -> b));
            List<com.leave_service.dto.LeaveDayEntry> days = leave.getDays();
            if (days != null && !days.isEmpty()) {
                days.forEach(d -> d.setStatus(decisionMap.getOrDefault(d.getDate(), LeaveConstants.STATUS_APPROVED)));
                leave.setDays(days);
                leaveRepository.save(leave);
            }
            List<Map<String, String>> approvedDays = dayDecisions.stream()
                .filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.getOrDefault("status", LeaveConstants.STATUS_APPROVED)))
                .collect(Collectors.toList());
            List<Map<String, String>> rejectedDays = dayDecisions.stream()
                .filter(d -> LeaveConstants.STATUS_REJECTED.equals(d.get("status")))
                .collect(Collectors.toList());
            if (!approvedDays.isEmpty()) {
                String empApprovedSubject = "Manager Approved Your Leave â€” Awaiting HR Decision";
                String empApprovedHtml = buildPartialSummaryHtml(
                    "Manager Approved Your Leave Days",
                    "Hi " + employeeName + ", your manager has approved the following leave days. These are now pending HR final approval.",
                    leaveType, fromDate, toDate, managerName, approvedDays, true);
                asyncMailSender.send(leave.getEmailId(), empApprovedSubject, empApprovedHtml);
            }
            if (!rejectedDays.isEmpty()) {
                String empRejectedSubject = "Manager Rejected Some Leave Days";
                String empRejectedHtml = buildPartialSummaryHtml(
                    "Manager Rejected Your Leave Days",
                    "Hi " + employeeName + ", your manager has rejected the following leave days.",
                    leaveType, fromDate, toDate, managerName, rejectedDays, true);
                asyncMailSender.send(leave.getEmailId(), empRejectedSubject, empRejectedHtml);
            }
            if (!approvedDays.isEmpty()) {
                String adminSubject = "Leave Partially Approved by Manager â€” Action Required";
                String adminHtml = buildPartialSummaryHtml(
                    "Leave Request Pending Your Approval",
                    "Hi HR/Admin, the following leave days were approved by the manager and require your final decision.",
                    leaveType, fromDate, toDate, managerName, approvedDays, false);
                asyncMailSender.send(adminEmail, adminSubject, adminHtml);
            }
            log.info("[ManagerPartialNotify] Notified employee={} and admin={} for leave id={} (approved={}, rejected={})",
                leave.getEmailId(), adminEmail, leaveId, approvedDays.size(), rejectedDays.size());
        });
    }
    public void directPartialReview(Long leaveId, String reviewer, List<Map<String, String>> dayDecisions) {
        log.info("[DirectPartialReview] Processing partial review for leave id={} by reviewer={}", leaveId, reviewer);
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            String reviewerFullName = userServiceClient.getFullName(reviewer);
            String employeeName     = userServiceClient.getFullName(leave.getEmailId());
            String leaveType        = leave.getLeaveType();
            String fromDate         = leave.getFromDate().toString();
            String toDate           = leave.getToDate().toString();

            String stage = reviewer != null && reviewer.equals(adminEmail) ? LeaveConstants.STAGE_ADMIN : LeaveConstants.STAGE_MANAGER;
            log.info("[DirectPartialReview] Determined stage: {} for reviewer: {}", stage, reviewer);

            Map<String, String> decisionMap = dayDecisions.stream()
                .collect(Collectors.toMap(d -> d.get("date"), d -> d.getOrDefault("status", LeaveConstants.STATUS_APPROVED), (a, b) -> b));
            Map<String, String> reasonMap = dayDecisions.stream()
                .filter(d -> d.get("reason") != null && !d.get("reason").isBlank())
                .collect(Collectors.toMap(d -> d.get("date"), d -> d.get("reason"), (a, b) -> b));

            List<com.leave_service.dto.LeaveDayEntry> days = leave.getDays();

            if (LeaveConstants.STAGE_MANAGER.equals(stage)) {
                // Manager stage: update day statuses, save to logged leaves, add trail, notify employee + admin
                boolean isAdminLeave = adminEmail.equals(leave.getEmailId());

                if (days != null && !days.isEmpty()) {
                    days.forEach(d -> {
                        String decision = decisionMap.get(d.getDate());
                        if (decision != null) {
                            d.setStatus(decision);
                            if (LeaveConstants.STATUS_REJECTED.equals(decision)) {
                                String reason = reasonMap.get(d.getDate());
                                if (reason != null) d.setReason(reason);
                            }
                        }
                    });
                    leave.setDays(days);
                }

                boolean allApproved = dayDecisions.stream().allMatch(d -> LeaveConstants.STATUS_APPROVED.equals(d.get("status")));
                boolean allRejected = dayDecisions.stream().allMatch(d -> LeaveConstants.STATUS_REJECTED.equals(d.get("status")));
                String overallStatus = allApproved ? LeaveConstants.STATUS_MANAGER_APPROVED
                    : allRejected ? LeaveConstants.STATUS_REJECTED : LeaveConstants.STATUS_MANAGER_APPROVED;

                Map<String, String> trailEntry = new HashMap<>();
                trailEntry.put(LeaveConstants.TRAIL_STATUS, isAdminLeave
                    ? (allRejected ? LeaveConstants.STATUS_REJECTED : allApproved ? LeaveConstants.STATUS_APPROVED : LeaveConstants.STATUS_PARTIAL)
                    : overallStatus);
                trailEntry.put(LeaveConstants.TRAIL_DATE, now());
                trailEntry.put(LeaveConstants.TRAIL_REVIEWED_BY, reviewer);
                trailEntry.put(LeaveConstants.TRAIL_STAGE, isAdminLeave ? LeaveConstants.STAGE_ADMIN : LeaveConstants.STAGE_MANAGER);
                trail.add(trailEntry);
                leave.setTrail(trail);
                leaveRepository.save(leave);

                savePartiallyApprovedLeaveToManager(leaveId, reviewer, dayDecisions);
                addManagerReviewAuditEntry(leaveId, reviewerFullName, overallStatus, null);

                if (isAdminLeave || allRejected) {
                    if (isAdminLeave && !allRejected) {
                        // Update balance for admin's own leave
                        double approvedDays = dayDecisions.stream()
                            .filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.get("status")))
                            .mapToDouble(d -> LeaveConstants.DAY_TYPE_HALF.equals(d.get("dayType")) ? 0.5 : 1.0).sum();
                        employeeLeaveRepository.findByEmailId(leave.getEmailId()).ifPresent(empLeave -> {
                            Map<String, Object> balances = empLeave.getLeaves();
                            if (balances != null && balances.containsKey(leaveType)) {
                                double current = ((Number) balances.get(leaveType)).doubleValue();
                                balances.put(leaveType, Math.max(0.0, current - approvedDays));
                                empLeave.setLeaves(balances);
                                employeeLeaveRepository.save(empLeave);
                            }
                        });
                    }
                    asyncMailSender.send(leave.getEmailId(), "Manager Reviewed Your Leave",
                        buildMailTable("Manager Reviewed Your Leave", leaveType, fromDate, toDate, overallStatus, reviewerFullName, null));
                } else {
                    // Notify employee and admin for further action
                    List<Map<String, String>> approvedDays = dayDecisions.stream()
                        .filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.get("status"))).collect(Collectors.toList());
                    List<Map<String, String>> rejectedDays = dayDecisions.stream()
                        .filter(d -> LeaveConstants.STATUS_REJECTED.equals(d.get("status"))).collect(Collectors.toList());
                    if (!approvedDays.isEmpty())
                        asyncMailSender.send(leave.getEmailId(), "Manager Approved Your Leave - Awaiting HR",
                            buildPartialSummaryHtml("Manager Approved Your Leave Days",
                                "Hi " + employeeName + ", your manager approved the following days. Pending HR final approval.",
                                leaveType, fromDate, toDate, reviewerFullName, approvedDays, true));
                    if (!rejectedDays.isEmpty())
                        asyncMailSender.send(leave.getEmailId(), "Manager Rejected Some Leave Days",
                            buildPartialSummaryHtml("Manager Rejected Your Leave Days",
                                "Hi " + employeeName + ", your manager rejected the following days.",
                                leaveType, fromDate, toDate, reviewerFullName, rejectedDays, true));
                    if (!approvedDays.isEmpty())
                        asyncMailSender.send(adminEmail, "Leave Approved by Manager - Action Required",
                            buildPartialSummaryHtml("Leave Request Pending Your Approval",
                                "Hi HR/Admin, the following days were approved by the manager and require your final decision.",
                                leaveType, fromDate, toDate, reviewerFullName, approvedDays, false));
                }
                return;
            }

            if (days != null && !days.isEmpty()) {
                days.forEach(d -> {
                    if (LeaveConstants.STATUS_REJECTED.equals(d.getStatus())) return;
                    String decision = decisionMap.get(d.getDate());
                    if (decision != null) {
                        d.setStatus(decision);
                        if (LeaveConstants.STATUS_REJECTED.equals(decision)) {
                            String reason = reasonMap.get(d.getDate());
                            if (reason != null) d.setReason(reason);
                        }
                    }
                });
                leave.setDays(days);
            }

            List<com.leave_service.dto.LeaveDayEntry> finalApproved = days != null ? days.stream()
                .filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.getStatus())).collect(Collectors.toList()) : new ArrayList<>();
            List<com.leave_service.dto.LeaveDayEntry> finalRejected = days != null ? days.stream()
                .filter(d -> LeaveConstants.STATUS_REJECTED.equals(d.getStatus())).collect(Collectors.toList()) : new ArrayList<>();

            String overallStatus = finalRejected.isEmpty() ? LeaveConstants.STATUS_APPROVED
                : finalApproved.isEmpty() ? LeaveConstants.STATUS_REJECTED : LeaveConstants.STATUS_PARTIAL;

            Map<String, String> finalEntry = new HashMap<>();
            finalEntry.put(LeaveConstants.TRAIL_STATUS, overallStatus);
            finalEntry.put(LeaveConstants.TRAIL_DATE, now());
            finalEntry.put(LeaveConstants.TRAIL_REVIEWED_BY, reviewer);
            finalEntry.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
            trail.add(finalEntry);
            leave.setTrail(trail);
            leaveRepository.save(leave);

            savePartiallyApprovedLeaveToAdmin(leaveId, reviewer, dayDecisions);
            addAdminReviewAuditEntry(leaveId, reviewerFullName, overallStatus, null);

            // Update employee balance only at admin final stage
            if (!finalApproved.isEmpty()) {
                double approvedDaysCount = finalApproved.stream()
                    .mapToDouble(d -> LeaveConstants.DAY_TYPE_HALF.equals(d.getDayType()) ? 0.5 : 1.0).sum();
                employeeLeaveRepository.findByEmailId(leave.getEmailId()).ifPresent(empLeave -> {
                    Map<String, Object> balances = empLeave.getLeaves();
                    if (balances != null && balances.containsKey(leaveType)) {
                        double current = ((Number) balances.get(leaveType)).doubleValue();
                        balances.put(leaveType, Math.max(0.0, current - approvedDaysCount));
                        empLeave.setLeaves(balances);
                        employeeLeaveRepository.save(empLeave);
                    }
                });
            }

            List<Map<String, String>> approvedPayload = finalApproved.stream()
                .map(d -> { Map<String, String> m = new HashMap<>(); m.put("date", d.getDate()); m.put("status", LeaveConstants.STATUS_APPROVED); return m; })
                .collect(Collectors.toList());
            List<Map<String, String>> rejectedPayload = finalRejected.stream()
                .filter(d -> decisionMap.containsKey(d.getDate()))
                .map(d -> { Map<String, String> m = new HashMap<>(); m.put("date", d.getDate()); m.put("status", LeaveConstants.STATUS_REJECTED); m.put("reason", reasonMap.getOrDefault(d.getDate(), "")); return m; })
                .collect(Collectors.toList());
            if (!approvedPayload.isEmpty())
                asyncMailSender.send(leave.getEmailId(), "HR Approved Your Leave Days",
                    buildPartialSummaryHtml("HR Approved Your Leave Days",
                        "Hi " + employeeName + ", HR has approved the following leave days.",
                        leaveType, fromDate, toDate, reviewerFullName, approvedPayload, true));
            if (!rejectedPayload.isEmpty())
                asyncMailSender.send(leave.getEmailId(), "HR Rejected Some Leave Days",
                    buildPartialSummaryHtml("HR Rejected Your Leave Days",
                        "Hi " + employeeName + ", HR has rejected the following leave days.",
                        leaveType, fromDate, toDate, reviewerFullName, rejectedPayload, true));
        });
    }
    private void directApprove(Long leaveId, String reviewedBy) {
        log.info("[DirectApprove] Processing direct approval for leave id={} by reviewer={}", leaveId, reviewedBy);
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            String dayType = trail.isEmpty() ? LeaveConstants.DAY_TYPE_FULL
                : trail.get(0).getOrDefault(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_FULL);

            String stage = reviewedBy != null && reviewedBy.equals(adminEmail) ? LeaveConstants.STAGE_ADMIN : LeaveConstants.STAGE_MANAGER;
            boolean isAdminLeave = adminEmail.equals(leave.getEmailId());
            String reviewerFullName = userServiceClient.getFullName(reviewedBy != null ? reviewedBy : LeaveConstants.SYSTEM_USER);

            if (LeaveConstants.STAGE_MANAGER.equals(stage)) {
                // Mark all days approved + write DAY_DECISION trail entries
                if (leave.getDays() != null) {
                    leave.getDays().forEach(d -> {
                        d.setStatus(LeaveConstants.STATUS_APPROVED);
                        d.setReviewedBy(reviewerFullName);
                        d.setReviewStage(LeaveConstants.STAGE_MANAGER);
                        Map<String, String> dayEntry = new HashMap<>();
                        dayEntry.put("type", LeaveConstants.TYPE_DAY_DECISION);
                        dayEntry.put("date", now());
                        dayEntry.put("dayDate", d.getDate());
                        dayEntry.put("reviewedBy", reviewerFullName);
                        dayEntry.put("stage", LeaveConstants.STAGE_MANAGER);
                        dayEntry.put("status", LeaveConstants.STATUS_APPROVED);
                        trail.add(dayEntry);
                    });
                }
                String trailStatus = isAdminLeave ? LeaveConstants.STATUS_APPROVED : LeaveConstants.STATUS_MANAGER_APPROVED;
                Map<String, String> entry = new HashMap<>();
                entry.put(LeaveConstants.TRAIL_STATUS, trailStatus);
                entry.put(LeaveConstants.TRAIL_DAY_TYPE, dayType);
                entry.put(LeaveConstants.TRAIL_DATE, now());
                entry.put(LeaveConstants.TRAIL_REVIEWED_BY, reviewedBy != null ? reviewedBy : LeaveConstants.SYSTEM_USER);
                entry.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_MANAGER);
                trail.add(entry);
                leave.setTrail(trail);
                leaveRepository.save(leave);

                saveApprovedLeaveToManager(leaveId, reviewedBy);
                addManagerReviewAuditEntry(leaveId, reviewerFullName, LeaveConstants.STATUS_APPROVED, null);

                if (isAdminLeave) {
                    double totalDays = calculateTotalDays(leave);
                    employeeLeaveRepository.findByEmailId(leave.getEmailId()).ifPresent(empLeave -> {
                        Map<String, Object> balances = empLeave.getLeaves();
                        if (balances != null && balances.containsKey(leave.getLeaveType())) {
                            double current = ((Number) balances.get(leave.getLeaveType())).doubleValue();
                            balances.put(leave.getLeaveType(), Math.max(0.0, current - totalDays));
                            empLeave.setLeaves(balances);
                            employeeLeaveRepository.save(empLeave);
                        }
                    });
                    asyncMailSender.send(leave.getEmailId(), "Leave Approved: " + leave.getLeaveType(),
                        buildMailTable("Your Leave Request has been Approved",
                            leave.getLeaveType(), leave.getFromDate().toString(), leave.getToDate().toString(),
                            LeaveConstants.STATUS_APPROVED, reviewerFullName, null));
                } else {
                    asyncMailSender.send(leave.getEmailId(), "Manager Approved Your Leave - Awaiting HR",
                        buildMailTable("Manager Approved Your Leave",
                            leave.getLeaveType(), leave.getFromDate().toString(), leave.getToDate().toString(),
                            LeaveConstants.STATUS_MANAGER_APPROVED, reviewerFullName, null));
                    asyncMailSender.send(adminEmail, "Leave Approved by Manager - Action Required",
                        buildMailTable("Leave Request Pending Your Approval",
                            leave.getLeaveType(), leave.getFromDate().toString(), leave.getToDate().toString(),
                            LeaveConstants.STATUS_MANAGER_APPROVED, reviewerFullName, null));
                }
            } else {
                if (leave.getDays() != null) {
                    leave.getDays().forEach(d -> {
                        if (!LeaveConstants.STATUS_REJECTED.equals(d.getStatus())) {
                            d.setStatus(LeaveConstants.STATUS_APPROVED);
                            d.setReviewedBy(reviewerFullName);
                            d.setReviewStage(LeaveConstants.STAGE_ADMIN);
                            Map<String, String> dayEntry = new HashMap<>();
                            dayEntry.put("type", LeaveConstants.TYPE_DAY_DECISION);
                            dayEntry.put("date", now());
                            dayEntry.put("dayDate", d.getDate());
                            dayEntry.put("reviewedBy", reviewerFullName);
                            dayEntry.put("stage", LeaveConstants.STAGE_ADMIN);
                            dayEntry.put("status", LeaveConstants.STATUS_APPROVED);
                            trail.add(dayEntry);
                        }
                    });
                }
                Map<String, String> entry = new HashMap<>();
                entry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
                entry.put(LeaveConstants.TRAIL_DAY_TYPE, dayType);
                entry.put(LeaveConstants.TRAIL_DATE, now());
                entry.put(LeaveConstants.TRAIL_REVIEWED_BY, reviewedBy != null ? reviewedBy : LeaveConstants.SYSTEM_USER);
                entry.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
                trail.add(entry);
                leave.setTrail(trail);
                leaveRepository.save(leave);

                saveApprovedLeaveToAdmin(leaveId, reviewedBy);
                addAdminReviewAuditEntry(leaveId, reviewerFullName, LeaveConstants.STATUS_APPROVED, null);

                double totalDays = calculateTotalDays(leave);
                employeeLeaveRepository.findByEmailId(leave.getEmailId()).ifPresent(empLeave -> {
                    Map<String, Object> balances = empLeave.getLeaves();
                    if (balances != null && balances.containsKey(leave.getLeaveType())) {
                        double current = ((Number) balances.get(leave.getLeaveType())).doubleValue();
                        balances.put(leave.getLeaveType(), Math.max(0.0, current - totalDays));
                        empLeave.setLeaves(balances);
                        employeeLeaveRepository.save(empLeave);
                    }
                });
                asyncMailSender.send(leave.getEmailId(), "Leave Approved: " + leave.getLeaveType(),
                    buildMailTable("Your Leave Request has been Approved by HR",
                        leave.getLeaveType(), leave.getFromDate().toString(), leave.getToDate().toString(),
                        LeaveConstants.STATUS_APPROVED, reviewerFullName, null));
            }
        });
    }
    private void directReject(Long leaveId, String reviewedBy, String reason) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            String dayType = trail.isEmpty() ? LeaveConstants.DAY_TYPE_FULL
                : trail.get(0).getOrDefault(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_FULL);

            String stage = reviewedBy != null && reviewedBy.equals(adminEmail) ? LeaveConstants.STAGE_ADMIN : LeaveConstants.STAGE_MANAGER;
            String reviewerFullName = userServiceClient.getFullName(reviewedBy != null ? reviewedBy : LeaveConstants.SYSTEM_USER);

            if (leave.getDays() != null) {
                leave.getDays().forEach(d -> {
                    if (LeaveConstants.STAGE_ADMIN.equals(stage) && LeaveConstants.STATUS_REJECTED.equals(d.getStatus())) return;
                    d.setStatus(LeaveConstants.STATUS_REJECTED);
                    d.setReviewedBy(reviewerFullName);
                    d.setReviewStage(stage);
                    if (reason != null) d.setReason(reason);
                    Map<String, String> dayEntry = new HashMap<>();
                    dayEntry.put("type", LeaveConstants.TYPE_DAY_DECISION);
                    dayEntry.put("date", now());
                    dayEntry.put("dayDate", d.getDate());
                    dayEntry.put("reviewedBy", reviewerFullName);
                    dayEntry.put("stage", stage);
                    dayEntry.put("status", LeaveConstants.STATUS_REJECTED);
                    if (reason != null && !reason.isBlank()) dayEntry.put("rejectionReason", reason);
                    trail.add(dayEntry);
                });
            }

            if (LeaveConstants.STAGE_MANAGER.equals(stage)) {
                addManagerReviewAuditEntry(leaveId, reviewerFullName, LeaveConstants.STATUS_REJECTED, reason);
            } else {
                addAdminReviewAuditEntry(leaveId, reviewerFullName, LeaveConstants.STATUS_REJECTED, reason);
            }

            Map<String, String> entry = new HashMap<>();
            entry.put(LeaveConstants.TRAIL_STATUS,      LeaveConstants.STATUS_REJECTED);
            entry.put(LeaveConstants.TRAIL_DAY_TYPE,    dayType);
            entry.put(LeaveConstants.TRAIL_DATE,        now());
            entry.put(LeaveConstants.TRAIL_REVIEWED_BY, reviewedBy != null ? reviewedBy : LeaveConstants.SYSTEM_USER);
            entry.put(LeaveConstants.TRAIL_STAGE,       stage);
            if (reason != null && !reason.isBlank())
                entry.put(LeaveConstants.TRAIL_REJECTION_REASON, reason);
            trail.add(entry);
            leave.setTrail(trail);
            leaveRepository.save(leave);
            log.info("[LeaveProcess] Direct DB rejection done for leave id={} by {} at {} stage", leaveId, reviewedBy, stage);
            String subject = "Leave Rejected: " + leave.getLeaveType() + " (" + leave.getFromDate() + " to " + leave.getToDate() + ")";
            String html = buildMailTable("Your Leave Request has been Rejected",
                leave.getLeaveType(), leave.getFromDate().toString(), leave.getToDate().toString(),
                LeaveConstants.STATUS_REJECTED, reviewerFullName,
                reason != null && !reason.isBlank() ? reason : LeaveConstants.NO_REASON_PROVIDED);
            asyncMailSender.send(leave.getEmailId(), subject, html);
        });
    }
    private String buildMailTable(String heading, String leaveType, String fromDate, String toDate,
                                   String status, String reviewedBy, String reason) {
        String statusColor = LeaveConstants.STATUS_APPROVED.equals(status) ? "#2e7d32" : "#c62828";
        String headerColor = LeaveConstants.STATUS_APPROVED.equals(status) ? "#2e7d32" : "#c62828";
        String rows = "<tr><td style='padding:10px 14px;background:#f9f9f9;font-weight:600;border:1px solid #ddd;width:160px'>Leave Type</td><td style='padding:10px 14px;border:1px solid #ddd'>" + leaveType + "</td></tr>"
            + "<tr><td style='padding:10px 14px;background:#f9f9f9;font-weight:600;border:1px solid #ddd'>From Date</td><td style='padding:10px 14px;border:1px solid #ddd'>" + fromDate + "</td></tr>"
            + "<tr><td style='padding:10px 14px;background:#f9f9f9;font-weight:600;border:1px solid #ddd'>To Date</td><td style='padding:10px 14px;border:1px solid #ddd'>" + toDate + "</td></tr>"
            + "<tr><td style='padding:10px 14px;background:#f9f9f9;font-weight:600;border:1px solid #ddd'>Status</td><td style='padding:10px 14px;border:1px solid #ddd;color:" + statusColor + ";font-weight:bold'>" + status + "</td></tr>"
            + "<tr><td style='padding:10px 14px;background:#f9f9f9;font-weight:600;border:1px solid #ddd'>Reviewed By</td><td style='padding:10px 14px;border:1px solid #ddd'>" + reviewedBy + "</td></tr>";
        if (reason != null)
            rows += "<tr><td style='padding:10px 14px;background:#f9f9f9;font-weight:600;border:1px solid #ddd'>Reason</td><td style='padding:10px 14px;border:1px solid #ddd'>" + reason + "</td></tr>";
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden'>"
            + "<div style='background:" + headerColor + ";padding:20px 24px'><h2 style='color:#fff;margin:0;font-size:18px'>" + heading + "</h2></div>"
            + "<div style='padding:20px 24px'><table style='width:100%;border-collapse:collapse'>" + rows + "</table></div>"
            + "<div style='background:#f5f5f5;padding:12px 24px;font-size:12px;color:#999;text-align:center'>Leave Management System &mdash; Automated email, do not reply.</div></div>";
    }
    private String buildPartialSummaryHtml(String heading, String intro, String leaveType,
                                            String fromDate, String toDate, String reviewerName,
                                            List<Map<String, String>> dayDecisions, boolean isEmployee) {
        StringBuilder approvedRows = new StringBuilder();
        StringBuilder rejectedRows = new StringBuilder();
        for (Map<String, String> d : dayDecisions) {
            String date   = d.getOrDefault("date", "");
            String status = d.getOrDefault("status", LeaveConstants.STATUS_APPROVED);
            String reason = d.getOrDefault("reason", "");
            if (LeaveConstants.STATUS_APPROVED.equals(status)) {
                String approvedRow = "<tr><td style='padding:8px 12px;border:1px solid #ddd'>" + date + "</td></tr>";
                approvedRows.append(approvedRow);
            } else {
                String rejectedRow = "<tr><td style='padding:8px 12px;border:1px solid #ddd'>" + date + "</td><td style='padding:8px 12px;border:1px solid #ddd'>" + reason + "</td></tr>";
                rejectedRows.append(rejectedRow);
            }
        }
        String headerColor = isEmployee ? "#f57c00" : "#1565c0";
        String approvedTable = approvedRows.length() > 0
            ? "<h4 style='color:#2e7d32'>Approved Days</h4>"
            + "<table style='width:100%;border-collapse:collapse;margin-bottom:16px'>"
            + "<thead><tr style='background:#e8f5e9'><th style='padding:8px 12px;border:1px solid #ddd;text-align:left'>Date</th></tr></thead>"
            + "<tbody>" + approvedRows + "</tbody></table>" : "";
        String rejectedTable = rejectedRows.length() > 0
            ? "<h4 style='color:#c62828'>Rejected Days</h4>"
            + "<table style='width:100%;border-collapse:collapse;margin-bottom:16px'>"
            + "<thead><tr style='background:#ffebee'><th style='padding:8px 12px;border:1px solid #ddd;text-align:left'>Date</th><th style='padding:8px 12px;border:1px solid #ddd;text-align:left'>Reason</th></tr></thead>"
            + "<tbody>" + rejectedRows + "</tbody></table>" : "";
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden'>"
            + "<div style='background:" + headerColor + ";padding:20px 24px'><h2 style='color:#fff;margin:0;font-size:18px'>" + heading + "</h2></div>"
            + "<div style='padding:20px 24px'><p>" + intro + "</p>"
            + "<p><b>Leave Type:</b> " + leaveType + " &nbsp; <b>From:</b> " + fromDate + " &nbsp; <b>To:</b> " + toDate + "</p>"
            + "<p><b>Reviewed By:</b> " + reviewerName + "</p>"
            + approvedTable + rejectedTable + "</div>"
            + "<div style='background:#f5f5f5;padding:12px 24px;font-size:12px;color:#999;text-align:center'>Leave Management System &mdash; Automated email, do not reply.</div></div>";
    }
    @Override
    public List<Long> getActiveLeaveIdsForTask(String taskDefKey) {
        return taskService.createTaskQuery()
            .taskDefinitionKey(taskDefKey)
            .list()
            .stream()
            .map(task -> {
                String businessKey = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult()
                    .getBusinessKey();
                try {
                    return Long.parseLong(businessKey.replace(LeaveConstants.PROCESS_BUSINESS_KEY, ""));
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(id -> id != null)
            .collect(Collectors.toList());
    }
    private void saveDayStatuses(Long leaveId, List<Map<String, String>> dayDecisions) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<com.leave_service.dto.LeaveDayEntry> days = leave.getDays();
            if (days == null || days.isEmpty()) return;
            Map<String, String> decisionMap = dayDecisions.stream()
                .collect(Collectors.toMap(d -> d.get("date"), d -> d.getOrDefault("status", LeaveConstants.STATUS_APPROVED), (a, b) -> b));
            Map<String, String> reasonMap = dayDecisions.stream()
                .filter(d -> d.get("reason") != null && !d.get("reason").isBlank())
                .collect(Collectors.toMap(d -> d.get("date"), d -> d.get("reason"), (a, b) -> b));
            days.forEach(d -> {
                d.setStatus(decisionMap.getOrDefault(d.getDate(), LeaveConstants.STATUS_APPROVED));
                String reason = reasonMap.get(d.getDate());
                if (reason != null) d.setReason(reason);
            });
            leave.setDays(days);
            leaveRepository.save(leave);
            log.info("[SaveDayStatuses] Saved day statuses for leave id={}", leaveId);
        });
    }
    private void saveTrailEntry(Long leaveId, String status, String reviewedBy, String reason, String stage) {
        log.info("[SaveTrailEntry] Saving trail entry for leave id={}, status={}, reviewedBy={}, stage={}", leaveId, status, reviewedBy, stage);
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            String dayType = trail.isEmpty() ? LeaveConstants.DAY_TYPE_FULL
                : trail.get(0).getOrDefault(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_FULL);
            Map<String, String> entry = new HashMap<>();
            entry.put(LeaveConstants.TRAIL_STATUS,      status);
            entry.put(LeaveConstants.TRAIL_DAY_TYPE,    dayType);
            entry.put(LeaveConstants.TRAIL_DATE,        now());
            entry.put(LeaveConstants.TRAIL_REVIEWED_BY, reviewedBy != null ? reviewedBy : LeaveConstants.SYSTEM_USER);
            entry.put(LeaveConstants.TRAIL_STAGE,       stage);
            if (reason != null && !reason.isBlank())
                entry.put(LeaveConstants.TRAIL_REJECTION_REASON, reason);
            trail.add(entry);
            leave.setTrail(trail);
            leaveRepository.save(leave);
            log.info("[SaveTrailEntry] Trail entry saved successfully for leave id={}, trail size now: {}, entry: {}", leaveId, trail.size(), entry);
        });
    }
    private Task getManagerTask(Long leaveId) {
        return taskService.createTaskQuery()
            .processInstanceBusinessKey(LeaveConstants.PROCESS_BUSINESS_KEY + leaveId)
            .taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)
            .singleResult();
    }
    private Task getAdminTask(Long leaveId) {
        return taskService.createTaskQuery()
            .processInstanceBusinessKey(LeaveConstants.PROCESS_BUSINESS_KEY + leaveId)
            .taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_ADMIN)
            .singleResult();
    }
    private static final DateTimeFormatter TRAIL_DT_FMT = DateTimeFormatter.ofPattern(LeaveConstants.TRAIL_DT_PATTERN);
    private String now() { return LocalDateTime.now().format(TRAIL_DT_FMT); }

    private void saveApprovedLeaveToManager(Long leaveId, String managerEmail) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            employeeLeaveRepository.findByEmailId(managerEmail).ifPresentOrElse(
                managerLeave -> {
                    Map<String, Object> managerLeaves = managerLeave.getLeaves();
                    if (managerLeaves == null) {
                        managerLeaves = new HashMap<>();
                    }
                    
                    String approvedLeavesKey = LeaveConstants.APPROVED_LEAVES_KEY;
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> approvedLeaves = (List<Map<String, Object>>) managerLeaves.get(approvedLeavesKey);
                    if (approvedLeaves == null) {
                        approvedLeaves = new ArrayList<>();
                    }
                    
                    Map<String, Object> leaveRecord = new HashMap<>();
                    leaveRecord.put("leaveId", leaveId);
                    leaveRecord.put("employeeEmail", leave.getEmailId());
                    leaveRecord.put("leaveType", leave.getLeaveType());
                    leaveRecord.put("fromDate", leave.getFromDate().toString());
                    leaveRecord.put("toDate", leave.getToDate().toString());
                    leaveRecord.put("approvedDate", LocalDateTime.now().format(TRAIL_DT_FMT));
                    leaveRecord.put("totalDays", calculateTotalDays(leave));
                    leaveRecord.put("status", "APPROVED");
                    
                    approvedLeaves.add(leaveRecord);
                    managerLeaves.put(approvedLeavesKey, approvedLeaves);
                    managerLeave.setLeaves(managerLeaves);
                    employeeLeaveRepository.save(managerLeave);
                    
                    log.info("[SaveApprovedLeaveToManager] Saved approved leave id={} to manager={}", leaveId, managerEmail);
                },
                () -> {
                    log.warn("[SaveApprovedLeaveToManager] Manager EmployeeLeave record not found for email: {}", managerEmail);
                    // Create a basic record for the manager
                    createManagerEmployeeLeaveRecord(managerEmail, leaveId, leave);
                }
            );
        });
    }

    private void savePartiallyApprovedLeaveToManager(Long leaveId, String managerEmail, List<Map<String, String>> dayDecisions) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            double approvedDays = dayDecisions.stream()
                .filter(decision -> LeaveConstants.STATUS_APPROVED.equals(decision.get("status")))
                .mapToDouble(decision -> {
                    String dayType = decision.get("dayType");
                    return LeaveConstants.DAY_TYPE_HALF.equals(dayType) ? 0.5 : 1.0;
                })
                .sum();
            
            if (approvedDays > 0) {
                employeeLeaveRepository.findByEmailId(managerEmail).ifPresentOrElse(
                    managerLeave -> {
                        Map<String, Object> managerLeaves = managerLeave.getLeaves();
                        if (managerLeaves == null) {
                            managerLeaves = new HashMap<>();
                        }
                        
                        String approvedLeavesKey = LeaveConstants.APPROVED_LEAVES_KEY;
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> approvedLeaves = (List<Map<String, Object>>) managerLeaves.get(approvedLeavesKey);
                        if (approvedLeaves == null) {
                            approvedLeaves = new ArrayList<>();
                        }
                        
                        Map<String, Object> leaveRecord = new HashMap<>();
                        leaveRecord.put("leaveId", leaveId);
                        leaveRecord.put("employeeEmail", leave.getEmailId());
                        leaveRecord.put("leaveType", leave.getLeaveType());
                        leaveRecord.put("fromDate", leave.getFromDate().toString());
                        leaveRecord.put("toDate", leave.getToDate().toString());
                        leaveRecord.put("approvedDate", LocalDateTime.now().format(TRAIL_DT_FMT));
                        leaveRecord.put("totalDays", approvedDays);
                        leaveRecord.put("status", "PARTIAL");
                        
                        approvedLeaves.add(leaveRecord);
                        managerLeaves.put(approvedLeavesKey, approvedLeaves);
                        managerLeave.setLeaves(managerLeaves);
                        employeeLeaveRepository.save(managerLeave);
                        
                        log.info("[SavePartiallyApprovedLeaveToManager] Saved partially approved leave id={} to manager={}, approved days={}", leaveId, managerEmail, approvedDays);
                    },
                    () -> {
                        log.warn("[SavePartiallyApprovedLeaveToManager] Manager EmployeeLeave record not found for email: {}", managerEmail);
                        createPartialManagerEmployeeLeaveRecord(managerEmail, leaveId, leave, approvedDays);
                    }
                );
            }
        });
    }

    private void addManagerReviewAuditEntry(Long leaveId, String managerName, String reviewStatus, String reason) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            
            Map<String, String> auditEntry = new HashMap<>();
            auditEntry.put("type", LeaveConstants.TYPE_MANAGER_REVIEW);
            auditEntry.put("date", LocalDateTime.now().format(TRAIL_DT_FMT));
            auditEntry.put("reviewedBy", managerName);
            auditEntry.put("stage", LeaveConstants.STAGE_MANAGER);
            auditEntry.put("reviewStatus", reviewStatus);
            auditEntry.put("employeeEmail", leave.getEmailId());
            auditEntry.put("leaveType", leave.getLeaveType());
            if (reason != null) {
                auditEntry.put("rejectionReason", reason);
            }
            
            trail.add(auditEntry);
            leave.setTrail(trail);
            leaveRepository.save(leave);
            
            log.info("[AddManagerReviewAuditEntry] Added manager review audit entry for leave id={}, status={}", leaveId, reviewStatus);
        });
    }

    private double calculateTotalDays(Leave leave) {
        if (leave.getDays() != null && !leave.getDays().isEmpty()) {
            return leave.getDays().stream()
                .mapToDouble(day -> LeaveConstants.DAY_TYPE_HALF.equals(day.getDayType()) ? 0.5 : 1.0)
                .sum();
        }
        return 1.0; 
    }

    private void createManagerEmployeeLeaveRecord(String managerEmail, Long leaveId, Leave leave) {
        try {
            com.leave_service.model.EmployeeLeave managerLeave = new com.leave_service.model.EmployeeLeave();
            managerLeave.setEmailId(managerEmail);
            managerLeave.setFullName(userServiceClient.getFullName(managerEmail));
            
            Map<String, Object> managerLeaves = new HashMap<>();
            List<Map<String, Object>> approvedLeaves = new ArrayList<>();
            
            Map<String, Object> leaveRecord = new HashMap<>();
            leaveRecord.put("leaveId", leaveId);
            leaveRecord.put("employeeEmail", leave.getEmailId());
            leaveRecord.put("leaveType", leave.getLeaveType());
            leaveRecord.put("fromDate", leave.getFromDate().toString());
            leaveRecord.put("toDate", leave.getToDate().toString());
            leaveRecord.put("approvedDate", LocalDateTime.now().format(TRAIL_DT_FMT));
            leaveRecord.put("totalDays", calculateTotalDays(leave));
            leaveRecord.put("status", "APPROVED");
            
            approvedLeaves.add(leaveRecord);
            managerLeaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
            managerLeave.setLeaves(managerLeaves);
            
            employeeLeaveRepository.save(managerLeave);
            log.info("[CreateManagerEmployeeLeaveRecord] Created new EmployeeLeave record for manager={} with approved leave id={}", managerEmail, leaveId);
        } catch (Exception e) {
            log.error("[CreateManagerEmployeeLeaveRecord] Failed to create EmployeeLeave record for manager={}: {}", managerEmail, e.getMessage());
        }
    }
    private void createPartialManagerEmployeeLeaveRecord(String managerEmail, Long leaveId, Leave leave, double approvedDays) {
        try {
            com.leave_service.model.EmployeeLeave managerLeave = new com.leave_service.model.EmployeeLeave();
            managerLeave.setEmailId(managerEmail);
            managerLeave.setFullName(userServiceClient.getFullName(managerEmail));
            
            Map<String, Object> managerLeaves = new HashMap<>();
            List<Map<String, Object>> approvedLeaves = new ArrayList<>();
            
            Map<String, Object> leaveRecord = new HashMap<>();
            leaveRecord.put("leaveId", leaveId);
            leaveRecord.put("employeeEmail", leave.getEmailId());
            leaveRecord.put("leaveType", leave.getLeaveType());
            leaveRecord.put("fromDate", leave.getFromDate().toString());
            leaveRecord.put("toDate", leave.getToDate().toString());
            leaveRecord.put("approvedDate", LocalDateTime.now().format(TRAIL_DT_FMT));
            leaveRecord.put("totalDays", approvedDays);
            leaveRecord.put("status", "PARTIAL");
            
            approvedLeaves.add(leaveRecord);
            managerLeaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
            managerLeave.setLeaves(managerLeaves);
            
            employeeLeaveRepository.save(managerLeave);
            log.info("[CreatePartialManagerEmployeeLeaveRecord] Created new EmployeeLeave record for manager={} with partial approved leave id={}, days={}", managerEmail, leaveId, approvedDays);
        } catch (Exception e) {
            log.error("[CreatePartialManagerEmployeeLeaveRecord] Failed to create EmployeeLeave record for manager={}: {}", managerEmail, e.getMessage());
        }
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
                    Map<String, Object> adminLeaves = adminLeave.getLeaves() != null ? adminLeave.getLeaves() : new HashMap<>();
                    String approvedLeavesKey = LeaveConstants.APPROVED_LEAVES_KEY;
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> approvedLeaves = (List<Map<String, Object>>) adminLeaves.getOrDefault(approvedLeavesKey, new ArrayList<>());
                    Map<String, Object> leaveRecord = new HashMap<>();
                    leaveRecord.put("leaveId", leaveId);
                    leaveRecord.put("employeeEmail", leave.getEmailId());
                    leaveRecord.put("leaveType", leave.getLeaveType());
                    leaveRecord.put("fromDate", leave.getFromDate().toString());
                    leaveRecord.put("toDate", leave.getToDate().toString());
                    leaveRecord.put("approvedDate", LocalDateTime.now().format(TRAIL_DT_FMT));
                    leaveRecord.put("totalDays", approvedDays);
                    leaveRecord.put("status", "APPROVED");
                    approvedLeaves.add(leaveRecord);
                    adminLeaves.put(approvedLeavesKey, approvedLeaves);
                    adminLeave.setLeaves(adminLeaves);
                    employeeLeaveRepository.save(adminLeave);
                    log.info("[SaveApprovedLeaveToAdmin] Saved approved leave id={} to admin={}", leaveId, adminEmail);
                },
                () -> {
                    log.warn("[SaveApprovedLeaveToAdmin] Admin EmployeeLeave record not found for email: {}", adminEmail);
                    createAdminEmployeeLeaveRecord(adminEmail, leaveId, leave, approvedDays);
                }
            );
        });
    }

    private void savePartiallyApprovedLeaveToAdmin(Long leaveId, String adminEmail, List<Map<String, String>> dayDecisions) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            double approvedDays = dayDecisions.stream()
                .filter(decision -> LeaveConstants.STATUS_APPROVED.equals(decision.get("status")))
                .mapToDouble(decision -> {
                    String dayType = decision.get("dayType");
                    return LeaveConstants.DAY_TYPE_HALF.equals(dayType) ? 0.5 : 1.0;
                })
                .sum();
            
            if (approvedDays > 0) {
                employeeLeaveRepository.findByEmailId(adminEmail).ifPresentOrElse(
                    adminLeave -> {
                        Map<String, Object> adminLeaves = adminLeave.getLeaves();
                        if (adminLeaves == null) {
                            adminLeaves = new HashMap<>();
                        }
                        
                        String approvedLeavesKey = LeaveConstants.APPROVED_LEAVES_KEY;
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> approvedLeaves = (List<Map<String, Object>>) adminLeaves.get(approvedLeavesKey);
                        if (approvedLeaves == null) {
                            approvedLeaves = new ArrayList<>();
                        }
                        
                        Map<String, Object> leaveRecord = new HashMap<>();
                        leaveRecord.put("leaveId", leaveId);
                        leaveRecord.put("employeeEmail", leave.getEmailId());
                        leaveRecord.put("leaveType", leave.getLeaveType());
                        leaveRecord.put("fromDate", leave.getFromDate().toString());
                        leaveRecord.put("toDate", leave.getToDate().toString());
                        leaveRecord.put("approvedDate", LocalDateTime.now().format(TRAIL_DT_FMT));
                        leaveRecord.put("totalDays", approvedDays);
                        leaveRecord.put("status", "PARTIAL");
                        
                        approvedLeaves.add(leaveRecord);
                        adminLeaves.put(approvedLeavesKey, approvedLeaves);
                        adminLeave.setLeaves(adminLeaves);
                        employeeLeaveRepository.save(adminLeave);
                        
                        log.info("[SavePartiallyApprovedLeaveToAdmin] Saved partially approved leave id={} to admin={}, approved days={}", leaveId, adminEmail, approvedDays);
                    },
                    () -> {
                        log.warn("[SavePartiallyApprovedLeaveToAdmin] Admin EmployeeLeave record not found for email: {}", adminEmail);
                        createPartialAdminEmployeeLeaveRecord(adminEmail, leaveId, leave, approvedDays);
                    }
                );
            }
        });
    }

    private void addAdminReviewAuditEntry(Long leaveId, String adminName, String reviewStatus, String reason) {
        leaveRepository.findById(leaveId).ifPresent(leave -> {
            List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
            
            Map<String, String> auditEntry = new HashMap<>();
            auditEntry.put("type", LeaveConstants.TYPE_ADMIN_REVIEW);
            auditEntry.put("date", LocalDateTime.now().format(TRAIL_DT_FMT));
            auditEntry.put("reviewedBy", adminName);
            auditEntry.put("stage", LeaveConstants.STAGE_ADMIN);
            auditEntry.put("reviewStatus", reviewStatus);
            auditEntry.put("employeeEmail", leave.getEmailId());
            auditEntry.put("leaveType", leave.getLeaveType());
            if (reason != null) {
                auditEntry.put("rejectionReason", reason);
            }
            
            trail.add(auditEntry);
            leave.setTrail(trail);
            leaveRepository.save(leave);
            
            log.info("[AddAdminReviewAuditEntry] Added admin review audit entry for leave id={}, status={}", leaveId, reviewStatus);
        });
    }

    private void createAdminEmployeeLeaveRecord(String adminEmail, Long leaveId, Leave leave, double approvedDays) {
        try {
            com.leave_service.model.EmployeeLeave adminLeave = new com.leave_service.model.EmployeeLeave();
            adminLeave.setEmailId(adminEmail);
            adminLeave.setFullName(userServiceClient.getFullName(adminEmail));
            
            Map<String, Object> adminLeaves = new HashMap<>();
            List<Map<String, Object>> approvedLeavesList = new ArrayList<>();
            
            Map<String, Object> leaveRecord = new HashMap<>();
            leaveRecord.put("leaveId", leaveId);
            leaveRecord.put("employeeEmail", leave.getEmailId());
            leaveRecord.put("leaveType", leave.getLeaveType());
            leaveRecord.put("fromDate", leave.getFromDate().toString());
            leaveRecord.put("toDate", leave.getToDate().toString());
            leaveRecord.put("approvedDate", LocalDateTime.now().format(TRAIL_DT_FMT));
            leaveRecord.put("totalDays", approvedDays);
            leaveRecord.put("status", "APPROVED");
            
            approvedLeavesList.add(leaveRecord);
            adminLeaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeavesList);
            adminLeave.setLeaves(adminLeaves);
            
            employeeLeaveRepository.save(adminLeave);
            log.info("[CreateAdminEmployeeLeaveRecord] Created new EmployeeLeave record for admin={} with approved leave id={}", adminEmail, leaveId);
        } catch (Exception e) {
            log.error("[CreateAdminEmployeeLeaveRecord] Failed to create EmployeeLeave record for admin={}: {}", adminEmail, e.getMessage());
        }
    }

    private void createPartialAdminEmployeeLeaveRecord(String adminEmail, Long leaveId, Leave leave, double approvedDays) {
        try {
            com.leave_service.model.EmployeeLeave adminLeave = new com.leave_service.model.EmployeeLeave();
            adminLeave.setEmailId(adminEmail);
            adminLeave.setFullName(userServiceClient.getFullName(adminEmail));
            
            Map<String, Object> adminLeaves = new HashMap<>();
            List<Map<String, Object>> approvedLeaves = new ArrayList<>();
            
            Map<String, Object> leaveRecord = new HashMap<>();
            leaveRecord.put("leaveId", leaveId);
            leaveRecord.put("employeeEmail", leave.getEmailId());
            leaveRecord.put("leaveType", leave.getLeaveType());
            leaveRecord.put("fromDate", leave.getFromDate().toString());
            leaveRecord.put("toDate", leave.getToDate().toString());
            leaveRecord.put("approvedDate", LocalDateTime.now().format(TRAIL_DT_FMT));
            leaveRecord.put("totalDays", approvedDays);
            leaveRecord.put("status", "PARTIAL");
            
            approvedLeaves.add(leaveRecord);
            adminLeaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approvedLeaves);
            adminLeave.setLeaves(adminLeaves);
            
            employeeLeaveRepository.save(adminLeave);
            log.info("[CreatePartialAdminEmployeeLeaveRecord] Created new EmployeeLeave record for admin={} with partial approved leave id={}, days={}", adminEmail, leaveId, approvedDays);
        } catch (Exception e) {
            log.error("[CreatePartialAdminEmployeeLeaveRecord] Failed to create EmployeeLeave record for admin={}: {}", adminEmail, e.getMessage());
        }
    }
}

