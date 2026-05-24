package com.leave_service.service.impl;

import com.leave_service.client.UserServiceClient;
import com.leave_service.commons.LeaveConstants;
import com.leave_service.dto.LeaveDayEntry;
import com.leave_service.model.EmployeeLeave;
import com.leave_service.model.Leave;
import com.leave_service.repository.EmployeeLeaveRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.service.AsyncMailSender;
import com.leave_service.service.LeaveWorkflowService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Branch coverage tests for LeaveProcessServiceImpl targeting directApprove,
 * directReject, and directPartialReview missing branches.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveProcessServiceImplBranchCoverageTest {

    @Mock private RuntimeService runtimeService;
    @Mock private TaskService taskService;
    @Mock private LeaveRepository leaveRepository;
    @Mock private EmployeeLeaveRepository employeeLeaveRepository;
    @Mock private AsyncMailSender asyncMailSender;
    @Mock private UserServiceClient userServiceClient;
    @Mock private LeaveWorkflowService leaveWorkflowService;
    @InjectMocks private LeaveProcessServiceImpl leaveProcessService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(leaveProcessService, "adminEmail", "admin@test.com");

        // Default: no active Flowable tasks → direct methods called
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.processVariableValueEquals(anyString(), any())).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null); // no task
    }

    private Leave createLeave(String emailId) {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId(emailId);
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        return leave;
    }

    // ── directApprove: manager approves admin's own leave (isAdminLeave=true) ─

    @Test
    void directApprove_managerReviewsAdminOwnLeave_isAdminLeave() {
        // reviewer = manager@test.com → STAGE_MANAGER
        // leave.emailId = admin@test.com → isAdminLeave = true
        Leave leave = createLeave("admin@test.com"); // admin's own leave

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());

        leaveProcessService.approveLeave(1L, "manager@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void directApprove_managerReviewsAdminOwnLeave_withBalance() {
        // isAdminLeave=true, empLeave found with balance
        Leave leave = createLeave("admin@test.com");

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com");
        Map<String, Object> balances = new HashMap<>();
        balances.put("Sick", 10.0);
        empLeave.setLeaves(balances);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.approveLeave(1L, "manager@test.com");

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void directApprove_managerReviewsRegularEmployee_notAdminLeave() {
        // reviewer = manager@test.com → STAGE_MANAGER
        // leave.emailId = emp@test.com → isAdminLeave = false
        Leave leave = createLeave("emp@test.com");

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("manager@test.com")).thenReturn(Optional.empty());

        leaveProcessService.approveLeave(1L, "manager@test.com");

        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    @Test
    void directApprove_adminReviewsWithDaysHavingRejectedStatus() {
        // reviewer = admin@test.com → STAGE_ADMIN
        // Days has one REJECTED day → !STATUS_REJECTED = false → skip
        Leave leave = createLeave("emp@test.com");

        LeaveDayEntry rejectedDay = new LeaveDayEntry();
        rejectedDay.setDate("2025-06-01");
        rejectedDay.setStatus(LeaveConstants.STATUS_REJECTED);

        LeaveDayEntry pendingDay = new LeaveDayEntry();
        pendingDay.setDate("2025-06-02");
        pendingDay.setStatus(LeaveConstants.STATUS_PENDING);

        leave.setDays(new ArrayList<>(List.of(rejectedDay, pendingDay)));

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("emp@test.com");
        Map<String, Object> balances = new HashMap<>();
        balances.put("Sick", 10.0);
        empLeave.setLeaves(balances);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void directApprove_trailEmpty_usesDefaultDayType() {
        // leave.trail is empty → dayType = DAY_TYPE_FULL (trail.isEmpty() = true)
        Leave leave = createLeave("emp@test.com");
        leave.setTrail(new ArrayList<>()); // empty trail

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.approveLeave(1L, "manager@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
        // Verify the trail entry has FULL_DAY type
        List<Map<String, String>> trail = leave.getTrail();
        boolean hasFullDay = trail.stream()
            .anyMatch(e -> LeaveConstants.DAY_TYPE_FULL.equals(e.get(LeaveConstants.TRAIL_DAY_TYPE)));
        assertTrue(hasFullDay);
    }

    @Test
    void directApprove_reviewedByNull_usesSystemUser() {
        // reviewedBy = null → reviewerFullName = userServiceClient.getFullName(SYSTEM_USER)
        Leave leave = createLeave("emp@test.com");
        leave.setTrail(new ArrayList<>());

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(LeaveConstants.SYSTEM_USER)).thenReturn("System");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.approveLeave(1L, null);

        verify(userServiceClient).getFullName(LeaveConstants.SYSTEM_USER);
    }

    @Test
    void directApprove_adminReviewsWithNullDays_skipsLoop() {
        // STAGE_ADMIN, leave.days = null → skip forEach loop
        Leave leave = createLeave("emp@test.com");
        leave.setDays(null);

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("emp@test.com");
        Map<String, Object> balances = new HashMap<>();
        balances.put("Sick", 10.0);
        empLeave.setLeaves(balances);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ── directApprove: admin with leave balance null map ──────────────────────

    @Test
    void directApprove_adminStage_balanceMapNull() {
        // empLeave.getLeaves() returns null → skip balance update
        Leave leave = createLeave("emp@test.com");

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("emp@test.com");
        empLeave.setLeaves(null); // null balances

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());

        // Should complete without error
        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void directApprove_adminStage_leaveTypeNotInBalanceMap() {
        // empLeave.getLeaves() doesn't have the leave type → balance update skipped
        Leave leave = createLeave("emp@test.com");

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("emp@test.com");
        Map<String, Object> balances = new HashMap<>();
        balances.put("Casual", 10.0); // Different type

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ── directReject: manager vs admin stage ─────────────────────────────────

    @Test
    void directReject_managerStage_sendsRejectionMail() {
        // reviewer = manager → STAGE_MANAGER
        Leave leave = createLeave("emp@test.com");

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "manager@test.com", "Not approved");

        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    @Test
    void directReject_adminStage_sendsRejectionMail() {
        // reviewer = admin → STAGE_ADMIN
        Leave leave = createLeave("emp@test.com");

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "admin@test.com", "Policy violation");

        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    @Test
    void directReject_nullReason_noReason() {
        Leave leave = createLeave("emp@test.com");

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "manager@test.com", null);

        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    // ── directPartialReview: admin stage variations ──────────────────────────

    @Test
    void directPartialReview_adminStage_allRejected() {
        Leave leave = createLeave("emp@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        leave.setDays(new ArrayList<>(List.of(day)));

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_REJECTED, "reason", "Policy")
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.empty());

        leaveProcessService.directPartialReview(1L, "admin@test.com", dayDecisions);

        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    @Test
    void directPartialReview_adminStage_allApproved_withBalance() {
        Leave leave = createLeave("emp@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day)));

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("emp@test.com");
        Map<String, Object> balances = new HashMap<>();
        balances.put("Sick", 10.0);
        empLeave.setLeaves(balances);

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_APPROVED)
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.directPartialReview(1L, "admin@test.com", dayDecisions);

        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    @Test
    void directPartialReview_managerStage_adminLeave_allApproved() {
        // isAdminLeave = true → admin's own leave, manager reviews
        Leave leave = createLeave("admin@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day)));

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com");
        Map<String, Object> balances = new HashMap<>();
        balances.put("Sick", 10.0);
        empLeave.setLeaves(balances);

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_APPROVED)
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.directPartialReview(1L, "manager@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void directPartialReview_managerStage_adminLeave_allRejected_noBalanceUpdate() {
        // isAdminLeave = true + allRejected → no balance update, just mail
        Leave leave = createLeave("admin@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day)));

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_REJECTED, "reason", "Denied")
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());

        leaveProcessService.directPartialReview(1L, "manager@test.com", dayDecisions);

        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    @Test
    void directPartialReview_managerStage_regularEmployee_mixedDecisions() {
        // Mixed decisions for regular employee → notify employee + admin
        Leave leave = createLeave("emp@test.com");
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1, day2)));

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_APPROVED),
            Map.of("date", "2025-06-02", "status", LeaveConstants.STATUS_REJECTED, "reason", "Holiday conflict")
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.directPartialReview(1L, "manager@test.com", dayDecisions);

        // Should send to employee (approved + rejected) and admin (approved)
        verify(asyncMailSender, atLeast(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void directPartialReview_managerStage_nullDays_skipsLoop() {
        // leave.days = null → skip day processing
        Leave leave = createLeave("emp@test.com");
        leave.setDays(null);

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_APPROVED)
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.directPartialReview(1L, "manager@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ── directManagerPartialNotify: approved/rejected combinations ────────────

    @Test
    void directManagerPartialNotify_withApprovedAndRejectedDays() {
        Leave leave = createLeave("emp@test.com");
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1, day2)));

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_APPROVED),
            Map.of("date", "2025-06-02", "status", LeaveConstants.STATUS_REJECTED, "reason", "Office req")
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.directManagerPartialNotify(1L, "manager@test.com", dayDecisions, "admin@test.com");

        // Should send to employee (approved days + rejected days) and admin (approved days)
        verify(asyncMailSender, atLeast(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void directManagerPartialNotify_onlyApprovedDays() {
        Leave leave = createLeave("emp@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day)));

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_APPROVED)
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.directManagerPartialNotify(1L, "manager@test.com", dayDecisions, "admin@test.com");

        // Employee gets approved message + admin gets action required
        verify(asyncMailSender, atLeast(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void directManagerPartialNotify_onlyRejectedDays() {
        Leave leave = createLeave("emp@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day)));

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_REJECTED, "reason", "Not allowed")
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.directManagerPartialNotify(1L, "manager@test.com", dayDecisions, "admin@test.com");

        // Only employee gets rejected message
        verify(asyncMailSender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void directManagerPartialNotify_nullDays_noUpdate() {
        // leave.days = null → skip day status update
        Leave leave = createLeave("emp@test.com");
        leave.setDays(null);

        List<Map<String, String>> dayDecisions = List.of(
            Map.of("date", "2025-06-01", "status", LeaveConstants.STATUS_APPROVED)
        );

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName("manager@test.com")).thenReturn("Manager");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.directManagerPartialNotify(1L, "manager@test.com", dayDecisions, "admin@test.com");

        verify(leaveRepository, never()).save(any());
    }
}
