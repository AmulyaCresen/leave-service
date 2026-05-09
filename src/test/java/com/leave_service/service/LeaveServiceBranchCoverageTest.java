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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional branch coverage tests for LeaveService targeting missed branches.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveServiceBranchCoverageTest {

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

    // ── updateLeaveType conflict checks (lambda$updateLeaveType$11, $13) ─────

    @Test
    void updateLeaveType_leaveNameConflictWithDifferentId_throwsConflict() {
        LeaveType existing = new LeaveType();
        existing.setId(1);
        existing.setLeaveName("Sick Leave");
        existing.setLeaveUniqueName("SICK");

        LeaveType conflicting = new LeaveType();
        conflicting.setId(2); // Different ID
        conflicting.setLeaveName("Sick Leave");

        CreateLeaveTypeRequest request = new CreateLeaveTypeRequest();
        request.setLeaveName("Sick Leave");
        request.setLeaveUniqueName("SICK_NEW");
        request.setDescription("Sick");
        request.setMaxDays(10);

        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.findByLeaveName("Sick Leave")).thenReturn(Optional.of(conflicting));

        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeaveType(1, request));
    }

    @Test
    void updateLeaveType_leaveNameSameId_noConflict() {
        // Same ID as the one being updated → no conflict
        LeaveType existing = new LeaveType();
        existing.setId(1);
        existing.setLeaveName("Sick Leave");
        existing.setLeaveUniqueName("SICK");

        CreateLeaveTypeRequest request = new CreateLeaveTypeRequest();
        request.setLeaveName("Sick Leave");
        request.setLeaveUniqueName("SICK");
        request.setDescription("Updated");
        request.setMaxDays(15);

        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        // Returns same existing object with same ID → filter removes it → no conflict
        when(leaveTypeRepository.findByLeaveName("Sick Leave")).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.findByLeaveUniqueName("SICK")).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.save(any())).thenReturn(existing);

        assertDoesNotThrow(() -> leaveService.updateLeaveType(1, request));
    }

    @Test
    void updateLeaveType_uniqueNameConflictWithDifferentId_throwsConflict() {
        LeaveType existing = new LeaveType();
        existing.setId(1);
        existing.setLeaveName("Sick Leave");
        existing.setLeaveUniqueName("SICK");

        LeaveType conflicting = new LeaveType();
        conflicting.setId(2);
        conflicting.setLeaveUniqueName("SICK");

        CreateLeaveTypeRequest request = new CreateLeaveTypeRequest();
        request.setLeaveName("New Name");
        request.setLeaveUniqueName("SICK");
        request.setDescription("Desc");
        request.setMaxDays(10);

        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.findByLeaveName("New Name")).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByLeaveUniqueName("SICK")).thenReturn(Optional.of(conflicting));

        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeaveType(1, request));
    }

    // ── updateHoliday conflict check (lambda$updateHoliday$8) ────────────────

    @Test
    void updateHoliday_dateConflictWithDifferentId_throwsConflict() {
        Holiday existing = new Holiday();
        existing.setId(1L);
        existing.setName("New Year");
        existing.setDate(LocalDate.of(2025, 1, 1));

        Holiday conflicting = new Holiday();
        conflicting.setId(2L); // Different ID
        conflicting.setDate(LocalDate.of(2025, 1, 2));

        HolidayRequest request = new HolidayRequest();
        request.setName("New Year Eve");
        request.setDate("2025-01-02");

        when(holidayRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(holidayRepository.findByDate(LocalDate.of(2025, 1, 2))).thenReturn(Optional.of(conflicting));

        assertThrows(ResponseStatusException.class, () -> leaveService.updateHoliday(1L, request));
    }

    @Test
    void updateHoliday_dateSameId_noConflict() {
        // Same holiday ID, same date → no conflict
        Holiday existing = new Holiday();
        existing.setId(1L);
        existing.setName("New Year");
        existing.setDate(LocalDate.of(2025, 1, 1));

        HolidayRequest request = new HolidayRequest();
        request.setName("New Year Updated");
        request.setDate("2025-01-01");

        when(holidayRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(holidayRepository.findByDate(LocalDate.of(2025, 1, 1))).thenReturn(Optional.of(existing));
        when(holidayRepository.save(any())).thenReturn(existing);

        assertDoesNotThrow(() -> leaveService.updateHoliday(1L, request));
    }

    // ── getLeaveBalance with null leaves (lambda$getLeaveBalance$37) ──────────

    @Test
    void getLeaveBalance_empLeaveFoundButNullLeaves_returnsEmpty() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("emp@test.com");
        empLeave.setFullName("Test Employee");
        empLeave.setLeaves(null); // null leaves

        when(employeeLeaveRepository.findByEmailId("emp@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getLeaveBalance("emp@test.com");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── getAdminLoggedLeaves with fullLeave found (lambda$getAdminLoggedLeaves$29) ─

    @Test
    void getAdminLoggedLeaves_withLeaveFound_enrichesRecord() {
        Leave fullLeave = new Leave();
        fullLeave.setId(1L);
        fullLeave.setEmailId("emp@test.com");
        fullLeave.setReason("Medical");
        fullLeave.setTrail(new ArrayList<>());

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com");
        empLeave.setFullName("Admin User");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of(Map.of("leaveId", 1)));
        empLeave.setLeaves(leaves);

        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(fullLeave));

        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");

        assertFalse(result.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enrichedLeaves = (List<Map<String, Object>>) result.get("approved_leaves");
        assertNotNull(enrichedLeaves);
        // The enriched leave should have trail data
        assertTrue(enrichedLeaves.get(0).containsKey("trail"));
    }

    @Test
    void getAdminLoggedLeaves_withLeaveNotFound_returnsRecord() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com");
        empLeave.setFullName("Admin User");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of(Map.of("leaveId", 99)));
        empLeave.setLeaves(leaves);

        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(leaveRepository.findById(99L)).thenReturn(Optional.empty()); // not found

        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");

        assertFalse(result.isEmpty());
        // Record is returned as-is when leave not found
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enrichedLeaves = (List<Map<String, Object>>) result.get("approved_leaves");
        assertNotNull(enrichedLeaves);
    }

    @Test
    void getAdminLoggedLeaves_empLeaveFoundNoApprovedKey_returnsEmpty() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com");
        empLeave.setFullName("Admin User");
        empLeave.setLeaves(new HashMap<>()); // No "approved_leaves" key

        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");

        assertTrue(result.isEmpty());
    }

    // ── getManagerLoggedLeaves with fullLeave found (lambda$getManagerLoggedLeaves$26) ─

    @Test
    void getManagerLoggedLeaves_withLeaveFound_enrichesRecord() {
        Leave fullLeave = new Leave();
        fullLeave.setId(1L);
        fullLeave.setEmailId("emp@test.com");
        fullLeave.setReason("Personal");
        fullLeave.setTrail(new ArrayList<>());

        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("manager@test.com");
        empLeave.setFullName("Manager User");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of(Map.of("leaveId", 1)));
        empLeave.setLeaves(leaves);

        when(employeeLeaveRepository.findByEmailId("manager@test.com")).thenReturn(Optional.of(empLeave));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(fullLeave));

        Map<String, Object> result = leaveService.getManagerLoggedLeaves("manager@test.com");

        assertFalse(result.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enrichedLeaves = (List<Map<String, Object>>) result.get("approved_leaves");
        assertNotNull(enrichedLeaves);
        assertTrue(enrichedLeaves.get(0).containsKey("trail"));
    }

    @Test
    void getManagerLoggedLeaves_withLeaveNotFound_returnsRecord() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("manager@test.com");
        empLeave.setFullName("Manager User");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of(Map.of("leaveId", 99)));
        empLeave.setLeaves(leaves);

        when(employeeLeaveRepository.findByEmailId("manager@test.com")).thenReturn(Optional.of(empLeave));
        when(leaveRepository.findById(99L)).thenReturn(Optional.empty());

        Map<String, Object> result = leaveService.getManagerLoggedLeaves("manager@test.com");

        assertFalse(result.isEmpty());
    }

    @Test
    void getManagerLoggedLeaves_empLeaveFoundNoApprovedKey_returnsEmpty() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("manager@test.com");
        empLeave.setFullName("Manager User");
        empLeave.setLeaves(new HashMap<>());

        when(employeeLeaveRepository.findByEmailId("manager@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getManagerLoggedLeaves("manager@test.com");

        assertTrue(result.isEmpty());
    }

    // ── hydrateTransients missed branches ────────────────────────────────────

    @Test
    void hydrateTransients_adminApproval_allDaysRejected_setsRejected() {
        // hasAdminApproval=true AND rejectedCount == days.size() → STATUS_REJECTED
        Leave leave = new Leave();
        leave.setId(1L);

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_REJECTED);
        day.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(List.of(day));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        leave.setTrail(List.of(trail));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_REJECTED, result.getStatus());
    }

    @Test
    void hydrateTransients_adminApproval_allDaysApproved_setsApproved() {
        // hasAdminApproval=true AND all days approved → else branch → STATUS_APPROVED
        Leave leave = new Leave();
        leave.setId(1L);

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        day.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(List.of(day));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        leave.setTrail(List.of(trail));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_APPROVED, result.getStatus());
    }

    @Test
    void hydrateTransients_managerApprovedStatus_inTrail() {
        // STATUS_MANAGER_APPROVED in trail → hasManagerApproval=true via first OR condition
        Leave leave = new Leave();
        leave.setId(1L);

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        day.setDayType(LeaveConstants.DAY_TYPE_FULL);
        leave.setDays(List.of(day));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_MANAGER_APPROVED);
        // No TRAIL_STAGE → doesn't match STAGE_ADMIN condition
        leave.setTrail(List.of(trail));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_MANAGER_APPROVED, result.getStatus());
    }

    @Test
    void hydrateTransients_totalDays_halfDayType() {
        // lambda$hydrateTransients$3: DAY_TYPE_HALF → 0.5
        Leave leave = new Leave();
        leave.setId(1L);

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        day.setDayType(LeaveConstants.DAY_TYPE_HALF);
        leave.setDays(List.of(day));

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        leave.setTrail(List.of(trail));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(0.5, result.getTotalDays());
    }

    // ── deleteLeave non-pending (1 missed branch) ─────────────────────────────

    @Test
    void deleteLeave_approvedLeave_throwsBadRequest() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        leave.setTrail(List.of(trail));

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        assertThrows(ResponseStatusException.class, () -> leaveService.deleteLeave(1L, "emp@test.com"));
    }

    // ── createLeave overlap check with no days in overlapping leave ───────────

    @Test
    void createLeave_withRequestDays_overlappingLeaveNoDays_dateRangeConflict() {
        // Request has days, overlapping leave has no days → uses date range comparison
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick");
        request.setReason("Medical");

        LeaveDayEntry reqDay = new LeaveDayEntry();
        reqDay.setDate("2025-06-10");
        reqDay.setDayType(LeaveConstants.DAY_TYPE_FULL);
        request.setDays(List.of(reqDay));

        // Overlapping leave has no days (uses fromDate/toDate)
        Leave overlapping = new Leave();
        overlapping.setId(2L);
        overlapping.setFromDate(LocalDate.of(2025, 6, 9));
        overlapping.setToDate(LocalDate.of(2025, 6, 11));
        overlapping.setDays(null); // No day entries

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        overlapping.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong()))
            .thenReturn(List.of(overlapping));

        assertThrows(ResponseStatusException.class,
            () -> leaveService.createLeave(request, "emp@test.com"));
    }

    @Test
    void createLeave_withRequestDays_overlappingLeaveNoDays_noDateRangeConflict() {
        // Request has days for a different date range → no conflict
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick");
        request.setReason("Medical");

        LeaveDayEntry reqDay = new LeaveDayEntry();
        reqDay.setDate("2025-06-15"); // Outside the overlapping range
        reqDay.setDayType(LeaveConstants.DAY_TYPE_FULL);
        request.setDays(List.of(reqDay));

        // Overlapping leave covers 9-11, request is for 15
        Leave overlapping = new Leave();
        overlapping.setId(2L);
        overlapping.setFromDate(LocalDate.of(2025, 6, 9));
        overlapping.setToDate(LocalDate.of(2025, 6, 11));
        overlapping.setDays(null);

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        overlapping.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong()))
            .thenReturn(List.of(overlapping));
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(3L);
            return l;
        });

        Leave result = leaveService.createLeave(request, "emp@test.com");
        assertNotNull(result);
    }

    @Test
    void createLeave_withRequestDays_overlappingStatusApproved_conflict() {
        // Tests lambda$createLeave$35: STATUS_APPROVED filter branch
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick");
        request.setReason("Medical");

        LeaveDayEntry reqDay = new LeaveDayEntry();
        reqDay.setDate("2025-06-10");
        reqDay.setDayType(LeaveConstants.DAY_TYPE_FULL);
        request.setDays(List.of(reqDay));

        LeaveDayEntry existingDay = new LeaveDayEntry();
        existingDay.setDate("2025-06-10");

        Leave overlapping = new Leave();
        overlapping.setId(2L);
        overlapping.setDays(List.of(existingDay));

        // Trail has APPROVED status (not PENDING)
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        overlapping.setTrail(List.of(trail));

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong()))
            .thenReturn(List.of(overlapping));

        assertThrows(ResponseStatusException.class,
            () -> leaveService.createLeave(request, "emp@test.com"));
    }

    @Test
    void createLeave_withManagerEmailBlank_usesDefaultManager() {
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick");
        request.setFromDate("2025-06-01");
        request.setToDate("2025-06-03");
        request.setReason("Medical");
        request.setManagerEmail(""); // blank → use default

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong()))
            .thenReturn(Collections.emptyList());
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });

        Leave result = leaveService.createLeave(request, "emp@test.com");

        assertNotNull(result);
        assertEquals("manager@test.com", result.getManagerEmail());
    }

    // ── getPendingLeavesFor admin with manager-approved days ─────────────────

    @Test
    void getPendingLeavesFor_adminLeaveWithManagerApprovedDays_includesLeave() {
        // Days have STAGE_MANAGER + STATUS_APPROVED → approvedDays NOT empty → no second filter
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        day.setReviewStage(LeaveConstants.STAGE_MANAGER);

        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 1));
        leave.setDays(new ArrayList<>(List.of(day)));
        leave.setTrail(null);

        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN))
            .thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER))
            .thenReturn(Collections.emptyList());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getPendingLeavesFor_adminLeaveWithAllRejectedDays_emptySecondFilter() {
        // All days are rejected → second filter (non-rejected) returns empty → return false
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_REJECTED);
        day.setReviewStage(LeaveConstants.STAGE_MANAGER);

        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setDays(new ArrayList<>(List.of(day)));
        leave.setTrail(null);

        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN))
            .thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER))
            .thenReturn(Collections.emptyList());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(0, result.size()); // All rejected → second filter empty → excluded
    }

    @Test
    void getPendingLeavesFor_adminLeaveWithNonRejectedDays_includesLeave() {
        // Days have PENDING status → first approvedDays is empty (no STAGE_MANAGER+APPROVED)
        // → second filter: not-rejected → includes these days → approvedDays not empty → include
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_PENDING);
        day.setReviewStage(null); // No review stage

        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 1));
        leave.setDays(new ArrayList<>(List.of(day)));
        leave.setTrail(null);

        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN))
            .thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER))
            .thenReturn(Collections.emptyList());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getPendingLeavesFor_adminLeaveNotInAdminTaskIds_returnsFalse() {
        // Leave is NOT in adminTaskLeaveIds → first check returns false
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setTrail(null);

        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN))
            .thenReturn(Collections.emptyList()); // Leave ID 1 NOT in admin tasks
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER))
            .thenReturn(Collections.emptyList());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(0, result.size());
    }

    @Test
    void getPendingLeavesFor_nonAdminNonManager_noMatchingTask() {
        // lambda$getPendingLeavesFor$18: manager check covers partial branches
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        leave.setManagerEmail("other@test.com"); // different manager

        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER))
            .thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN))
            .thenReturn(Collections.emptyList());

        // Email "reviewer@test.com" is not admin, not manager of leave → excluded
        List<Leave> result = leaveService.getPendingLeavesFor("reviewer@test.com");

        assertEquals(0, result.size());
    }

    // ── appendTrail: existing trail has TYPE_LEAVE_APPLIED with empty check ──

    @Test
    void createLeave_appendTrail_existingTrailEmpty_addsTypeLeaveApplied() {
        // Empty trail → noneMatch(TYPE_LEAVE_APPLIED) → adds type field
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick");
        request.setFromDate("2025-06-01");
        request.setToDate("2025-06-03");
        request.setReason("Medical");
        request.setDayType(LeaveConstants.DAY_TYPE_FULL);

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong()))
            .thenReturn(Collections.emptyList());
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(5L);
            return l;
        });

        Leave result = leaveService.createLeave(request, "emp@test.com");

        assertNotNull(result);
        // Trail should have been created with TYPE_LEAVE_APPLIED
        Map<String, String> trailEntry = result.getTrail().get(0);
        assertEquals(LeaveConstants.TYPE_LEAVE_APPLIED, trailEntry.get("type"));
    }

    // ── getReviewedLeavesByReviewer: reviewer is same as emailId ─────────────

    @Test
    void getReviewedLeavesByReviewer_reviewerIsLeaveOwner_excluded() {
        // lambda$getReviewedLeavesByReviewer$23: reviewer == emailId → excluded
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("mgr@test.com"); // Same as reviewer

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_REVIEWED_BY, "mgr@test.com");
        leave.setTrail(List.of(trail));

        when(leaveRepository.findAll()).thenReturn(List.of(leave));

        List<Leave> result = leaveService.getReviewedLeavesByReviewer("mgr@test.com");

        assertEquals(0, result.size()); // Own leave excluded
    }

    @Test
    void getReviewedLeavesByReviewer_withNullTrailEntry_notCounted() {
        // lambda$getReviewedLeavesByReviewer$25: trail entry has null reviewedBy
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");

        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        // No TRAIL_REVIEWED_BY → null → reviewedBy.equals fails quietly
        leave.setTrail(List.of(trail));

        when(leaveRepository.findAll()).thenReturn(List.of(leave));

        List<Leave> result = leaveService.getReviewedLeavesByReviewer("mgr@test.com");

        assertEquals(0, result.size()); // Not reviewed by mgr
    }

    // ── appendTrail with half-day in createLeave ──────────────────────────────

    @Test
    void createLeave_withHalfDayType_setsHalfDaySession() {
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick");
        request.setFromDate("2025-06-01");
        request.setToDate("2025-06-01");
        request.setReason("Doctor");
        request.setDayType(LeaveConstants.DAY_TYPE_HALF);
        request.setHalfDaySession("MORNING");

        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong()))
            .thenReturn(Collections.emptyList());
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(6L);
            return l;
        });

        Leave result = leaveService.createLeave(request, "emp@test.com");

        assertNotNull(result);
        // Trail entry should have HALF_DAY_SESSION
        Map<String, String> trailEntry = result.getTrail().get(0);
        assertEquals("MORNING", trailEntry.get(LeaveConstants.TRAIL_HALF_DAY_SESSION));
    }

    // ── setDocumentPath with leave not found ──────────────────────────────────

    @Test
    void setDocumentPath_leaveNotFound_throwsNotFound() {
        when(leaveRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
            () -> leaveService.setDocumentPath(999L, "/path/doc.pdf"));
    }
}
