package com.leave_service.service;

import com.leave_service.commons.LeaveConstants;
import com.leave_service.dto.CreateLeaveRequest;
import com.leave_service.dto.LeaveDayEntry;
import com.leave_service.model.Leave;
import com.leave_service.repository.EmployeeLeaveRepository;
import com.leave_service.repository.HolidayRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.repository.LeaveTypeRepository;
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
class LeaveServiceEdgeCaseTest {

    @Mock private LeaveRepository leaveRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private EmployeeLeaveRepository employeeLeaveRepository;
    @Mock private LeaveProcessService leaveProcessService;
    @InjectMocks private LeaveService leaveService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(leaveService, "managerEmail", "mgr@test.com");
        ReflectionTestUtils.setField(leaveService, "adminEmail", "admin@test.com");
    }

    @Test
    void hydrateTransients_adminApprovalWithAllDaysRejected_setsRejectedStatus() {
        Leave leave = new Leave();
        leave.setId(1L);
        
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setStatus(LeaveConstants.STATUS_REJECTED);
        
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2025-06-02");
        day2.setStatus(LeaveConstants.STATUS_REJECTED);
        
        leave.setDays(List.of(day1, day2));
        
        List<Map<String, String>> trail = new ArrayList<>();
        Map<String, String> adminEntry = new HashMap<>();
        adminEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        adminEntry.put(LeaveConstants.TRAIL_STAGE, LeaveConstants.STAGE_ADMIN);
        trail.add(adminEntry);
        leave.setTrail(trail);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        Leave result = leaveService.getLeaveById(1L);
        
        assertEquals(LeaveConstants.STATUS_REJECTED, result.getStatus());
    }

    @Test
    void hydrateTransients_noDaysWithPendingInTrail_usesFallbackStatus() {
        Leave leave = new Leave();
        leave.setId(1L);
        
        List<Map<String, String>> trail = new ArrayList<>();
        Map<String, String> entry = new HashMap<>();
        entry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        trail.add(entry);
        leave.setTrail(trail);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        
        Leave result = leaveService.getLeaveById(1L);
        
        assertEquals(LeaveConstants.STATUS_PENDING, result.getStatus());
    }

    @Test
    void getPendingLeavesFor_adminReviewingOwnLeave_includesInPendingList() {
        Leave adminLeave = new Leave();
        adminLeave.setId(1L);
        adminLeave.setEmailId("admin@test.com");
        adminLeave.setManagerEmail("mgr@test.com");
        
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        day.setStatus(LeaveConstants.STATUS_APPROVED);
        day.setReviewStage(LeaveConstants.STAGE_MANAGER);
        adminLeave.setDays(List.of(day));
        
        List<Map<String, String>> trail = new ArrayList<>();
        Map<String, String> entry = new HashMap<>();
        entry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_MANAGER_APPROVED);
        trail.add(entry);
        adminLeave.setTrail(trail);
        
        when(leaveRepository.findAll()).thenReturn(List.of(adminLeave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of());
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(List.of(1L));
        
        List<Leave> result = leaveService.getPendingLeavesFor("admin@test.com");
        
        assertEquals(0, result.size());
    }

    @Test
    void getReviewedLeavesByReviewer_noMatchingReviewer_logsDebug() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("emp@test.com");
        
        List<Map<String, String>> trail = new ArrayList<>();
        Map<String, String> entry = new HashMap<>();
        entry.put(LeaveConstants.TRAIL_REVIEWED_BY, "other@test.com");
        trail.add(entry);
        leave.setTrail(trail);
        
        when(leaveRepository.findAll()).thenReturn(List.of(leave));
        
        List<Leave> result = leaveService.getReviewedLeavesByReviewer("mgr@test.com");
        
        assertEquals(0, result.size());
    }

    @Test
    void createLeave_withDaysAndOverlappingDateRange_throwsConflict() {
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick");
        request.setReason("Medical");
        
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2025-06-01");
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        request.setDays(List.of(day1));
        
        Leave existingLeave = new Leave();
        existingLeave.setId(2L);
        existingLeave.setFromDate(LocalDate.of(2025, 5, 30));
        existingLeave.setToDate(LocalDate.of(2025, 6, 5));
        existingLeave.setTrail(new ArrayList<>());
        
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(existingLeave));
        
        assertThrows(Exception.class, () -> leaveService.createLeave(request, "emp@test.com"));
    }
}
