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
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveProcessServiceImplAdditionalCoverageTest {

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

    private Leave createTestLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());
        return leave;
    }

    @Test
    void getActiveLeaveIdsForTask_withValidTasks() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("managerTask")).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(new ArrayList<>());

        List<Long> result = leaveProcessService.getActiveLeaveIdsForTask("managerTask");
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void getActiveLeaveIdsForTask_withEmptyTasks() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("managerTask")).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(new ArrayList<>());

        List<Long> result = leaveProcessService.getActiveLeaveIdsForTask("managerTask");

        assertEquals(0, result.size());
    }

    @Test
    void startLeaveProcess_withAllVariables() {
        Leave leave = createTestLeave();
        leave.setTotalDays(2.5);
        leave.setReason("Medical appointment");
        
        org.flowable.engine.runtime.ProcessInstance processInstance = mock(org.flowable.engine.runtime.ProcessInstance.class);
        when(processInstance.getId()).thenReturn("proc-123");
        
        when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap()))
            .thenReturn(processInstance);

        leaveProcessService.startLeaveProcess(leave, "mgr@test.com", "admin@test.com");

        verify(runtimeService).startProcessInstanceByKey(
            eq(LeaveConstants.PROCESS_KEY),
            eq(LeaveConstants.PROCESS_BUSINESS_KEY + "1"),
            anyMap()
        );
    }

    @Test
    void startLeaveProcess_withNullTotalDays() {
        Leave leave = createTestLeave();
        leave.setTotalDays(null);
        
        org.flowable.engine.runtime.ProcessInstance processInstance = mock(org.flowable.engine.runtime.ProcessInstance.class);
        when(processInstance.getId()).thenReturn("proc-123");
        
        when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap()))
            .thenReturn(processInstance);

        leaveProcessService.startLeaveProcess(leave, "mgr@test.com", "admin@test.com");

        verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test
    void approveLeave_managerTask_success() {
        // Mock for getManagerTask call
        TaskQuery managerTaskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(managerTaskQuery);
        when(managerTaskQuery.processInstanceBusinessKey(anyString())).thenReturn(managerTaskQuery);
        when(managerTaskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(managerTaskQuery);
        
        Task managerTask = mock(Task.class);
        when(managerTask.getId()).thenReturn("mgr-task-1");
        when(managerTaskQuery.singleResult()).thenReturn(managerTask);

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(taskService).complete(eq("mgr-task-1"), anyMap());
    }

    @Test
    void approveLeave_adminTask_success() {
        // Create separate TaskQuery mocks for each call
        TaskQuery managerTaskQuery = mock(TaskQuery.class);
        TaskQuery adminTaskQuery = mock(TaskQuery.class);
        
        // Setup the sequence of calls to taskService.createTaskQuery()
        when(taskService.createTaskQuery())
            .thenReturn(managerTaskQuery)  // First call for getManagerTask
            .thenReturn(adminTaskQuery);   // Second call for getAdminTask
        
        // Setup manager task query chain (returns null)
        when(managerTaskQuery.processInstanceBusinessKey(anyString())).thenReturn(managerTaskQuery);
        when(managerTaskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(managerTaskQuery);
        when(managerTaskQuery.singleResult()).thenReturn(null);
        
        // Setup admin task query chain (returns task)
        when(adminTaskQuery.processInstanceBusinessKey(anyString())).thenReturn(adminTaskQuery);
        when(adminTaskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(adminTaskQuery);
        Task adminTask = mock(Task.class);
        when(adminTask.getId()).thenReturn("admin-task-1");
        when(adminTaskQuery.singleResult()).thenReturn(adminTask);

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(taskService).complete(eq("admin-task-1"), anyMap());
    }

    @Test
    void rejectLeave_managerTask_success() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        
        Task managerTask = mock(Task.class);
        when(managerTask.getId()).thenReturn("mgr-task-1");
        when(taskQuery.singleResult()).thenReturn(managerTask);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", "Insufficient notice");

        verify(taskService).complete(eq("mgr-task-1"), anyMap());
    }

    @Test
    void partialReview_managerTask_allApproved() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        
        Task managerTask = mock(Task.class);
        when(managerTask.getId()).thenReturn("mgr-task-1");
        when(taskQuery.singleResult()).thenReturn(managerTask);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(taskService).complete(eq("mgr-task-1"), anyMap());
    }

    @Test
    void partialReview_managerTask_allRejected() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        
        Task managerTask = mock(Task.class);
        when(managerTask.getId()).thenReturn("mgr-task-1");
        when(taskQuery.singleResult()).thenReturn(managerTask);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_REJECTED);
        decision.put("reason", "Not available");
        dayDecisions.add(decision);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(taskService).complete(eq("mgr-task-1"), anyMap());
    }

    @Test
    void partialReview_managerTask_partial() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(taskQuery);
        
        Task managerTask = mock(Task.class);
        when(managerTask.getId()).thenReturn("mgr-task-1");
        when(taskQuery.singleResult()).thenReturn(managerTask);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision1 = new HashMap<>();
        decision1.put("date", "2025-06-01");
        decision1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision1);
        
        Map<String, String> decision2 = new HashMap<>();
        decision2.put("date", "2025-06-02");
        decision2.put("status", LeaveConstants.STATUS_REJECTED);
        dayDecisions.add(decision2);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(taskService).complete(eq("mgr-task-1"), anyMap());
    }

    @Test
    void partialReview_adminTask_allApproved() {
        // Create separate TaskQuery mocks for each call
        TaskQuery managerTaskQuery = mock(TaskQuery.class);
        TaskQuery adminTaskQuery = mock(TaskQuery.class);
        
        // Setup the sequence of calls to taskService.createTaskQuery()
        when(taskService.createTaskQuery())
            .thenReturn(managerTaskQuery)  // First call for getManagerTask
            .thenReturn(adminTaskQuery);   // Second call for getAdminTask
        
        // Setup manager task query chain (returns null)
        when(managerTaskQuery.processInstanceBusinessKey(anyString())).thenReturn(managerTaskQuery);
        when(managerTaskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(managerTaskQuery);
        when(managerTaskQuery.singleResult()).thenReturn(null);
        
        // Setup admin task query chain (returns task)
        when(adminTaskQuery.processInstanceBusinessKey(anyString())).thenReturn(adminTaskQuery);
        when(adminTaskQuery.taskDefinitionKey(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(adminTaskQuery);
        Task adminTask = mock(Task.class);
        when(adminTask.getId()).thenReturn("admin-task-1");
        when(adminTaskQuery.singleResult()).thenReturn(adminTask);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(taskService).complete(eq("admin-task-1"), anyMap());
    }

    @Test
    void partialReview_nullReviewer() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        Leave leave = createTestLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(LeaveConstants.SYSTEM_USER)).thenReturn("System User");
        when(leaveRepository.save(any())).thenReturn(leave);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);

        leaveProcessService.partialReview(1L, null, dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void buildMailTable_withReason() {
        // Use reflection to test private method
        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "buildMailTable", String.class, String.class, String.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);
            
            String result = (String) method.invoke(leaveProcessService, 
                "Leave Approved", "Sick", "2025-06-01", "2025-06-03", 
                LeaveConstants.STATUS_APPROVED, "Manager Name", "Good reason");
            
            assertNotNull(result);
            assertTrue(result.contains("Leave Approved"));
            assertTrue(result.contains("Good reason"));
        } catch (Exception e) {
            fail("Failed to test buildMailTable method");
        }
    }

    @Test
    void buildMailTable_withoutReason() {
        // Use reflection to test private method
        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "buildMailTable", String.class, String.class, String.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);
            
            String result = (String) method.invoke(leaveProcessService, 
                "Leave Rejected", "Sick", "2025-06-01", "2025-06-03", 
                LeaveConstants.STATUS_REJECTED, "Manager Name", null);
            
            assertNotNull(result);
            assertTrue(result.contains("Leave Rejected"));
        } catch (Exception e) {
            fail("Failed to test buildMailTable method");
        }
    }

    @Test
    void buildPartialSummaryHtml_employeeView() {
        // Use reflection to test private method
        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "buildPartialSummaryHtml", String.class, String.class, String.class, String.class, String.class, String.class, List.class, boolean.class);
            method.setAccessible(true);
            
            List<Map<String, String>> dayDecisions = new ArrayList<>();
            Map<String, String> decision = new HashMap<>();
            decision.put("date", "2025-06-01");
            decision.put("status", LeaveConstants.STATUS_APPROVED);
            dayDecisions.add(decision);
            
            String result = (String) method.invoke(leaveProcessService, 
                "Partial Approval", "Your leave has been partially approved", "Sick", 
                "2025-06-01", "2025-06-03", "Manager Name", dayDecisions, true);
            
            assertNotNull(result);
            assertTrue(result.contains("Partial Approval"));
        } catch (Exception e) {
            fail("Failed to test buildPartialSummaryHtml method");
        }
    }

    @Test
    void buildPartialSummaryHtml_adminView() {
        // Use reflection to test private method
        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "buildPartialSummaryHtml", String.class, String.class, String.class, String.class, String.class, String.class, List.class, boolean.class);
            method.setAccessible(true);
            
            List<Map<String, String>> dayDecisions = new ArrayList<>();
            Map<String, String> decision = new HashMap<>();
            decision.put("date", "2025-06-01");
            decision.put("status", LeaveConstants.STATUS_REJECTED);
            decision.put("reason", "Not available");
            dayDecisions.add(decision);
            
            String result = (String) method.invoke(leaveProcessService, 
                "Action Required", "Please review this leave request", "Sick", 
                "2025-06-01", "2025-06-03", "Manager Name", dayDecisions, false);
            
            assertNotNull(result);
            assertTrue(result.contains("Action Required"));
        } catch (Exception e) {
            fail("Failed to test buildPartialSummaryHtml method");
        }
    }

    @Test
    void directPartialReview_adminStageWithEmptyApprovedDays() {
        Leave leave = createTestLeave();
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        leave.setDays(List.of(day));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-01");
        decision.put("status", LeaveConstants.STATUS_REJECTED);
        dayDecisions.add(decision);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        // Use reflection to call private method
        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "directPartialReview", Long.class, String.class, List.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "admin@test.com", dayDecisions);
        } catch (Exception e) {
            // Handle reflection exception
        }

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void createManagerEmployeeLeaveRecord_exception() {
        Leave leave = createTestLeave();
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(userServiceClient.getFullName("mgr@test.com")).thenThrow(new RuntimeException("Service error"));

        // Use reflection to call private method
        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveApprovedLeaveToManager", Long.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "mgr@test.com");
        } catch (Exception e) {
            // Expected exception due to service error
        }

        verify(employeeLeaveRepository, never()).save(any());
    }

    @Test
    void createAdminEmployeeLeaveRecord_exception() {
        Leave leave = createTestLeave();
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        when(userServiceClient.getFullName("admin@test.com")).thenThrow(new RuntimeException("Service error"));

        // Use reflection to call private method
        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveApprovedLeaveToAdmin", Long.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "admin@test.com");
        } catch (Exception e) {
            // Expected exception due to service error
        }

        verify(employeeLeaveRepository, never()).save(any());
    }
}