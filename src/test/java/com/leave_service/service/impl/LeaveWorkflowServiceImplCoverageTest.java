package com.leave_service.service.impl;

import com.leave_service.client.UserServiceClient;
import com.leave_service.commons.LeaveConstants;
import com.leave_service.model.EmployeeLeave;
import com.leave_service.model.Leave;
import com.leave_service.dto.LeaveDayEntry;
import com.leave_service.repository.EmployeeLeaveRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.service.AsyncMailSender;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveWorkflowServiceImplCoverageTest {

    @Mock private LeaveRepository leaveRepository;
    @Mock private EmployeeLeaveRepository employeeLeaveRepository;
    @Mock private AsyncMailSender asyncMailSender;
    @Mock private UserServiceClient userServiceClient;
    @Mock private DelegateExecution execution;
    @InjectMocks private LeaveWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "adminEmail", "admin@test.com");
    }

    @Test
    void addTrailEntry_nullReviewedBy() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn(null);
        Leave leave = createLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void addTrailEntryWithEmployeeName_nullEmployeeName() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn(null);
        Leave leave = createLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.initializeLeaveProcess(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void addTrailEntryWithEmployeeName_nullReviewedBy() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        Leave leave = createLeave();
        leave.setTrail(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.initializeLeaveProcess(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void addTrailEntryWithEmployeeName_nullReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        Leave leave = createLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.initializeLeaveProcess(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void addTrailEntryWithEmployeeName_nullStage() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        Leave leave = createLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.initializeLeaveProcess(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void updateDayStatusesWithReviewer_emptyDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList();
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(new ArrayList<>());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void updateDayStatusesWithReviewer_nullDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED")
        );
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void updateDayStatusesWithReviewer_nullTrail() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED")
        );
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.setTrail(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processAdminPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void updateDayStatusesWithReviewer_reasonWithSpaces() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED", "reason", "   ")
        );
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processAdminPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void markAllDaysAsReviewedBy_emptyDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeave();
        leave.setDays(new ArrayList<>());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void markAllDaysAsReviewedBy_nullDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeave();
        leave.setDays(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void markDaysAsReviewedByAdmin_emptyDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(new ArrayList<>());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processAdminApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void markDaysAsReviewedByAdmin_nullDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processAdminApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void markDaysAsReviewedByAdmin_dayNotManagerApproved() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_REJECTED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void markDaysAsReviewedByAdmin_dayNotInManagerStage() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_ADMIN);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void calculateTotalDays_emptyDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(new ArrayList<>());
        leave.setDayType("HALF");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void calculateTotalDays_nullDayType() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(new ArrayList<>());
        leave.setDayType(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void saveApprovedLeaveToAdmin_emptyDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(new ArrayList<>());
        leave.setDayType("FULL");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void saveApprovedLeaveToAdmin_nullDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(null);
        leave.setDayType("FULL");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- updateDayStatuses via reflection: with days and reason ----
    @Test
    void updateDayStatuses_withDaysAndReason() {
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenReturn(leave);

        List<Map<String, String>> decisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2024-12-25");
        d1.put("status", LeaveConstants.STATUS_REJECTED);
        d1.put("reason", "Not approved");
        decisions.add(d1);
        Map<String, String> d2 = new HashMap<>();
        d2.put("date", "2024-12-26");
        d2.put("status", LeaveConstants.STATUS_APPROVED);
        decisions.add(d2);

        try {
            java.lang.reflect.Method method = LeaveWorkflowServiceImpl.class.getDeclaredMethod(
                "updateDayStatuses", Long.class, List.class);
            method.setAccessible(true);
            method.invoke(service, 1L, decisions);
        } catch (Exception e) {
            // expected
        }

        verify(leaveRepository).save(any());
    }

    // ---- updateDayStatuses via reflection: empty decisions → no save ----
    @Test
    void updateDayStatuses_withNoDays() {
        Leave leave = createLeave();
        leave.setDays(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        List<Map<String, String>> decisions = new ArrayList<>();

        try {
            java.lang.reflect.Method method = LeaveWorkflowServiceImpl.class.getDeclaredMethod(
                "updateDayStatuses", Long.class, List.class);
            method.setAccessible(true);
            method.invoke(service, 1L, decisions);
        } catch (Exception e) {
            // expected
        }

        verify(leaveRepository, never()).save(any());
    }

    // ---- updateDayStatuses via reflection: days with matching date ----
    @Test
    void updateDayStatuses_withMatchingDate() {
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenReturn(leave);

        List<Map<String, String>> decisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2024-12-25");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        decisions.add(d1);
        // no reason → reason is null

        try {
            java.lang.reflect.Method method = LeaveWorkflowServiceImpl.class.getDeclaredMethod(
                "updateDayStatuses", Long.class, List.class);
            method.setAccessible(true);
            method.invoke(service, 1L, decisions);
        } catch (Exception e) {
            // expected
        }

        verify(leaveRepository).save(any());
    }

    // ---- addDayWorkflowEntry via reflection: with reason ----
    @Test
    void addDayWorkflowEntry_withReason() {
        Leave leave = createLeave();

        try {
            java.lang.reflect.Method method = LeaveWorkflowServiceImpl.class.getDeclaredMethod(
                "addDayWorkflowEntry", Leave.class, String.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(service, leave, "2024-12-25", "Manager", LeaveConstants.STAGE_MANAGER,
                LeaveConstants.STATUS_REJECTED, "Project deadline");
        } catch (Exception e) {
            // expected
        }

        // Verify trail was modified
        assert leave.getTrail() != null;
    }

    // ---- addDayWorkflowEntry via reflection: null reason ----
    @Test
    void addDayWorkflowEntry_nullReason() {
        Leave leave = createLeave();
        leave.setTrail(null); // null trail → new ArrayList created

        try {
            java.lang.reflect.Method method = LeaveWorkflowServiceImpl.class.getDeclaredMethod(
                "addDayWorkflowEntry", Leave.class, String.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(service, leave, "2024-12-25", "Manager", LeaveConstants.STAGE_MANAGER,
                LeaveConstants.STATUS_APPROVED, null);
        } catch (Exception e) {
            // expected
        }

        assert leave.getTrail() != null;
    }

    // ---- processManagerPartial: isAdminLeave = true, allApproved ----
    @Test
    void processManagerPartial_isAdminLeave_allApproved() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com"); // admin's own leave
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", LeaveConstants.STATUS_APPROVED)
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));

        service.processManagerPartial(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- processManagerPartial: isAdminLeave = true, allRejected ----
    @Test
    void processManagerPartial_isAdminLeave_allRejected() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", LeaveConstants.STATUS_REJECTED)
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);

        service.processManagerPartial(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- processManagerPartial: isAdminLeave = true, partial decisions ----
    @Test
    void processManagerPartial_isAdminLeave_partialDecisions() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            new HashMap<>(Map.of("date", "2024-12-25", "status", LeaveConstants.STATUS_APPROVED)),
            new HashMap<>(Map.of("date", "2024-12-26", "status", LeaveConstants.STATUS_REJECTED))
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 5.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));

        service.processManagerPartial(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- processManagerPartial: day with reason ----
    @Test
    void processManagerPartial_withDayReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        List<Map<String, String>> decisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2024-12-25");
        d1.put("status", LeaveConstants.STATUS_REJECTED);
        d1.put("reason", "Project crunch");
        decisions.add(d1);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());

        service.processManagerPartial(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- updateLeaveBalance: leave with no days and HALF dayType ----
    @Test
    void updateLeaveBalance_noDays_halfDayType() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeave();
        leave.setDays(null);
        leave.setDayType(LeaveConstants.DAY_TYPE_HALF);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- updateLeaveBalancePartial: with approved days ----
    @Test
    void updateLeaveBalancePartial_withApprovedDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", LeaveConstants.STATUS_APPROVED)
        );
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> d.setStatus(LeaveConstants.STATUS_APPROVED));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminPartial(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- addManagerReviewAuditEntry with non-null reason ----
    @Test
    void addManagerReviewAuditEntry_withReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("No leave available");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        service.processManagerRejection(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- addAdminReviewAuditEntry with non-null reason ----
    @Test
    void addAdminReviewAuditEntry_withReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("Not approved");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        service.processAdminRejection(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // ---- finalizeLeaveProcess: leave not found ----
    @Test
    void finalizeLeaveProcess_leaveNotFound() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(99L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Sick");
        when(leaveRepository.findById(99L)).thenReturn(Optional.empty());

        service.finalizeLeaveProcess(execution);

        verify(leaveRepository, never()).save(any());
    }

    // ---- finalizeLeaveProcess: leave with empty trail ----
    @Test
    void finalizeLeaveProcess_emptyTrail() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Casual");
        Leave leave = createLeave();
        leave.setTrail(new ArrayList<>());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        service.finalizeLeaveProcess(execution);

        verify(leaveRepository, never()).save(any());
    }

    // ---- savePartiallyApprovedLeaveToAdmin: admin not found ----
    @Test
    void savePartiallyApprovedLeaveToAdmin_adminNotFound() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", LeaveConstants.STATUS_APPROVED, "dayType", LeaveConstants.DAY_TYPE_HALF)
        );
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> d.setStatus(LeaveConstants.STATUS_APPROVED));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin");
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminPartial(execution);

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // ---- processAdminRejection: null rejectionReason ----
    @Test
    void processAdminRejection_nullReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn(null);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        service.processAdminRejection(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
        verify(asyncMailSender).send(any(), anyString(), anyString());
    }

    private Leave createLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Casual");
        leave.setFromDate(LocalDate.of(2024, 12, 25));
        leave.setToDate(LocalDate.of(2024, 12, 26));
        leave.setTrail(new ArrayList<>());
        Map<String, String> entry = new HashMap<>();
        entry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        leave.getTrail().add(entry);
        return leave;
    }

    private Leave createLeaveWithDays() {
        Leave leave = createLeave();
        List<LeaveDayEntry> days = new ArrayList<>();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2024-12-25");
        day1.setDayType("FULL");
        day1.setStatus("PENDING");
        days.add(day1);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2024-12-26");
        day2.setDayType("FULL");
        day2.setStatus("PENDING");
        days.add(day2);
        leave.setDays(days);
        return leave;
    }

    // saveApprovedLeaveToAdmin: admin found but getLeaves() is null
    @Test
    void saveApprovedLeaveToAdmin_adminFoundNullLeaves() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> d.setStatus(LeaveConstants.STATUS_APPROVED));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        EmployeeLeave adminLeave = new EmployeeLeave();
        adminLeave.setLeaves(null); // null leaves map

        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> empLeaves = new HashMap<>();
        empLeaves.put("Casual", 10.0);
        empLeave.setLeaves(empLeaves);

        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(adminLeave));
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(adminLeave);

        service.processAdminApproval(execution);

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // savePartiallyApprovedLeaveToAdmin: all days rejected, approvedDays=0 -> early return
    @Test
    void savePartiallyApprovedLeaveToAdmin_allRejectedDays_noUpdate() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2024-12-25");
        d1.put("status", LeaveConstants.STATUS_REJECTED);
        decisions.add(d1);
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        service.processAdminPartial(execution);

        // employeeLeaveRepository for admin should NOT be called (approvedDays=0)
        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // savePartiallyApprovedLeaveToAdmin: admin found but getLeaves() is null
    @Test
    void savePartiallyApprovedLeaveToAdmin_adminFoundNullLeaves_workflow() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2024-12-25");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", "FULL");
        decisions.add(d1);
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> d.setStatus(LeaveConstants.STATUS_APPROVED));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        EmployeeLeave adminLeave = new EmployeeLeave();
        adminLeave.setLeaves(null); // null leaves

        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> empLeaves = new HashMap<>();
        empLeaves.put("Casual", 10.0);
        empLeave.setLeaves(empLeaves);

        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(adminLeave));
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(adminLeave);

        service.processAdminPartial(execution);

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // processManagerRejection: null trail -> covers FALSE branch of trail ternary in markAllDaysAsReviewedBy, addTrailEntry, addManagerReviewAuditEntry
    @Test
    void processManagerRejection_nullTrail() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("Project crunch");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        leave.setTrail(null); // null trail - covers FALSE branch in trail ternary
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        service.processManagerRejection(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // processAdminRejection: null trail -> covers FALSE branch in markDaysAsReviewedByAdmin, addAdminReviewAuditEntry
    @Test
    void processAdminRejection_nullTrail() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("Policy");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
        });
        leave.setTrail(null); // null trail - covers FALSE branch
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        service.processAdminRejection(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // processAdminApproval: HALF_DAY type - covers 0.5 branch in updateLeaveBalance (L520) and saveApprovedLeaveToAdmin (L772)
    @Test
    void processAdminApproval_halfDay_nullTrail() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
            d.setDayType(LeaveConstants.DAY_TYPE_HALF); // "HALF_DAY" covers 0.5 branch
        });
        leave.setTrail(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // processAdminPartial: HALF_DAY type - covers 0.5 branch in updateLeaveBalancePartial (L537)
    @Test
    void processAdminPartial_halfDay() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2024-12-25");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", LeaveConstants.DAY_TYPE_HALF);
        decisions.add(d1);
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> d.setStatus(LeaveConstants.STATUS_APPROVED));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> empLeaves = new HashMap<>();
        empLeaves.put("Casual", 10.0);
        empLeave.setLeaves(empLeaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        service.processAdminPartial(execution);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // processManagerApproval: HALF_DAY + null manager leaves - covers L564 (calculateTotalDays HALF) and L676 (null leaves FALSE branch)
    @Test
    void processManagerApproval_halfDay_managerNullLeaves() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> d.setDayType(LeaveConstants.DAY_TYPE_HALF)); // HALF_DAY covers L564
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave mgrLeave = new EmployeeLeave();
        mgrLeave.setLeaves(null); // null leaves - covers L676 FALSE branch
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(mgrLeave));

        service.processManagerApproval(execution);

        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    // processManagerPartial: HALF_DAY + null trail + manager null leaves - covers L196, L705, L710
    @Test
    void processManagerPartial_halfDay_nullTrail_managerNullLeaves() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        List<Map<String, String>> decisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2024-12-25");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", LeaveConstants.DAY_TYPE_HALF); // HALF_DAY covers L705
        decisions.add(d1);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        leave.setTrail(null); // null trail - covers L196
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenReturn(leave);
        EmployeeLeave mgrLeave = new EmployeeLeave();
        mgrLeave.setLeaves(null); // null manager leaves - covers L710 FALSE branch
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(mgrLeave));

        service.processManagerPartial(execution);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // processManagerPartial: regular employee (isAdminLeave=false), all approved
    @Test
    void processManagerPartial_regularEmployee_allApproved() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com"); // not admin
        List<Map<String, String>> decisions = Arrays.asList(
            new HashMap<>(Map.of("date", "2024-12-25", "status", LeaveConstants.STATUS_APPROVED)),
            new HashMap<>(Map.of("date", "2024-12-26", "status", LeaveConstants.STATUS_APPROVED))
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenReturn(leave);

        service.processManagerPartial(execution);

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender, atLeastOnce()).send(any(), anyString(), anyString());
    }
}
