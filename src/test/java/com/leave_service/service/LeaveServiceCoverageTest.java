package com.leave_service.service;

import com.leave_service.commons.LeaveConstants;
import com.leave_service.dto.*;
import com.leave_service.model.*;
import com.leave_service.repository.*;
import jakarta.persistence.EntityManager;
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
class LeaveServiceCoverageTest {

    @Mock private LeaveRepository leaveRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private EmployeeLeaveRepository employeeLeaveRepository;
    @Mock private LeaveProcessService leaveProcessService;
    @Mock private EntityManager entityManager;
    @InjectMocks private LeaveService leaveService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(leaveService, "managerEmail", "manager@test.com");
        ReflectionTestUtils.setField(leaveService, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(leaveService, "entityManager", entityManager);
    }

    // Test hydration with various trail and day combinations
    @Test
    void hydrateTransients_withAdminApprovalAndMixedDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        day1.setDayType("FULL_DAY");
        
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_REJECTED);
        day2.setDayType("HALF_DAY");
        
        leave.setDays(List.of(day1, day2));
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        trail.put(LeaveConstants.TRAIL_DAY_TYPE, "FULL_DAY");
        trail.put(LeaveConstants.TRAIL_HALF_DAY_SESSION, "MORNING");
        trail.put(LeaveConstants.TRAIL_REVIEWED_BY, "admin@test.com");
        
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PARTIAL, result.getStatus());
        assertEquals(1.5, result.getTotalDays());
        assertEquals("MORNING", result.getHalfDaySession());
    }

    @Test
    void hydrateTransients_withManagerApprovalStatus() {
        Leave leave = new Leave();
        leave.setId(1L);
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        leave.setDays(List.of(day));
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_MANAGER_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_MANAGER);
        
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_MANAGER_APPROVED, result.getStatus());
    }

    @Test
    void hydrateTransients_allDaysRejected() {
        Leave leave = new Leave();
        leave.setId(1L);
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_REJECTED);
        leave.setDays(List.of(day));
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_REJECTED, result.getStatus());
    }

    @Test
    void hydrateTransients_withPendingDaysOnly() {
        Leave leave = new Leave();
        leave.setId(1L);
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_PENDING);
        leave.setDays(List.of(day));
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PENDING, result.getStatus());
    }

    @Test
    void hydrateTransients_noDaysWithTrail() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setDays(null);
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_HALF);
        
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_APPROVED, result.getStatus());
        assertEquals(LeaveConstants.DAY_TYPE_HALF, result.getDayType());
    }

    @Test
    void hydrateTransients_emptyTrailAndDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setDays(null);
        leave.setTrail(new ArrayList<>());
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PENDING, result.getStatus());
        assertEquals(LeaveConstants.DAY_TYPE_FULL, result.getDayType());
    }

    // Test getPendingLeavesFor with various scenarios
    @Test
    void getPendingLeavesFor_adminWithManagerRejectedLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_REJECTED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_MANAGER);
        
        leave.setTrail(List.of(trail));
        
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(0, result.size());
    }

    @Test
    void getPendingLeavesFor_adminWithDayDecisionRejection() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_REJECTED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_MANAGER);
        trail.put("type", LeaveConstants.TYPE_DAY_DECISION);
        
        leave.setTrail(List.of(trail));
        
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getPendingLeavesFor_adminWithApprovedDaysFromManager() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        day.setReviewStage(LeaveConstants.STAGE_MANAGER);
        
        leave.setDays(List.of(day));
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 1));
        leave.setTrail(new ArrayList<>());
        
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getPendingLeavesFor_adminWithAllRejectedDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_REJECTED);
        
        leave.setDays(List.of(day));
        leave.setTrail(new ArrayList<>());
        
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(0, result.size());
    }

    @Test
    void getPendingLeavesFor_adminWithNonRejectedDays() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(null);
        
        leave.setDays(List.of(day));
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 1));
        leave.setTrail(new ArrayList<>());
        
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getPendingLeavesFor_managerExcludesOwnLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("mgr@test.com");
        leave.setManagerEmail("mgr@test.com");
        leave.setTrail(new ArrayList<>());
        
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("mgr@test.com");

        assertEquals(0, result.size());
    }

    // Test createLeave with various scenarios
    @Test
    void createLeave_withDaysAndHalfDayType() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-10");
        day.setDayType("HALF_DAY");
        day.setHalfDaySession("MORNING");
        req.setDays(List.of(day));
        req.setReason("Medical");
        
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of());
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertNotNull(result);
        verify(leaveProcessService).startLeaveProcess(any(), anyString(), anyString());
    }

    @Test
    void createLeave_withNullManagerEmail() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        req.setFromDate("2025-06-10");
        req.setToDate("2025-06-12");
        req.setReason("Medical");
        req.setManagerEmail(null);
        
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of());
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertEquals("manager@test.com", result.getManagerEmail());
    }

    @Test
    void createLeave_withBlankManagerEmail() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        req.setFromDate("2025-06-10");
        req.setToDate("2025-06-12");
        req.setReason("Medical");
        req.setManagerEmail("   ");
        
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of());
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertEquals("manager@test.com", result.getManagerEmail());
    }

    @Test
    void createLeave_conflictWithDaysVsNoDays() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-10");
        req.setDays(List.of(day));
        req.setReason("Medical");
        
        Leave existing = new Leave();
        existing.setId(2L);
        existing.setFromDate(LocalDate.of(2025, 6, 10));
        existing.setToDate(LocalDate.of(2025, 6, 10));
        existing.setDays(null);
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        existing.setTrail(List.of(trail));
        
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));

        assertThrows(Exception.class, () -> leaveService.createLeave(req, "emp@test.com"));
    }

    @Test
    void updateLeave_withNullDayType() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual");
        req.setFromDate("2025-06-05");
        req.setToDate("2025-06-07");
        req.setReason("Personal");
        req.setDayType(null);
        
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(new ArrayList<>(List.of(trail)));
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Leave result = leaveService.updateLeave(1L, req, "emp@test.com");

        assertNotNull(result);
    }

    @Test
    void getReviewedLeavesByReviewer_withEmptyTrail() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setTrail(new ArrayList<>());
        
        when(leaveRepository.findAll()).thenReturn(List.of(leave));

        List<Leave> result = leaveService.getReviewedLeavesByReviewer("mgr@test.com");

        assertEquals(0, result.size());
    }

    @Test
    void hydrateTransients_withManagerApprovalInTrail() {
        Leave leave = new Leave();
        leave.setId(1L);
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        leave.setDays(List.of(day));
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_MANAGER);
        
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_MANAGER_APPROVED, result.getStatus());
    }

    @Test
    void hydrateTransients_withPartialStatusInTrail() {
        Leave leave = new Leave();
        leave.setId(1L);
        
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_APPROVED);
        
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_REJECTED);
        
        leave.setDays(List.of(day1, day2));
        
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PARTIAL);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PARTIAL, result.getStatus());
    }

    // appendTrail: halfDaySession != null branch
    @Test
    void updateLeave_withHalfDayType_setsHalfDaySession() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual");
        req.setFromDate("2025-06-05");
        req.setToDate("2025-06-05");
        req.setReason("Personal");
        req.setDayType(LeaveConstants.DAY_TYPE_HALF);
        req.setHalfDaySession("MORNING");

        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");

        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(new ArrayList<>(List.of(trailEntry)));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Leave result = leaveService.updateLeave(1L, req, "emp@test.com");

        assertNotNull(result);
        assertTrue(result.getTrail().stream()
            .anyMatch(e -> "MORNING".equals(e.get(LeaveConstants.TRAIL_HALF_DAY_SESSION))));
    }

    // appendTrail: noneMatch=false branch (trail already has TYPE_LEAVE_APPLIED)
    @Test
    void updateLeave_trailAlreadyHasTypeLeaveApplied_noTypeAddedAgain() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual");
        req.setFromDate("2025-06-05");
        req.setToDate("2025-06-07");
        req.setReason("Re-apply");
        req.setDayType(LeaveConstants.DAY_TYPE_FULL);

        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");

        Map<String, String> existingEntry = new HashMap<>();
        existingEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        existingEntry.put("type", LeaveConstants.TYPE_LEAVE_APPLIED);
        leave.setTrail(new ArrayList<>(List.of(existingEntry)));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Leave result = leaveService.updateLeave(1L, req, "emp@test.com");

        assertNotNull(result);
        // New entry should NOT have type=TYPE_LEAVE_APPLIED since noneMatch is false
        Map<String, String> lastEntry = result.getTrail().get(result.getTrail().size() - 1);
        assertNull(lastEntry.get("type"));
    }

    // getPendingLeavesFor: admin with null trail (skips managerRejected check)
    @Test
    void getPendingLeavesFor_adminWithNullTrail_includesLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setTrail(null);
        leave.setDays(null);

        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(1, result.size());
    }

    // getPendingLeavesFor: manager sees admin's own leave
    @Test
    void getPendingLeavesFor_managerCanSeeAdminOwnLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("admin@test.com");
        leave.setManagerEmail("mgr@test.com");
        leave.setTrail(null);

        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("mgr@test.com");

        assertEquals(1, result.size());
    }

    // getAdminLoggedLeaves: empLeave not found
    @Test
    void getAdminLoggedLeaves_empLeaveNotFound() {
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());

        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");

        assertTrue(result.isEmpty());
    }

    // getAdminLoggedLeaves: empLeave found with approved_leaves
    @Test
    void getAdminLoggedLeaves_empLeaveFoundWithApprovedLeaves() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com");
        empLeave.setFullName("Admin User");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of(Map.of("leaveId", "1")));
        empLeave.setLeaves(leaves);

        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");

        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("approved_leaves"));
    }

    // getManagerLoggedLeaves: empLeave found but no approved_leaves key
    @Test
    void getManagerLoggedLeaves_empLeaveFoundNoApprovedKey() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("mgr@test.com");
        empLeave.setFullName("Manager User");
        empLeave.setLeaves(new HashMap<>());

        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getManagerLoggedLeaves("mgr@test.com");

        assertTrue(result.isEmpty());
    }

    // getManagerLoggedLeaves: empLeave not found
    @Test
    void getManagerLoggedLeaves_empLeaveNotFound() {
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());

        Map<String, Object> result = leaveService.getManagerLoggedLeaves("mgr@test.com");

        assertTrue(result.isEmpty());
    }

    // hydrateTransients: day with null status counts as pending
    @Test
    void hydrateTransients_dayWithNullStatus_setsPending() {
        Leave leave = new Leave();
        leave.setId(1L);

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(null);
        leave.setDays(List.of(day));

        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(List.of(trailEntry));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PENDING, result.getStatus());
    }

    // hydrateTransients: all approved days, no admin/manager trail -> else branch
    @Test
    void hydrateTransients_allApprovedDays_noSpecialTrail_usesTrailStatus() {
        Leave leave = new Leave();
        leave.setId(1L);

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        day.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(List.of(day));

        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put("type", LeaveConstants.TYPE_LEAVE_APPLIED);
        trailEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(List.of(trailEntry));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        // pendingCount=0, rejectedCount=0, no admin/manager approval -> uses trail status
        assertEquals(LeaveConstants.STATUS_PENDING, result.getStatus());
    }

    // createLeave: overlapping leave has days matching submitted dates -> conflict
    @Test
    void createLeave_conflictWithMatchingDaysInOverlappingLeave() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");

        LeaveDayEntry reqDay = new LeaveDayEntry();
        reqDay.setDate("2025-06-10");
        req.setDays(List.of(reqDay));
        req.setReason("Medical");

        LeaveDayEntry existingDay = new LeaveDayEntry();
        existingDay.setDate("2025-06-10");

        Leave existing = new Leave();
        existing.setId(2L);
        existing.setDays(new ArrayList<>(List.of(existingDay)));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        existing.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));

        assertThrows(Exception.class, () -> leaveService.createLeave(req, "emp@test.com"));
    }

    // createLeave: overlapping leave has days that do NOT match submitted dates -> no conflict
    @Test
    void createLeave_noConflict_overlappingLeaveDaysNotMatching() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");

        LeaveDayEntry reqDay = new LeaveDayEntry();
        reqDay.setDate("2025-06-10");
        req.setDays(List.of(reqDay));
        req.setReason("Medical");

        LeaveDayEntry existingDay = new LeaveDayEntry();
        existingDay.setDate("2025-06-11"); // different date

        Leave existing = new Leave();
        existing.setId(2L);
        existing.setDays(new ArrayList<>(List.of(existingDay)));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        existing.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(3L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertNotNull(result);
    }

    // createLeave: overlapping leave has REJECTED status -> filtered out -> no conflict
    @Test
    void createLeave_overlappingLeaveRejectedStatus_noConflict() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");

        LeaveDayEntry reqDay = new LeaveDayEntry();
        reqDay.setDate("2025-06-10");
        req.setDays(List.of(reqDay));
        req.setReason("Medical");

        Leave existing = new Leave();
        existing.setId(2L);
        existing.setDays(null);
        existing.setFromDate(LocalDate.of(2025, 6, 10));
        existing.setToDate(LocalDate.of(2025, 6, 10));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_REJECTED);
        existing.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(3L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertNotNull(result);
    }

    // createLeave: no days in request, conflict by date range
    @Test
    void createLeave_noDays_conflictByDateRange() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Casual");
        req.setFromDate("2025-06-10");
        req.setToDate("2025-06-12");
        req.setReason("Holiday");

        Leave existing = new Leave();
        existing.setId(2L);
        existing.setDays(null);
        existing.setFromDate(LocalDate.of(2025, 6, 10));
        existing.setToDate(LocalDate.of(2025, 6, 12));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        existing.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));

        assertThrows(Exception.class, () -> leaveService.createLeave(req, "emp@test.com"));
    }

    // createLeave: submitted date BEFORE existing leave range -> no match -> no conflict
    @Test
    void createLeave_daysBeforeExistingLeaveRange_noConflict() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");

        LeaveDayEntry reqDay = new LeaveDayEntry();
        reqDay.setDate("2025-06-05"); // before the existing leave range
        req.setDays(List.of(reqDay));
        req.setReason("Medical");

        Leave existing = new Leave();
        existing.setId(2L);
        existing.setDays(null);
        existing.setFromDate(LocalDate.of(2025, 6, 10));
        existing.setToDate(LocalDate.of(2025, 6, 12));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        existing.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(3L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertNotNull(result);
    }

    // getPendingLeavesFor: manager reviewer sees a regular employee's leave
    @Test
    void getPendingLeavesFor_managerSeesEmployeeLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setManagerEmail("mgr@test.com");
        leave.setTrail(null);

        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("mgr@test.com");

        assertEquals(1, result.size());
    }

    // createLeave: submitted date AFTER existing leave range -> no match -> no conflict
    @Test
    void createLeave_daysAfterExistingLeaveRange_noConflict() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");

        LeaveDayEntry reqDay = new LeaveDayEntry();
        reqDay.setDate("2025-06-20"); // after the existing leave range
        req.setDays(List.of(reqDay));
        req.setReason("Medical");

        Leave existing = new Leave();
        existing.setId(2L);
        existing.setDays(null);
        existing.setFromDate(LocalDate.of(2025, 6, 10));
        existing.setToDate(LocalDate.of(2025, 6, 12));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        existing.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(3L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertNotNull(result);
    }

    // hydrateTransients: hasAdminApproval=true, all days rejected
    @Test
    void hydrateTransients_adminApprovalWithAllRejectedDays() {
        Leave leave = new Leave();
        leave.setId(1L);

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_REJECTED);
        leave.setDays(List.of(day));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        leave.setTrail(List.of(trail));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        // all rejected, admin approval exists -> STATUS_REJECTED
        assertEquals(LeaveConstants.STATUS_REJECTED, result.getStatus());
    }
}
