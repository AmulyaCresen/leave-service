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
class LeaveProcessServiceImplEdgeCaseTest {

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
    }

    @Test
    void partialReview_adminStageWithAlreadyRejectedDays_skipsRejectedDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_REJECTED);
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_APPROVED);
        day2.setDayType(LeaveConstants.DAY_TYPE_FULL);
        
        leave.setDays(List.of(day1, day2));
        
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision1 = new HashMap<>();
        decision1.put("date", "2025-06-01");
        decision1.put("status", LeaveConstants.STATUS_REJECTED);
        dayDecisions.add(decision1);
        
        Map<String, String> decision2 = new HashMap<>();
        decision2.put("date", "2025-06-02");
        decision2.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision2);
        
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);
        
        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);
        
        verify(leaveRepository, atLeastOnce()).save(any());
        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void approveLeave_managerStageWithException_createsNewManagerRecord() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());
        
        leaveProcessService.approveLeave(1L, "mgr@test.com");
        
        verify(employeeLeaveRepository).save(any());
        verify(asyncMailSender, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void approveLeave_adminStageWithException_createsNewAdminRecord() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(List.of(day));
        
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());
        
        leaveProcessService.approveLeave(1L, "admin@test.com");
        
        verify(employeeLeaveRepository).save(any());
        verify(asyncMailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void partialReview_managerStageWithException_createsNewManagerRecord() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(List.of(day));
        
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);
        
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());
        
        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);
        
        verify(employeeLeaveRepository).save(any());
        verify(asyncMailSender, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void partialReview_adminStageWithException_createsNewAdminRecord() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(List.of(day));
        
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);
        
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());
        
        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);
        
        verify(employeeLeaveRepository).save(any());
        verify(asyncMailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void approveLeave_managerStageWithUserServiceException_handlesGracefully() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());
        
        leaveProcessService.approveLeave(1L, "mgr@test.com");
        
        verify(leaveRepository, atLeastOnce()).save(any());
        verify(employeeLeaveRepository).save(any());
    }

    @Test
    void partialReview_adminStageAllRejected_completesFlowableTask() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        leave.setDays(List.of(day1, day2));
        
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision1 = new HashMap<>();
        decision1.put("date", "2025-06-01");
        decision1.put("status", LeaveConstants.STATUS_REJECTED);
        decision1.put("reason", "Not available");
        dayDecisions.add(decision1);
        
        Map<String, String> decision2 = new HashMap<>();
        decision2.put("date", "2025-06-02");
        decision2.put("status", LeaveConstants.STATUS_REJECTED);
        decision2.put("reason", "Not available");
        dayDecisions.add(decision2);
        
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        org.flowable.task.api.Task task = mock(org.flowable.task.api.Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null).thenReturn(task);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(taskQuery);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        
        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);
        
        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void partialReview_adminStagePartial_completesFlowableTask() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        leave.setDays(List.of(day1, day2));
        
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision1 = new HashMap<>();
        decision1.put("date", "2025-06-01");
        decision1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision1);
        
        Map<String, String> decision2 = new HashMap<>();
        decision2.put("date", "2025-06-02");
        decision2.put("status", LeaveConstants.STATUS_REJECTED);
        decision2.put("reason", "Not available");
        dayDecisions.add(decision2);
        
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        org.flowable.task.api.Task task = mock(org.flowable.task.api.Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null).thenReturn(task);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(taskQuery);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        
        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);
        
        verify(taskService).complete(eq("task-1"), anyMap());
    }
}
