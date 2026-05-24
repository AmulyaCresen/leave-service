package com.leave_service.service.impl;

import com.leave_service.client.UserServiceClient;
import com.leave_service.commons.LeaveConstants;
import com.leave_service.model.Leave;
import com.leave_service.repository.EmployeeLeaveRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.service.AsyncMailSender;
import com.leave_service.service.LeaveWorkflowService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
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
class LeaveProcessServiceImplTest {

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
    void startLeaveProcess_success() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setReason("Medical");
        leave.setTotalDays(3.0);

        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("proc-123");
        when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap())).thenReturn(processInstance);

        leaveProcessService.startLeaveProcess(leave, "mgr@test.com", "admin@test.com");

        verify(runtimeService).startProcessInstanceByKey(eq(LeaveConstants.PROCESS_KEY), anyString(), anyMap());
    }

    @Test
    void getActiveLeaveIdsForTask_returnsIds() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("proc-1");
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        org.flowable.engine.runtime.ProcessInstanceQuery piQuery = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getBusinessKey()).thenReturn("leave-1");
        when(piQuery.processInstanceId(anyString())).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(pi);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);

        List<Long> result = leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0));
    }

    @Test
    void approveLeave_withManagerTask() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void rejectLeave_withReason() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", "Not enough notice");

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void partialReview_withDayDecisions() {
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> day1 = new HashMap<>();
        day1.put("date", "2025-06-01");
        day1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(day1);

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void partialReview_allRejected() {
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> day1 = new HashMap<>();
        day1.put("date", "2025-06-01");
        day1.put("status", LeaveConstants.STATUS_REJECTED);
        day1.put("reason", "Not available");
        dayDecisions.add(day1);

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void partialReview_mixedDecisions() {
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> day1 = new HashMap<>();
        day1.put("date", "2025-06-01");
        day1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(day1);
        
        Map<String, String> day2 = new HashMap<>();
        day2.put("date", "2025-06-02");
        day2.put("status", LeaveConstants.STATUS_REJECTED);
        dayDecisions.add(day2);

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void partialReview_adminStage() {
        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> day1 = new HashMap<>();
        day1.put("date", "2025-06-01");
        day1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(day1);

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
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
    void approveLeave_adminStage() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null).thenReturn(task);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(taskQuery);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void rejectLeave_adminStage() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null).thenReturn(task);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(taskQuery);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        leaveProcessService.rejectLeave(1L, "admin@test.com", "Insufficient balance");

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void getActiveLeaveIdsForTask_withInvalidBusinessKey() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("proc-1");
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        org.flowable.engine.runtime.ProcessInstanceQuery piQuery = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getBusinessKey()).thenReturn("invalid-key");
        when(piQuery.processInstanceId(anyString())).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(pi);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);

        List<Long> result = leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER);

        assertEquals(0, result.size());
    }

    @Test
    void startLeaveProcess_withNullTotalDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setReason("Medical");
        leave.setTotalDays(null);

        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("proc-123");
        when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap())).thenReturn(processInstance);

        leaveProcessService.startLeaveProcess(leave, "mgr@test.com", "admin@test.com");

        verify(runtimeService).startProcessInstanceByKey(eq(LeaveConstants.PROCESS_KEY), anyString(), anyMap());
    }

    @Test
    void approveLeave_noTaskFallbackToDirect() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.empty());

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void rejectLeave_noTaskFallbackToDirect() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", "Not enough notice");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void partialReview_noTaskFallbackToDirect() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        com.leave_service.dto.LeaveDayEntry day = new com.leave_service.dto.LeaveDayEntry();
        day.setDate("2025-06-01"); day.setDayType("FULL_DAY");
        leave.setDays(List.of(day));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.empty());

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void partialReview_nullReviewer() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        com.leave_service.dto.LeaveDayEntry day = new com.leave_service.dto.LeaveDayEntry();
        day.setDate("2025-06-01");
        leave.setDays(List.of(day));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("System");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.empty());

        leaveProcessService.partialReview(1L, null, dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void approveLeave_flowableException() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        doThrow(new org.flowable.common.engine.api.FlowableException("Test exception")).when(taskService).complete(anyString(), anyMap());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.empty());

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void rejectLeave_flowableException() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        doThrow(new org.flowable.common.engine.api.FlowableException("Test exception")).when(taskService).complete(anyString(), anyMap());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", "Not enough notice");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void partialReview_flowableException() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        com.leave_service.dto.LeaveDayEntry day = new com.leave_service.dto.LeaveDayEntry();
        day.setDate("2025-06-01");
        leave.setDays(List.of(day));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        doThrow(new org.flowable.common.engine.api.FlowableException("Test exception")).when(taskService).complete(anyString(), anyMap());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.empty());

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void directPartialReview_adminStageWithRejectedDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        com.leave_service.dto.LeaveDayEntry day1 = new com.leave_service.dto.LeaveDayEntry();
        day1.setDate("2025-06-01"); day1.setStatus(LeaveConstants.STATUS_APPROVED);
        com.leave_service.dto.LeaveDayEntry day2 = new com.leave_service.dto.LeaveDayEntry();
        day2.setDate("2025-06-02"); day2.setStatus(LeaveConstants.STATUS_REJECTED);
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

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        com.leave_service.model.EmployeeLeave empLeave = new com.leave_service.model.EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void directApprove_adminStageWithDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        com.leave_service.dto.LeaveDayEntry day = new com.leave_service.dto.LeaveDayEntry();
        day.setDate("2025-06-01"); day.setDayType("FULL_DAY");
        leave.setDays(List.of(day));

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        com.leave_service.model.EmployeeLeave empLeave = new com.leave_service.model.EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void directReject_withDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        com.leave_service.dto.LeaveDayEntry day = new com.leave_service.dto.LeaveDayEntry();
        day.setDate("2025-06-01");
        leave.setDays(List.of(day));

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", "Not enough notice");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void directApprove_managerStageForAdminLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("admin@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        com.leave_service.model.EmployeeLeave empLeave = new com.leave_service.model.EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }
}
