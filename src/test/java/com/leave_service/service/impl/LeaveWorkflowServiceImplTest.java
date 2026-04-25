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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveWorkflowServiceImplTest {

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
    void initializeLeaveProcess_success() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee Name");
        Leave leave = new Leave();
        leave.setTrail(new ArrayList<>());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.initializeLeaveProcess(execution);
        
        verify(execution).setVariable("adminRequired", true);
        verify(leaveRepository).save(any(Leave.class));
    }

    @Test
    void scheduleReminder_validDate() {
        when(execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW)).thenReturn("2024-12-25");
        
        service.scheduleReminder(execution);
        
        verify(execution).setVariable(eq("reminderFireTime"), anyString());
    }

    @Test
    void scheduleReminder_invalidDate() {
        when(execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW)).thenReturn("invalid-date");
        
        service.scheduleReminder(execution);
        
        verify(execution, never()).setVariable(eq("reminderFireTime"), anyString());
    }

    @Test
    void sendReminderEmail_success() {
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_EMAIL)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Sick");
        when(execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW)).thenReturn("2024-12-25");
        when(execution.getVariable(LeaveConstants.VAR_TO_DATE_RAW)).thenReturn("2024-12-26");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        
        service.sendReminderEmail(execution);
        
        verify(asyncMailSender).send(eq("emp@test.com"), anyString(), anyString());
    }

    @Test
    void processManagerPartial_noApprovedDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "REJECTED")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processManagerPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminPartial_noApprovedDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "REJECTED")
        );
        when(execution.getVariable(LeaveConstants.VAR_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processManagerApproval_withHalfDay() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        leave.getDays().get(0).setDayType("HALF");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminApproval_withHalfDay() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
            d.setDayType("HALF");
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
    void processManagerApproval_noDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeave();
        leave.setDays(null);
        leave.setDayType("HALF");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminApproval_noDays() {
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

    @Test
    void processManagerRejection_withReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("Insufficient notice");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processManagerRejection(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminRejection_withReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("Policy violation");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminRejection(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processManagerPartial_withReasons() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED", "reason", "OK"),
            Map.of("date", "2024-12-26", "status", "REJECTED", "reason", "Busy day")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminPartial_withReasons() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED", "reason", "Approved"),
            Map.of("date", "2024-12-26", "status", "REJECTED", "reason", "Not allowed")
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
    void processAdminApproval_noManagerApprovedDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_REJECTED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminRejection_noManagerApprovedDays() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("Rejected");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_REJECTED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminRejection(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void finalizeLeaveProcess_emptyTrail() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Casual");
        Leave leave = createLeave();
        leave.setTrail(new ArrayList<>());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.finalizeLeaveProcess(execution);
        
        verify(leaveRepository).findById(1L);
    }

    @Test
    void finalizeLeaveProcess_noLeave() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Casual");
        when(leaveRepository.findById(1L)).thenReturn(Optional.empty());
        
        service.finalizeLeaveProcess(execution);
        
        verify(leaveRepository).findById(1L);
    }

    @Test
    void processManagerApproval_noBalanceForLeaveType() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeave();
        leave.setEmailId("admin@test.com");
        leave.setLeaveType("Unpaid");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerApproval(execution);
        
        verify(execution).setVariable("adminRequired", false);
    }

    @Test
    void processManagerApproval_noEmployeeLeave() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeave();
        leave.setEmailId("admin@test.com");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        
        service.processManagerApproval(execution);
        
        verify(execution).setVariable("adminRequired", false);
    }

    @Test
    void notifyManager_success() {
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_EMAIL)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Casual");
        when(execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW)).thenReturn("2024-12-25");
        when(execution.getVariable(LeaveConstants.VAR_TO_DATE_RAW)).thenReturn("2024-12-26");
        when(execution.getVariable(LeaveConstants.VAR_REASON)).thenReturn("Personal");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        
        service.notifyManager(execution);
        
        verify(asyncMailSender).send(eq("mgr@test.com"), anyString(), anyString());
    }

    @Test
    void notifyAdmin_success() {
        when(execution.getVariable(LeaveConstants.VAR_ADMIN_EMAIL)).thenReturn("admin@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Annual");
        when(execution.getVariable(LeaveConstants.VAR_FROM_DATE_RAW)).thenReturn("2024-12-25");
        when(execution.getVariable(LeaveConstants.VAR_TO_DATE_RAW)).thenReturn("2024-12-26");
        when(userServiceClient.getFullName("emp@test.com")).thenReturn("Employee");
        
        service.notifyAdmin(execution);
        
        verify(asyncMailSender).send(eq("admin@test.com"), anyString(), anyString());
    }

    @Test
    void processManagerApproval_regularEmployee() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerApproval(execution);
        
        verify(execution).setVariable("adminRequired", true);
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processManagerApproval_adminLeave() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeave();
        leave.setEmailId("admin@test.com");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerApproval(execution);
        
        verify(execution).setVariable("adminRequired", false);
    }

    @Test
    void processManagerPartial_regularEmployee() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED"),
            Map.of("date", "2024-12-26", "status", "REJECTED", "reason", "Not available")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processManagerPartial_adminLeave() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        leave.setEmailId("admin@test.com");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerPartial(execution);
        
        verify(execution).setVariable("adminRequired", false);
    }

    @Test
    void processManagerRejection_success() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("Not approved");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processManagerRejection(execution);
        
        verify(execution).setVariable("adminRequired", false);
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminApproval_success() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
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
    void processAdminPartial_success() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED"),
            Map.of("date", "2024-12-26", "status", "REJECTED")
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
    void processAdminRejection_success() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn("Rejected");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminRejection(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void finalizeLeaveProcess_success() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Casual");
        Leave leave = createLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.finalizeLeaveProcess(execution);
        
        verify(leaveRepository).findById(1L);
    }

    @Test
    void processManagerApproval_newManagerRecord() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        
        service.processManagerApproval(execution);
        
        verify(employeeLeaveRepository).save(any(EmployeeLeave.class));
    }

    @Test
    void processManagerPartial_newManagerRecord() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        
        service.processManagerPartial(execution);
        
        verify(employeeLeaveRepository).save(any(EmployeeLeave.class));
    }

    @Test
    void processAdminApproval_newAdminRecord() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        
        service.processAdminApproval(execution);
        
        verify(employeeLeaveRepository, atLeastOnce()).save(any(EmployeeLeave.class));
    }

    @Test
    void processAdminPartial_newAdminRecord() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED")
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
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        
        service.processAdminPartial(execution);
        
        verify(employeeLeaveRepository, atLeastOnce()).save(any(EmployeeLeave.class));
    }

    @Test
    void processManagerPartial_allRejected() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "REJECTED"),
            Map.of("date", "2024-12-26", "status", "REJECTED")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        leave.setEmailId("admin@test.com");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processManagerPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processManagerPartial_halfDayApproved() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED", "dayType", "HALF")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminPartial_halfDayApproved() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED", "dayType", "HALF")
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

    @Test
    void processManagerRejection_nullReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn(null);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processManagerRejection(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminRejection_nullReason() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(execution.getVariable(LeaveConstants.VAR_REJECTION_REASON)).thenReturn(null);
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.processAdminRejection(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processManagerPartial_emptyReasons() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED", "reason", "")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerPartial(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void processAdminPartial_emptyReasons() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED", "reason", "")
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
    void processManagerPartial_allApproved() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_REVIEWED_BY)).thenReturn("mgr@test.com");
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("admin@test.com");
        List<Map<String, String>> decisions = Arrays.asList(
            Map.of("date", "2024-12-25", "status", "APPROVED"),
            Map.of("date", "2024-12-26", "status", "APPROVED")
        );
        when(execution.getVariable(LeaveConstants.VAR_MANAGER_DAY_DECISIONS)).thenReturn(decisions);
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager");
        Leave leave = createLeaveWithDays();
        leave.setEmailId("admin@test.com");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Casual", 10.0);
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processManagerPartial(execution);
        
        verify(execution).setVariable("adminRequired", false);
    }



    @Test
    void updateEmployeeBalance_noLeaveType() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.setLeaveType("Unpaid");
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
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
    void updateEmployeeBalance_nullBalances() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_REVIEWED_BY)).thenReturn("admin@test.com");
        when(userServiceClient.getFullName("admin@test.com")).thenReturn("Admin");
        Leave leave = createLeaveWithDays();
        leave.getDays().forEach(d -> {
            d.setReviewStage(LeaveConstants.STAGE_MANAGER);
            d.setStatus(LeaveConstants.STATUS_APPROVED);
        });
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(null);
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        
        service.processAdminApproval(execution);
        
        verify(leaveRepository, atLeastOnce()).save(any(Leave.class));
    }

    @Test
    void finalizeLeaveProcess_nullTrail() {
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_ID)).thenReturn(1L);
        when(execution.getVariable(LeaveConstants.VAR_EMPLOYEE_EMAIL)).thenReturn("emp@test.com");
        when(execution.getVariable(LeaveConstants.VAR_LEAVE_TYPE)).thenReturn("Casual");
        Leave leave = createLeave();
        leave.setTrail(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        service.finalizeLeaveProcess(execution);
        
        verify(leaveRepository).findById(1L);
    }
}
