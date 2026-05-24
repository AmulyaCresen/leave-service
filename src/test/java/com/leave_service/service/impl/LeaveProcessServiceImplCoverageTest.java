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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveProcessServiceImplCoverageTest {

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

    private TaskQuery mockTaskQuery() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskQuery.processInstanceBusinessKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        return taskQuery;
    }

    @Test
    void approveLeave_directFallback_managerStage() {
        Leave leave = createTestLeave();
        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.empty());

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    @Test
    void directManagerPartialNotify_withApprovedAndRejectedDays() {
        Leave leave = createTestLeave();
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

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.directManagerPartialNotify(1L, "mgr@test.com", dayDecisions, "admin@test.com");

        verify(leaveRepository).save(any());
        verify(asyncMailSender, times(3)).send(anyString(), anyString(), anyString());
    }

    @Test
    void saveTrailEntry_withReason() {
        Leave leave = createTestLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenReturn(leave);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveTrailEntry", Long.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, LeaveConstants.STATUS_REJECTED, "mgr@test.com", "Not enough notice", LeaveConstants.STAGE_MANAGER);
        } catch (Exception e) {
            // Handle reflection exception
        }

        verify(leaveRepository).save(any());
    }

    @Test
    void saveDayStatuses_withValidDays() {
        Leave leave = createTestLeave();
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

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenReturn(leave);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveDayStatuses", Long.class, List.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, dayDecisions);
        } catch (Exception e) {
            // Handle reflection exception
        }

        verify(leaveRepository).save(any());
    }

    @Test
    void saveApprovedLeaveToManager_managerNotFound() {
        Leave leave = createTestLeave();
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager Name");
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveApprovedLeaveToManager", Long.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "mgr@test.com");
        } catch (Exception e) {
            // Handle reflection exception
        }

        verify(employeeLeaveRepository).save(any());
    }

    @Test
    void partialReview_directFallback_adminStageWithRejectedDays() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_REJECTED);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_APPROVED);
        leave.setDays(List.of(day1, day2));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> decision = new HashMap<>();
        decision.put("date", "2025-06-02");
        decision.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(decision);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    @Test
    void calculateTotalDays_withHalfDays() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setDayType(LeaveConstants.DAY_TYPE_HALF);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setDayType("FULL_DAY");
        leave.setDays(List.of(day1, day2));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // ---- directPartialReview admin stage: both approved & rejected payloads → both mails ----
    @Test
    void directPartialReview_adminStage_bothPayloadsMailed() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_REJECTED);
        day2.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(new ArrayList<>(List.of(day1, day2)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);
        Map<String, String> d2 = new HashMap<>();
        d2.put("date", "2025-06-02");
        d2.put("status", LeaveConstants.STATUS_REJECTED);
        d2.put("reason", "Not available");
        dayDecisions.add(d2);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(asyncMailSender, atLeast(2)).send(anyString(), anyString(), anyString());
        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // ---- directPartialReview admin stage: finalApproved has HALF_DAY → mapToDouble covers 0.5 ----
    @Test
    void directPartialReview_adminStage_halfDayApprovedBalance() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        day1.setDayType(LeaveConstants.DAY_TYPE_HALF);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // ---- directPartialReview admin stage: balance key not present → no save ----
    @Test
    void directPartialReview_adminStage_balanceKeyNotPresent() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        // empLeave found but doesn't contain the leave type
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Annual", 10.0))); // "Sick" not present
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directPartialReview admin stage: day gets REJECTED decision with reason ----
    @Test
    void directPartialReview_adminStage_dayRejectedWithReason() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_REJECTED);
        d1.put("reason", "Critical project delivery");
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(asyncMailSender, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    // ---- directPartialReview admin stage: no days ----
    @Test
    void directPartialReview_adminStage_noDays() {
        Leave leave = createTestLeave();
        leave.setDays(null);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.empty());
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directReject admin stage: null reason ----
    @Test
    void directReject_adminStage_nullReason() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(new ArrayList<>(List.of(day1)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "admin@test.com", null);

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender).send(anyString(), anyString(), anyString());
    }

    // ---- directReject admin stage: blank reason ----
    @Test
    void directReject_adminStage_blankReason() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(new ArrayList<>(List.of(day1)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "admin@test.com", "   ");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender).send(anyString(), anyString(), anyString());
    }

    // ---- directReject admin stage: day already ADMIN-rejected → skip ----
    @Test
    void directReject_adminStage_skipAlreadyRejectedDay() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_REJECTED);
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_APPROVED);
        day2.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(new ArrayList<>(List.of(day1, day2)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "admin@test.com", "Admin override");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directReject without days ----
    @Test
    void directReject_withNullDays() {
        Leave leave = createTestLeave();
        leave.setDays(null);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", "No reason");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender).send(anyString(), anyString(), anyString());
    }

    // ---- directApprove admin stage: some days already REJECTED → skip ----
    @Test
    void directApprove_adminStage_skipAlreadyRejectedDays() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_REJECTED);
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_APPROVED);
        day2.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(new ArrayList<>(List.of(day1, day2)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Sick", 10.0)));
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // ---- directApprove admin stage: balance key not present ----
    @Test
    void directApprove_adminStage_balanceKeyNotPresent() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(new ArrayList<>(List.of(day1)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setLeaves(new HashMap<>(Map.of("Annual", 10.0))); // "Sick" not present
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.of(empLeave));

        leaveProcessService.approveLeave(1L, "admin@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directManagerPartialNotify: null days ----
    @Test
    void directManagerPartialNotify_nullDays() {
        Leave leave = createTestLeave();
        leave.setDays(null);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");

        leaveProcessService.directManagerPartialNotify(1L, "mgr@test.com", dayDecisions, "admin@test.com");

        verify(leaveRepository, never()).save(any());
        verify(asyncMailSender, atLeast(2)).send(anyString(), anyString(), anyString());
    }

    // ---- directManagerPartialNotify: only rejected days → no approved mails ----
    @Test
    void directManagerPartialNotify_onlyRejectedDays() {
        Leave leave = createTestLeave();
        leave.setDays(null);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_REJECTED);
        d1.put("reason", "Critical deadline");
        dayDecisions.add(d1);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");

        leaveProcessService.directManagerPartialNotify(1L, "mgr@test.com", dayDecisions, "admin@test.com");

        verify(asyncMailSender, times(1)).send(anyString(), anyString(), anyString());
    }

    // ---- directManagerPartialNotify: null managerReviewer ----
    @Test
    void directManagerPartialNotify_nullManagerReviewer() {
        Leave leave = createTestLeave();
        leave.setDays(null);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("System");

        leaveProcessService.directManagerPartialNotify(1L, null, dayDecisions, "admin@test.com");

        verify(asyncMailSender, atLeast(1)).send(anyString(), anyString(), anyString());
    }

    // ---- saveTrailEntry: null reason and null reviewedBy ----
    @Test
    void saveTrailEntry_nullReasonAndNullReviewedBy() {
        Leave leave = createTestLeave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenReturn(leave);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveTrailEntry", Long.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, LeaveConstants.STATUS_APPROVED, null, null, null);
        } catch (Exception e) {
            // expected
        }

        verify(leaveRepository).save(any());
    }

    // ---- saveTrailEntry: null trail on leave ----
    @Test
    void saveTrailEntry_nullTrailOnLeave() {
        Leave leave = createTestLeave();
        leave.setTrail(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenReturn(leave);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveTrailEntry", Long.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, LeaveConstants.STATUS_REJECTED, "mgr@test.com", "reason", LeaveConstants.STAGE_MANAGER);
        } catch (Exception e) {
            // expected
        }

        verify(leaveRepository).save(any());
    }

    // ---- savePartiallyApprovedLeaveToManager: manager not found, approvedDays > 0 ----
    @Test
    void savePartiallyApprovedLeaveToManager_managerNotFound_approvedDaysPositive() {
        Leave leave = createTestLeave();

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(userServiceClient.getFullName("mgr@test.com")).thenReturn("Manager Name");
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "savePartiallyApprovedLeaveToManager", Long.class, String.class, List.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "mgr@test.com", dayDecisions);
        } catch (Exception e) {
            // expected
        }

        verify(employeeLeaveRepository).save(any());
    }

    // ---- savePartiallyApprovedLeaveToManager: manager found, existing leaves ----
    @Test
    void savePartiallyApprovedLeaveToManager_managerFoundWithExistingLeaves() {
        Leave leave = createTestLeave();

        EmployeeLeave mgrLeave = new EmployeeLeave();
        Map<String, Object> mgrLeaves = new HashMap<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        mgrLeaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approved);
        mgrLeave.setLeaves(mgrLeaves);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(mgrLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(mgrLeave);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", LeaveConstants.DAY_TYPE_HALF);
        dayDecisions.add(d1);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "savePartiallyApprovedLeaveToManager", Long.class, String.class, List.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "mgr@test.com", dayDecisions);
        } catch (Exception e) {
            // expected
        }

        verify(employeeLeaveRepository).save(any());
    }

    // ---- saveApprovedLeaveToAdmin: existing record found ----
    @Test
    void saveApprovedLeaveToAdmin_existingAdminRecord() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        day1.setDayType(LeaveConstants.DAY_TYPE_HALF);
        leave.setDays(new ArrayList<>(List.of(day1)));

        EmployeeLeave adminLeave = new EmployeeLeave();
        Map<String, Object> adminLeaves = new HashMap<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        adminLeaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approved);
        adminLeave.setLeaves(adminLeaves);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(adminLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(adminLeave);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveApprovedLeaveToAdmin", Long.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "admin@test.com");
        } catch (Exception e) {
            // expected
        }

        verify(employeeLeaveRepository).save(any());
    }

    // ---- saveApprovedLeaveToAdmin: admin not found ----
    @Test
    void saveApprovedLeaveToAdmin_adminNotFound() {
        Leave leave = createTestLeave();

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveApprovedLeaveToAdmin", Long.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "admin@test.com");
        } catch (Exception e) {
            // expected
        }

        verify(employeeLeaveRepository).save(any());
    }

    // ---- savePartiallyApprovedLeaveToAdmin: existing record found ----
    @Test
    void savePartiallyApprovedLeaveToAdmin_existingAdminRecord() {
        Leave leave = createTestLeave();

        EmployeeLeave adminLeave = new EmployeeLeave();
        Map<String, Object> adminLeaves = new HashMap<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        adminLeaves.put(LeaveConstants.APPROVED_LEAVES_KEY, approved);
        adminLeave.setLeaves(adminLeaves);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(adminLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(adminLeave);

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", LeaveConstants.DAY_TYPE_HALF);
        dayDecisions.add(d1);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "savePartiallyApprovedLeaveToAdmin", Long.class, String.class, List.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "admin@test.com", dayDecisions);
        } catch (Exception e) {
            // expected
        }

        verify(employeeLeaveRepository).save(any());
    }

    // ---- savePartiallyApprovedLeaveToAdmin: admin not found ----
    @Test
    void savePartiallyApprovedLeaveToAdmin_adminNotFound() {
        Leave leave = createTestLeave();

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(employeeLeaveRepository.save(any())).thenReturn(new EmployeeLeave());

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "savePartiallyApprovedLeaveToAdmin", Long.class, String.class, List.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "admin@test.com", dayDecisions);
        } catch (Exception e) {
            // expected
        }

        verify(employeeLeaveRepository).save(any());
    }

    // ---- getActiveLeaveIdsForTask: businessKey parse exception ----
    @Test
    void getActiveLeaveIdsForTask_businessKeyParseException() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        org.flowable.task.api.Task task = mock(org.flowable.task.api.Task.class);
        when(task.getProcessInstanceId()).thenReturn("proc-1");
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        org.flowable.engine.runtime.ProcessInstanceQuery piQuery = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
        org.flowable.engine.runtime.ProcessInstance pi = mock(org.flowable.engine.runtime.ProcessInstance.class);
        when(pi.getBusinessKey()).thenReturn("NOT_VALID_NUMBER");
        when(piQuery.processInstanceId(anyString())).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(pi);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);

        List<Long> result = leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ---- saveApprovedLeaveToManager: manager found but leaves map is null ----
    @Test
    void saveApprovedLeaveToManager_managerFoundNullLeaves() {
        Leave leave = createTestLeave();

        EmployeeLeave mgrLeave = new EmployeeLeave();
        mgrLeave.setLeaves(null); // null leaves map

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(mgrLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(mgrLeave);

        try {
            java.lang.reflect.Method method = LeaveProcessServiceImpl.class.getDeclaredMethod(
                "saveApprovedLeaveToManager", Long.class, String.class);
            method.setAccessible(true);
            method.invoke(leaveProcessService, 1L, "mgr@test.com");
        } catch (Exception e) {
            // expected
        }

        verify(employeeLeaveRepository).save(any());
    }

    // ---- directPartialReview: MANAGER stage, all rejected, not admin leave ----
    @Test
    void directPartialReview_managerStage_allRejected() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_REJECTED);
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender, atLeastOnce()).send(any(), anyString(), anyString());
    }

    // ---- directPartialReview: MANAGER stage, partial decisions, not admin leave ----
    @Test
    void directPartialReview_managerStage_partialDecisions() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(Arrays.asList(day1, day2)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);
        Map<String, String> d2 = new HashMap<>();
        d2.put("date", "2025-06-02");
        d2.put("status", LeaveConstants.STATUS_REJECTED);
        dayDecisions.add(d2);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId(anyString())).thenReturn(Optional.empty());

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directPartialReview: MANAGER stage, rejected day with reason ----
    @Test
    void directPartialReview_managerStage_dayWithReason() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_REJECTED);
        d1.put("reason", "Not enough notice");
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directPartialReview: MANAGER stage, admin's own leave, all approved, balance update ----
    @Test
    void directPartialReview_managerStage_isAdminLeave_allApproved() {
        Leave leave = createTestLeave();
        leave.setEmailId("admin@test.com"); // admin's own leave
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", LeaveConstants.DAY_TYPE_FULL);
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> balances = new HashMap<>();
        balances.put("Sick", 10.0);
        empLeave.setLeaves(balances);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender, atLeastOnce()).send(any(), anyString(), anyString());
    }

    // ---- directReject: MANAGER stage, non-null reason ----
    @Test
    void directReject_managerStage_withReason() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", "Project deadline");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender, atLeastOnce()).send(any(), anyString(), anyString());
    }

    // ---- directApprove: MANAGER stage, admin's own leave (isAdminLeave=true) ----
    @Test
    void directApprove_managerStage_isAdminLeave() {
        Leave leave = createTestLeave();
        leave.setEmailId("admin@test.com"); // admin's own leave

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> balances = new HashMap<>();
        balances.put("Sick", 10.0);
        empLeave.setLeaves(balances);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender, atLeastOnce()).send(any(), anyString(), anyString());
    }

    // ---- savePartiallyApprovedLeaveToManager: manager found but leaves is null ----
    @Test
    void savePartiallyApprovedLeaveToManager_managerFoundNullLeaves() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", LeaveConstants.DAY_TYPE_FULL);
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        EmployeeLeave mgrLeave = new EmployeeLeave();
        mgrLeave.setLeaves(null); // null leaves - triggers the null check

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(mgrLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(mgrLeave);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // ---- savePartiallyApprovedLeaveToManager: manager found, no approved_leaves key ----
    @Test
    void savePartiallyApprovedLeaveToManager_managerFoundNoApprovedLeavesKey() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        EmployeeLeave mgrLeave = new EmployeeLeave();
        mgrLeave.setLeaves(new HashMap<>()); // empty map, no approved_leaves key

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(mgrLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(mgrLeave);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // ---- savePartiallyApprovedLeaveToAdmin: admin found but leaves is null ----
    @Test
    void savePartiallyApprovedLeaveToAdmin_adminFoundNullLeaves() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", LeaveConstants.DAY_TYPE_FULL);
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        EmployeeLeave adminLeave = new EmployeeLeave();
        adminLeave.setLeaves(null); // null leaves

        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> empBalances = new HashMap<>();
        empBalances.put("Sick", 10.0);
        empLeave.setLeaves(empBalances);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(adminLeave));
        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(adminLeave);

        leaveProcessService.partialReview(1L, "admin@test.com", dayDecisions);

        verify(employeeLeaveRepository, atLeastOnce()).save(any());
    }

    // ---- directReject: ADMIN stage, non-null non-blank reason ----
    @Test
    void directReject_adminStage_withReason() {
        Leave leave = createTestLeave();
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Admin Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "admin@test.com", "Policy violation");

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender, atLeastOnce()).send(any(), anyString(), anyString());
    }

    // ---- directApprove: null trail -> covers L415 FALSE branch (trail==null -> new ArrayList) ----
    @Test
    void directApprove_nullTrail() {
        Leave leave = createTestLeave();
        leave.setTrail(null); // null trail - covers L415 FALSE branch

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        EmployeeLeave mgrLeave = new EmployeeLeave();
        mgrLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(mgrLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(mgrLeave);

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directApprove: non-empty trail -> covers L416 FALSE branch (trail non-empty -> get dayType from trail[0]) ----
    @Test
    void directApprove_nonEmptyTrail() {
        Leave leave = createTestLeave();
        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_HALF);
        trailEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        leave.setTrail(new ArrayList<>(List.of(trailEntry))); // non-empty trail - covers L416 FALSE

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);

        EmployeeLeave mgrLeave = new EmployeeLeave();
        mgrLeave.setLeaves(new HashMap<>());
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(mgrLeave));
        when(employeeLeaveRepository.save(any())).thenReturn(mgrLeave);

        leaveProcessService.approveLeave(1L, "mgr@test.com");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directReject: null trail -> covers L529 FALSE branch ----
    @Test
    void directReject_nullTrail() {
        Leave leave = createTestLeave();
        leave.setTrail(null); // null trail - covers L529 FALSE branch
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", "No reason");

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directReject: non-empty trail -> covers L530 FALSE branch (trail non-empty -> get dayType) ----
    @Test
    void directReject_nonEmptyTrail() {
        Leave leave = createTestLeave();
        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_FULL);
        trailEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        leave.setTrail(new ArrayList<>(List.of(trailEntry))); // non-empty trail - covers L530 FALSE
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager");
        when(leaveRepository.save(any())).thenReturn(leave);

        leaveProcessService.rejectLeave(1L, "mgr@test.com", null);

        verify(leaveRepository, atLeastOnce()).save(any());
    }

    // ---- directPartialReview: manager stage, isAdminLeave, null trail + HALF day -> covers L245, L305 ----
    @Test
    void directPartialReview_managerStage_isAdminLeave_nullTrail_halfDay() {
        Leave leave = createTestLeave();
        leave.setEmailId("admin@test.com"); // isAdminLeave = true
        leave.setTrail(null); // null trail - covers L245 FALSE branch
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(new ArrayList<>(List.of(day1)));

        List<Map<String, String>> dayDecisions = new ArrayList<>();
        Map<String, String> d1 = new HashMap<>();
        d1.put("date", "2025-06-01");
        d1.put("status", LeaveConstants.STATUS_APPROVED);
        d1.put("dayType", LeaveConstants.DAY_TYPE_HALF); // HALF_DAY - covers L305 0.5 branch
        dayDecisions.add(d1);

        TaskQuery taskQuery = mockTaskQuery();
        when(taskQuery.singleResult()).thenReturn(null);

        EmployeeLeave empLeave = new EmployeeLeave();
        Map<String, Object> balances = new HashMap<>();
        balances.put("Sick", 10.0);
        empLeave.setLeaves(balances);

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userServiceClient.getFullName(anyString())).thenReturn("Manager Name");
        when(leaveRepository.save(any())).thenReturn(leave);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());
        when(employeeLeaveRepository.save(any())).thenReturn(empLeave);

        leaveProcessService.partialReview(1L, "mgr@test.com", dayDecisions);

        verify(leaveRepository, atLeastOnce()).save(any());
        verify(asyncMailSender, atLeastOnce()).send(any(), anyString(), anyString());
    }
}