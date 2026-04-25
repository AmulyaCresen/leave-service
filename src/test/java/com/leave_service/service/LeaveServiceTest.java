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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

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

    @Test
    void getAllHolidays_returnsAllHolidays() {
        Holiday h = new Holiday(); h.setId(1L); h.setName("Diwali");
        when(holidayRepository.findAll()).thenReturn(List.of(h));

        List<Holiday> result = leaveService.getAllHolidays();

        assertEquals(1, result.size());
        assertEquals("Diwali", result.get(0).getName());
    }

    @Test
    void createHoliday_success() {
        HolidayRequest req = new HolidayRequest(); req.setName("Holi"); req.setDate("2025-03-14");
        when(holidayRepository.findByDate(any())).thenReturn(Optional.empty());
        when(holidayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Holiday result = leaveService.createHoliday(req);

        assertEquals("Holi", result.getName());
        verify(holidayRepository).save(any());
    }

    @Test
    void createHoliday_conflict() {
        HolidayRequest req = new HolidayRequest(); req.setName("Holi"); req.setDate("2025-03-14");
        Holiday existing = new Holiday(); existing.setId(1L);
        when(holidayRepository.findByDate(any())).thenReturn(Optional.of(existing));

        assertThrows(ResponseStatusException.class, () -> leaveService.createHoliday(req));
    }

    @Test
    void updateHoliday_success() {
        HolidayRequest req = new HolidayRequest(); req.setName("Updated"); req.setDate("2025-03-15");
        Holiday existing = new Holiday(); existing.setId(1L); existing.setName("Old");
        when(holidayRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(holidayRepository.findByDate(any())).thenReturn(Optional.empty());
        when(holidayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Holiday result = leaveService.updateHoliday(1L, req);

        assertEquals("Updated", result.getName());
    }

    @Test
    void updateHoliday_notFound() {
        HolidayRequest req = new HolidayRequest(); req.setName("Updated"); req.setDate("2025-03-15");
        when(holidayRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> leaveService.updateHoliday(1L, req));
    }

    @Test
    void updateHoliday_dateConflict() {
        HolidayRequest req = new HolidayRequest(); req.setName("Updated"); req.setDate("2025-03-15");
        Holiday existing = new Holiday(); existing.setId(1L);
        Holiday conflicting = new Holiday(); conflicting.setId(2L);
        when(holidayRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(holidayRepository.findByDate(any())).thenReturn(Optional.of(conflicting));

        assertThrows(ResponseStatusException.class, () -> leaveService.updateHoliday(1L, req));
    }

    @Test
    void deleteHoliday_success() {
        when(holidayRepository.existsById(1L)).thenReturn(true);
        doNothing().when(holidayRepository).deleteById(1L);
        doNothing().when(holidayRepository).flush();
        when(entityManager.createNativeQuery(anyString())).thenReturn(mock(jakarta.persistence.Query.class));

        leaveService.deleteHoliday(1L);

        verify(holidayRepository).deleteById(1L);
        verify(entityManager).createNativeQuery(anyString());
    }

    @Test
    void deleteHoliday_notFound() {
        when(holidayRepository.existsById(1L)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> leaveService.deleteHoliday(1L));
    }

    @Test
    void getAllLeaveTypes_returnsAll() {
        LeaveType lt = new LeaveType(); lt.setId(1); lt.setLeaveName("Sick");
        when(leaveTypeRepository.findAll()).thenReturn(List.of(lt));

        List<LeaveType> result = leaveService.getAllLeaveTypes();

        assertEquals(1, result.size());
    }

    @Test
    void leaveNameExists_returnsTrue() {
        when(leaveTypeRepository.findByLeaveName("Sick")).thenReturn(Optional.of(new LeaveType()));

        assertTrue(leaveService.leaveNameExists("Sick"));
    }

    @Test
    void leaveUniqueNameExists_returnsFalse() {
        when(leaveTypeRepository.findByLeaveUniqueName("sick")).thenReturn(Optional.empty());

        assertFalse(leaveService.leaveUniqueNameExists("sick"));
    }

    @Test
    void createLeaveType_success() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Sick"); req.setLeaveUniqueName("sick"); req.setMaxDays(10);
        when(leaveTypeRepository.findByLeaveName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByLeaveUniqueName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveType result = leaveService.createLeaveType(req);

        assertEquals("Sick", result.getLeaveName());
    }

    @Test
    void createLeaveType_nameConflict() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Sick"); req.setLeaveUniqueName("sick");
        when(leaveTypeRepository.findByLeaveName("Sick")).thenReturn(Optional.of(new LeaveType()));

        assertThrows(ResponseStatusException.class, () -> leaveService.createLeaveType(req));
    }

    @Test
    void createLeaveType_uniqueNameConflict() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Sick"); req.setLeaveUniqueName("sick");
        when(leaveTypeRepository.findByLeaveName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByLeaveUniqueName("sick")).thenReturn(Optional.of(new LeaveType()));

        assertThrows(ResponseStatusException.class, () -> leaveService.createLeaveType(req));
    }

    @Test
    void updateLeaveType_success() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Updated"); req.setLeaveUniqueName("updated"); req.setMaxDays(12);
        LeaveType existing = new LeaveType(); existing.setId(1);
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.findByLeaveName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByLeaveUniqueName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveType result = leaveService.updateLeaveType(1, req);

        assertEquals("Updated", result.getLeaveName());
    }

    @Test
    void updateLeaveType_notFound() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeaveType(1, req));
    }

    @Test
    void deleteLeaveType_success() {
        when(leaveTypeRepository.existsById(1)).thenReturn(true);

        leaveService.deleteLeaveType(1);

        verify(leaveTypeRepository).deleteById(1);
    }

    @Test
    void deleteLeaveType_notFound() {
        when(leaveTypeRepository.existsById(1)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> leaveService.deleteLeaveType(1));
    }

    @Test
    void getLeaveById_success() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        leave.setTrail(new ArrayList<>());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void getLeaveById_notFound() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> leaveService.getLeaveById(1L));
    }

    @Test
    void getAllLeaves_returnsAll() {
        Leave leave = new Leave(); leave.setId(1L); leave.setTrail(new ArrayList<>());
        when(leaveRepository.findAll()).thenReturn(List.of(leave));

        List<Leave> result = leaveService.getAllLeaves();

        assertEquals(1, result.size());
    }

    @Test
    void getLeavesByEmail_returnsFiltered() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com"); leave.setTrail(new ArrayList<>());
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(leave));

        List<Leave> result = leaveService.getLeavesByEmail("emp@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getPendingLeavesFor_manager() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        leave.setManagerEmail("mgr@test.com"); leave.setTrail(new ArrayList<>());
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("mgr@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getReviewedLeavesByReviewer_returnsFiltered() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_REVIEWED_BY, "mgr@test.com");
        leave.setTrail(List.of(trail));
        when(leaveRepository.findAll()).thenReturn(List.of(leave));

        List<Leave> result = leaveService.getReviewedLeavesByReviewer("mgr@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getManagerLoggedLeaves_returnsData() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("mgr@test.com"); empLeave.setFullName("Manager");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of());
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getManagerLoggedLeaves("mgr@test.com");

        assertTrue(result.containsKey("approved_leaves"));
    }

    @Test
    void getManagerLoggedLeaves_notFound() {
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.empty());

        Map<String, Object> result = leaveService.getManagerLoggedLeaves("mgr@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void getAdminLoggedLeaves_returnsData() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com"); empLeave.setFullName("Admin");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of());
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");

        assertTrue(result.containsKey("approved_leaves"));
    }

    @Test
    void updateLeave_success() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual"); req.setFromDate("2025-06-05"); req.setToDate("2025-06-07");
        req.setReason("Personal"); req.setDayType("FULL_DAY");
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(new ArrayList<>(List.of(trail)));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Leave result = leaveService.updateLeave(1L, req, "emp@test.com");

        assertEquals("Casual", result.getLeaveType());
    }

    @Test
    void updateLeave_forbidden() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeave(1L, req, "other@test.com"));
    }

    @Test
    void updateLeave_notPending() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeave(1L, req, "emp@test.com"));
    }

    @Test
    void deleteLeave_success() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        leaveService.deleteLeave(1L, "emp@test.com");

        verify(leaveRepository).deleteById(1L);
    }

    @Test
    void deleteLeave_forbidden() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        assertThrows(ResponseStatusException.class, () -> leaveService.deleteLeave(1L, "other@test.com"));
    }

    @Test
    void createLeave_success() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick"); req.setFromDate("2025-06-10"); req.setToDate("2025-06-12");
        req.setReason("Medical"); req.setDayType("FULL_DAY"); req.setManagerEmail("mgr@test.com");
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of());
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertEquals("Sick", result.getLeaveType());
        verify(leaveRepository).save(any());
    }

    @Test
    void createLeave_withDays() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-10"); day.setDayType("FULL_DAY");
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
    }

    @Test
    void createLeave_conflict() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick"); req.setFromDate("2025-06-10"); req.setToDate("2025-06-12");
        req.setReason("Medical");
        Leave existing = new Leave(); existing.setId(2L);
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        existing.setTrail(List.of(trail));
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));

        assertThrows(ResponseStatusException.class, () -> leaveService.createLeave(req, "emp@test.com"));
    }

    @Test
    void createLeave_withDaysConflict() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-10"); day.setDayType("FULL_DAY");
        req.setDays(List.of(day));
        req.setReason("Medical");
        
        Leave existing = new Leave(); existing.setId(2L);
        LeaveDayEntry existingDay = new LeaveDayEntry();
        existingDay.setDate("2025-06-10");
        existing.setDays(List.of(existingDay));
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        existing.setTrail(List.of(trail));
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));

        assertThrows(ResponseStatusException.class, () -> leaveService.createLeave(req, "emp@test.com"));
    }

    @Test
    void createLeave_withHalfDay() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick"); req.setFromDate("2025-06-10"); req.setToDate("2025-06-10");
        req.setReason("Medical"); req.setDayType("HALF_DAY"); req.setHalfDaySession("MORNING");
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of());
        when(leaveRepository.save(any())).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });

        Leave result = leaveService.createLeave(req, "emp@test.com");

        assertNotNull(result);
    }

    @Test
    void createLeave_withDefaultManager() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick"); req.setFromDate("2025-06-10"); req.setToDate("2025-06-12");
        req.setReason("Medical");
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
    void updateLeave_withHalfDay() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual"); req.setFromDate("2025-06-05"); req.setToDate("2025-06-05");
        req.setReason("Personal"); req.setDayType("HALF_DAY"); req.setHalfDaySession("AFTERNOON");
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(new ArrayList<>(List.of(trail)));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Leave result = leaveService.updateLeave(1L, req, "emp@test.com");

        assertNotNull(result);
    }

    @Test
    void deleteLeave_notPending() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        assertThrows(ResponseStatusException.class, () -> leaveService.deleteLeave(1L, "emp@test.com"));
    }

    @Test
    void getPendingLeavesFor_admin() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        leave.setManagerEmail("mgr@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01"); day.setStatus(LeaveConstants.STATUS_APPROVED);
        day.setReviewStage(LeaveConstants.STAGE_MANAGER);
        leave.setDays(List.of(day));
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 1));
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_MANAGER_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_MANAGER);
        leave.setTrail(List.of(trail));
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getPendingLeavesFor_adminWithRejectedDays() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01"); day.setStatus(LeaveConstants.STATUS_REJECTED);
        leave.setDays(List.of(day));
        leave.setTrail(new ArrayList<>());
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(0, result.size());
    }

    @Test
    void getManagerLoggedLeaves_noApprovedLeaves() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("mgr@test.com");
        Map<String, Object> leaves = new HashMap<>();
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("mgr@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getManagerLoggedLeaves("mgr@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void getAdminLoggedLeaves_notFound() {
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.empty());

        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void hydrateTransients_withDaysAndTrail() {
        Leave leave = new Leave(); leave.setId(1L);
        LeaveDayEntry day1 = new LeaveDayEntry(); day1.setDate("2025-06-01"); day1.setStatus(LeaveConstants.STATUS_APPROVED); day1.setDayType("FULL_DAY");
        LeaveDayEntry day2 = new LeaveDayEntry(); day2.setDate("2025-06-02"); day2.setStatus(LeaveConstants.STATUS_REJECTED); day2.setDayType("HALF_DAY");
        leave.setDays(List.of(day1, day2));
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PARTIAL);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        trail.put(LeaveConstants.TRAIL_DAY_TYPE, "FULL_DAY");
        trail.put(LeaveConstants.TRAIL_REVIEWED_BY, "admin@test.com");
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PARTIAL, result.getStatus());
        assertEquals(1.5, result.getTotalDays());
    }

    @Test
    void hydrateTransients_withManagerApproval() {
        Leave leave = new Leave(); leave.setId(1L);
        LeaveDayEntry day = new LeaveDayEntry(); day.setDate("2025-06-01"); day.setStatus(LeaveConstants.STATUS_APPROVED);
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
    void hydrateTransients_allRejected() {
        Leave leave = new Leave(); leave.setId(1L);
        LeaveDayEntry day = new LeaveDayEntry(); day.setDate("2025-06-01"); day.setStatus(LeaveConstants.STATUS_REJECTED);
        leave.setDays(List.of(day));
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_REJECTED, result.getStatus());
    }

    @Test
    void getPendingLeavesFor_adminWithManagerRejection() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
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
    void getPendingLeavesFor_adminWithNoDays() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        leave.setDays(null);
        leave.setTrail(new ArrayList<>());
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());

        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getPendingLeavesFor_excludesOwnLeaves() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("mgr@test.com");
        leave.setManagerEmail("mgr@test.com"); leave.setTrail(new ArrayList<>());
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of(1L));

        List<Leave> result = leaveService.getPendingLeavesFor("mgr@test.com");

        assertEquals(0, result.size());
    }

    @Test
    void createLeave_withDaysConflictNoDays() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        LeaveDayEntry day = new LeaveDayEntry(); day.setDate("2025-06-10"); day.setDayType("FULL_DAY");
        req.setDays(List.of(day));
        req.setReason("Medical");
        
        Leave existing = new Leave(); existing.setId(2L);
        existing.setFromDate(LocalDate.of(2025, 6, 10));
        existing.setToDate(LocalDate.of(2025, 6, 10));
        existing.setDays(null);
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        existing.setTrail(List.of(trail));
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existing));

        assertThrows(ResponseStatusException.class, () -> leaveService.createLeave(req, "emp@test.com"));
    }

    @Test
    void getAdminLoggedLeaves_noApprovedLeaves() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com");
        Map<String, Object> leaves = new HashMap<>();
        empLeave.setLeaves(leaves);
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));

        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void hydrateTransients_withAdminApprovalAndPartialDays() {
        Leave leave = new Leave(); leave.setId(1L);
        LeaveDayEntry day1 = new LeaveDayEntry(); day1.setDate("2025-06-01"); day1.setStatus(LeaveConstants.STATUS_APPROVED);
        LeaveDayEntry day2 = new LeaveDayEntry(); day2.setDate("2025-06-02"); day2.setStatus(LeaveConstants.STATUS_REJECTED);
        leave.setDays(List.of(day1, day2));
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        trail.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PARTIAL, result.getStatus());
    }

    @Test
    void hydrateTransients_withPendingDays() {
        Leave leave = new Leave(); leave.setId(1L);
        LeaveDayEntry day = new LeaveDayEntry(); day.setDate("2025-06-01"); day.setStatus(null);
        leave.setDays(List.of(day));
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PENDING, result.getStatus());
    }

    @Test
    void hydrateTransients_noDaysNoTrail() {
        Leave leave = new Leave(); leave.setId(1L);
        leave.setDays(null);
        leave.setTrail(null);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PENDING, result.getStatus());
        assertEquals(LeaveConstants.DAY_TYPE_FULL, result.getDayType());
    }

    @Test
    void createLeave_withBlankManagerEmail() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick"); req.setFromDate("2025-06-10"); req.setToDate("2025-06-12");
        req.setReason("Medical"); req.setManagerEmail("");
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
    void getPendingLeavesFor_adminWithPendingDays() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        LeaveDayEntry day = new LeaveDayEntry(); day.setDate("2025-06-01"); day.setStatus(LeaveConstants.STATUS_PENDING);
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
    void hydrateTransients_withEmptyTrail() {
        Leave leave = new Leave(); leave.setId(1L);
        leave.setDays(null);
        leave.setTrail(new ArrayList<>());
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals(LeaveConstants.STATUS_PENDING, result.getStatus());
    }

    @Test
    void createLeave_withDaysHalfDay() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-10"); day.setDayType("HALF_DAY"); day.setHalfDaySession("MORNING");
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
    }

    @Test
    void updateLeave_withNullDayType() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual"); req.setFromDate("2025-06-05"); req.setToDate("2025-06-07");
        req.setReason("Personal"); req.setDayType(null);
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        leave.setTrail(new ArrayList<>(List.of(trail)));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Leave result = leaveService.updateLeave(1L, req, "emp@test.com");

        assertNotNull(result);
    }

    @Test
    void hydrateTransients_withHalfDaySession() {
        Leave leave = new Leave(); leave.setId(1L);
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        trail.put(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_HALF);
        trail.put(LeaveConstants.TRAIL_HALF_DAY_SESSION, "MORNING");
        leave.setTrail(List.of(trail));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Leave result = leaveService.getLeaveById(1L);

        assertEquals("MORNING", result.getHalfDaySession());
    }

    @Test
    void getPendingLeavesFor_adminWithApprovedDaysNoReviewStage() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01"); day.setStatus(LeaveConstants.STATUS_APPROVED);
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
    void hydrateTransients_allApprovedWithAdminStage() {
        Leave leave = new Leave(); leave.setId(1L);
        LeaveDayEntry day = new LeaveDayEntry(); day.setDate("2025-06-01"); day.setStatus(LeaveConstants.STATUS_APPROVED);
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
    void updateHoliday_sameDateAllowed() {
        HolidayRequest req = new HolidayRequest(); req.setName("Updated"); req.setDate("2025-03-15");
        Holiday existing = new Holiday(); existing.setId(1L);
        when(holidayRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(holidayRepository.findByDate(any())).thenReturn(Optional.of(existing));
        when(holidayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Holiday result = leaveService.updateHoliday(1L, req);

        assertEquals("Updated", result.getName());
    }

    @Test
    void updateLeaveType_sameName() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Sick"); req.setLeaveUniqueName("sick"); req.setMaxDays(12);
        LeaveType existing = new LeaveType(); existing.setId(1); existing.setLeaveName("Sick");
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.findByLeaveName("Sick")).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.findByLeaveUniqueName("sick")).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveType result = leaveService.updateLeaveType(1, req);

        assertEquals("Sick", result.getLeaveName());
    }

    @Test
    void updateLeaveType_nameConflict() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Casual"); req.setLeaveUniqueName("casual");
        LeaveType existing = new LeaveType(); existing.setId(1);
        LeaveType conflicting = new LeaveType(); conflicting.setId(2);
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.findByLeaveName("Casual")).thenReturn(Optional.of(conflicting));

        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeaveType(1, req));
    }

    @Test
    void updateLeaveType_uniqueNameConflict() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Casual"); req.setLeaveUniqueName("casual");
        LeaveType existing = new LeaveType(); existing.setId(1);
        LeaveType conflicting = new LeaveType(); conflicting.setId(2);
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(leaveTypeRepository.findByLeaveName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByLeaveUniqueName("casual")).thenReturn(Optional.of(conflicting));

        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeaveType(1, req));
    }

    @Test
    void getReviewedLeavesByReviewer_excludesOwnLeaves() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("mgr@test.com");
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_REVIEWED_BY, "mgr@test.com");
        leave.setTrail(List.of(trail));
        when(leaveRepository.findAll()).thenReturn(List.of(leave));

        List<Leave> result = leaveService.getReviewedLeavesByReviewer("mgr@test.com");

        assertEquals(0, result.size());
    }

    @Test
    void getReviewedLeavesByReviewer_noTrail() {
        Leave leave = new Leave(); leave.setId(1L); leave.setEmailId("emp@test.com");
        leave.setTrail(null);
        when(leaveRepository.findAll()).thenReturn(List.of(leave));

        List<Leave> result = leaveService.getReviewedLeavesByReviewer("mgr@test.com");

        assertEquals(0, result.size());
    }

    @Test
    void updateLeave_notFound() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        when(leaveRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeave(1L, req, "emp@test.com"));
    }

    @Test
    void deleteLeave_notFound() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> leaveService.deleteLeave(1L, "emp@test.com"));
    }
}
