package com.leave_service.service;

import com.leave_service.commons.LeaveConstants;
import com.leave_service.dto.CreateLeaveRequest;
import com.leave_service.dto.CreateLeaveTypeRequest;
import com.leave_service.dto.HolidayRequest;
import com.leave_service.dto.LeaveDayEntry;
import com.leave_service.dto.UpdateLeaveRequest;
import com.leave_service.model.Holiday;
import com.leave_service.model.Leave;
import com.leave_service.model.LeaveType;
import com.leave_service.model.EmployeeLeave;
import com.leave_service.repository.EmployeeLeaveRepository;
import com.leave_service.repository.HolidayRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.repository.LeaveTypeRepository;
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
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private EmployeeLeaveRepository employeeLeaveRepository;

    @Mock
    private LeaveProcessService leaveProcessService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private LeaveService leaveService;

    private Leave testLeave;
    private LeaveType testLeaveType;
    private Holiday testHoliday;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(leaveService, "managerEmail", "manager@test.com");
        ReflectionTestUtils.setField(leaveService, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(leaveService, "entityManager", entityManager);

        testLeave = new Leave();
        testLeave.setId(1L);
        testLeave.setEmailId("employee@test.com");
        testLeave.setLeaveType("Sick Leave");
        testLeave.setFromDate(LocalDate.now());
        testLeave.setToDate(LocalDate.now().plusDays(2));
        testLeave.setReason("Medical");
        testLeave.setManagerEmail("manager@test.com");
        testLeave.setEditable(true);
        testLeave.setCreatedAt(LocalDate.now());
        
        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING);
        trailEntry.put(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_FULL);
        trailEntry.put(LeaveConstants.TRAIL_DATE, "2024-01-01 10:00:00");
        testLeave.setTrail(new java.util.ArrayList<>(List.of(trailEntry)));

        testLeaveType = new LeaveType();
        testLeaveType.setId(1);
        testLeaveType.setLeaveName("Sick Leave");
        testLeaveType.setLeaveUniqueName("SICK");
        testLeaveType.setDescription("Sick leave");
        testLeaveType.setMaxDays(10);
        testLeaveType.setCreatedAt(OffsetDateTime.now());

        testHoliday = new Holiday();
        testHoliday.setId(1L);
        testHoliday.setName("New Year");
        testHoliday.setDate(LocalDate.of(2024, 1, 1));
    }

    @Test
    void testGetAllHolidays() {
        when(holidayRepository.findAll()).thenReturn(List.of(testHoliday));
        
        List<Holiday> holidays = leaveService.getAllHolidays();
        
        assertNotNull(holidays);
        assertEquals(1, holidays.size());
        assertEquals("New Year", holidays.get(0).getName());
        verify(holidayRepository).findAll();
    }

    @Test
    void testCreateHoliday_Success() {
        HolidayRequest request = new HolidayRequest();
        request.setName("Christmas");
        request.setDate("2024-12-25");
        
        when(holidayRepository.findByDate(any())).thenReturn(Optional.empty());
        when(holidayRepository.save(any())).thenReturn(testHoliday);
        
        Holiday created = leaveService.createHoliday(request);
        
        assertNotNull(created);
        verify(holidayRepository).save(any(Holiday.class));
    }

    @Test
    void testCreateHoliday_Conflict() {
        HolidayRequest request = new HolidayRequest();
        request.setName("Christmas");
        request.setDate("2024-12-25");
        
        when(holidayRepository.findByDate(any())).thenReturn(Optional.of(testHoliday));
        
        assertThrows(ResponseStatusException.class, () -> leaveService.createHoliday(request));
    }

    @Test
    void testUpdateHoliday_Success() {
        HolidayRequest request = new HolidayRequest();
        request.setName("Updated Holiday");
        request.setDate("2024-12-26");
        
        when(holidayRepository.findById(1L)).thenReturn(Optional.of(testHoliday));
        when(holidayRepository.findByDate(any())).thenReturn(Optional.empty());
        when(holidayRepository.save(any())).thenReturn(testHoliday);
        
        Holiday updated = leaveService.updateHoliday(1L, request);
        
        assertNotNull(updated);
        verify(holidayRepository).save(any(Holiday.class));
    }

    @Test
    void testUpdateHoliday_NotFound() {
        HolidayRequest request = new HolidayRequest();
        request.setName("Updated Holiday");
        request.setDate("2024-12-26");
        
        when(holidayRepository.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> leaveService.updateHoliday(1L, request));
    }

    @Test
    void testDeleteHoliday_Success() {
        when(holidayRepository.existsById(1L)).thenReturn(true);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mock(jakarta.persistence.Query.class));
        
        assertDoesNotThrow(() -> leaveService.deleteHoliday(1L));
        
        verify(holidayRepository).deleteById(1L);
        verify(holidayRepository).flush();
    }

    @Test
    void testDeleteHoliday_NotFound() {
        when(holidayRepository.existsById(1L)).thenReturn(false);
        
        assertThrows(ResponseStatusException.class, () -> leaveService.deleteHoliday(1L));
    }

    @Test
    void testGetAllLeaveTypes() {
        when(leaveTypeRepository.findAll()).thenReturn(List.of(testLeaveType));
        
        List<LeaveType> leaveTypes = leaveService.getAllLeaveTypes();
        
        assertNotNull(leaveTypes);
        assertEquals(1, leaveTypes.size());
        assertEquals("Sick Leave", leaveTypes.get(0).getLeaveName());
    }

    @Test
    void testLeaveNameExists() {
        when(leaveTypeRepository.findByLeaveName("Sick Leave")).thenReturn(Optional.of(testLeaveType));
        
        assertTrue(leaveService.leaveNameExists("Sick Leave"));
        assertFalse(leaveService.leaveNameExists("Casual Leave"));
    }

    @Test
    void testLeaveUniqueNameExists() {
        when(leaveTypeRepository.findByLeaveUniqueName("SICK")).thenReturn(Optional.of(testLeaveType));
        
        assertTrue(leaveService.leaveUniqueNameExists("SICK"));
        assertFalse(leaveService.leaveUniqueNameExists("CASUAL"));
    }

    @Test
    void testCreateLeaveType_Success() {
        CreateLeaveTypeRequest request = new CreateLeaveTypeRequest();
        request.setLeaveName("Casual Leave");
        request.setLeaveUniqueName("CASUAL");
        request.setDescription("Casual leave");
        request.setMaxDays(12);
        
        when(leaveTypeRepository.findByLeaveName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByLeaveUniqueName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.save(any())).thenReturn(testLeaveType);
        
        LeaveType created = leaveService.createLeaveType(request);
        
        assertNotNull(created);
        verify(leaveTypeRepository).save(any(LeaveType.class));
    }

    @Test
    void testCreateLeaveType_NameConflict() {
        CreateLeaveTypeRequest request = new CreateLeaveTypeRequest();
        request.setLeaveName("Sick Leave");
        request.setLeaveUniqueName("SICK");
        request.setDescription("Sick leave");
        request.setMaxDays(10);
        
        when(leaveTypeRepository.findByLeaveName("Sick Leave")).thenReturn(Optional.of(testLeaveType));
        
        assertThrows(ResponseStatusException.class, () -> leaveService.createLeaveType(request));
    }

    @Test
    void testCreateLeaveType_UniqueNameConflict() {
        CreateLeaveTypeRequest request = new CreateLeaveTypeRequest();
        request.setLeaveName("Sick Leave");
        request.setLeaveUniqueName("SICK");
        request.setDescription("Sick leave");
        request.setMaxDays(10);
        
        when(leaveTypeRepository.findByLeaveName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByLeaveUniqueName("SICK")).thenReturn(Optional.of(testLeaveType));
        
        assertThrows(ResponseStatusException.class, () -> leaveService.createLeaveType(request));
    }

    @Test
    void testUpdateLeaveType_Success() {
        CreateLeaveTypeRequest request = new CreateLeaveTypeRequest();
        request.setLeaveName("Updated Leave");
        request.setLeaveUniqueName("UPDATED");
        request.setDescription("Updated description");
        request.setMaxDays(15);
        
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(testLeaveType));
        when(leaveTypeRepository.findByLeaveName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByLeaveUniqueName(anyString())).thenReturn(Optional.empty());
        when(leaveTypeRepository.save(any())).thenReturn(testLeaveType);
        
        LeaveType updated = leaveService.updateLeaveType(1, request);
        
        assertNotNull(updated);
        verify(leaveTypeRepository).save(any(LeaveType.class));
    }

    @Test
    void testUpdateLeaveType_NotFound() {
        CreateLeaveTypeRequest request = new CreateLeaveTypeRequest();
        request.setLeaveName("Updated Leave");
        request.setLeaveUniqueName("UPDATED");
        request.setDescription("Updated description");
        request.setMaxDays(15);
        
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeaveType(1, request));
    }

    @Test
    void testDeleteLeaveType_Success() {
        when(leaveTypeRepository.existsById(1)).thenReturn(true);
        
        assertDoesNotThrow(() -> leaveService.deleteLeaveType(1));
        
        verify(leaveTypeRepository).deleteById(1);
    }

    @Test
    void testDeleteLeaveType_NotFound() {
        when(leaveTypeRepository.existsById(1)).thenReturn(false);
        
        assertThrows(ResponseStatusException.class, () -> leaveService.deleteLeaveType(1));
    }

    @Test
    void testGetLeaveById_Success() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        
        Leave leave = leaveService.getLeaveById(1L);
        
        assertNotNull(leave);
        assertEquals(1L, leave.getId());
        assertEquals(LeaveConstants.STATUS_PENDING, leave.getStatus());
    }

    @Test
    void testGetLeaveById_NotFound() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> leaveService.getLeaveById(1L));
    }

    @Test
    void testSetDocumentPath() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        when(leaveRepository.save(any())).thenReturn(testLeave);
        
        assertDoesNotThrow(() -> leaveService.setDocumentPath(1L, "/path/to/document"));
        
        verify(leaveRepository).save(any(Leave.class));
    }

    @Test
    void testGetAllLeaves() {
        when(leaveRepository.findAll()).thenReturn(List.of(testLeave));
        
        List<Leave> leaves = leaveService.getAllLeaves();
        
        assertNotNull(leaves);
        assertEquals(1, leaves.size());
    }

    @Test
    void testGetLeavesByEmail() {
        when(leaveRepository.findByEmailId("employee@test.com")).thenReturn(List.of(testLeave));
        
        List<Leave> leaves = leaveService.getLeavesByEmail("employee@test.com");
        
        assertNotNull(leaves);
        assertEquals(1, leaves.size());
        assertEquals("employee@test.com", leaves.get(0).getEmailId());
    }

    @Test
    void testCreateLeave_Success() {
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick Leave");
        request.setFromDate("2024-12-01");
        request.setToDate("2024-12-03");
        request.setReason("Medical");
        request.setDayType(LeaveConstants.DAY_TYPE_FULL);
        request.setManagerEmail("manager@test.com");
        
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(Collections.emptyList());
        when(leaveRepository.save(any())).thenReturn(testLeave);
        doNothing().when(leaveProcessService).startLeaveProcess(any(), anyString(), anyString());
        
        Leave created = leaveService.createLeave(request, "employee@test.com");
        
        assertNotNull(created);
        verify(leaveRepository).save(any(Leave.class));
    }

    @Test
    void testCreateLeave_WithDays() {
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick Leave");
        request.setReason("Medical");
        
        LeaveDayEntry day1 = new LeaveDayEntry();
        day1.setDate("2024-12-01");
        day1.setDayType(LeaveConstants.DAY_TYPE_FULL);
        
        LeaveDayEntry day2 = new LeaveDayEntry();
        day2.setDate("2024-12-02");
        day2.setDayType(LeaveConstants.DAY_TYPE_HALF);
        day2.setHalfDaySession("MORNING");
        
        request.setDays(List.of(day1, day2));
        
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(Collections.emptyList());
        when(leaveRepository.save(any())).thenReturn(testLeave);
        doNothing().when(leaveProcessService).startLeaveProcess(any(), anyString(), anyString());
        
        Leave created = leaveService.createLeave(request, "employee@test.com");
        
        assertNotNull(created);
        verify(leaveRepository).save(any(Leave.class));
    }

    @Test
    void testCreateLeave_Conflict() {
        CreateLeaveRequest request = new CreateLeaveRequest();
        request.setLeaveType("Sick Leave");
        request.setFromDate("2024-12-01");
        request.setToDate("2024-12-03");
        request.setReason("Medical");
        
        Leave overlappingLeave = new Leave();
        overlappingLeave.setId(2L);
        overlappingLeave.setFromDate(LocalDate.parse("2024-12-02"));
        overlappingLeave.setToDate(LocalDate.parse("2024-12-04"));
        Map<String, String> trail = new HashMap<>();
        trail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        overlappingLeave.setTrail(List.of(trail));
        
        when(leaveRepository.findOverlapping(anyString(), any(), any(), anyLong())).thenReturn(List.of(overlappingLeave));
        
        assertThrows(ResponseStatusException.class, () -> leaveService.createLeave(request, "employee@test.com"));
    }

    @Test
    void testUpdateLeave_Success() {
        UpdateLeaveRequest request = new UpdateLeaveRequest();
        request.setLeaveType("Sick Leave");
        request.setFromDate("2024-12-01");
        request.setToDate("2024-12-03");
        request.setReason("Updated reason");
        request.setDayType(LeaveConstants.DAY_TYPE_FULL);
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        when(leaveRepository.save(any())).thenReturn(testLeave);
        
        Leave updated = leaveService.updateLeave(1L, request, "employee@test.com");
        
        assertNotNull(updated);
        verify(leaveRepository).save(any(Leave.class));
    }

    @Test
    void testUpdateLeave_NotFound() {
        UpdateLeaveRequest request = new UpdateLeaveRequest();
        request.setLeaveType("Sick Leave");
        request.setFromDate("2024-12-01");
        request.setToDate("2024-12-03");
        request.setReason("Updated reason");
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeave(1L, request, "employee@test.com"));
    }

    @Test
    void testUpdateLeave_Forbidden() {
        UpdateLeaveRequest request = new UpdateLeaveRequest();
        request.setLeaveType("Sick Leave");
        request.setFromDate("2024-12-01");
        request.setToDate("2024-12-03");
        request.setReason("Updated reason");
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        
        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeave(1L, request, "other@test.com"));
    }

    @Test
    void testUpdateLeave_NotPending() {
        UpdateLeaveRequest request = new UpdateLeaveRequest();
        request.setLeaveType("Sick Leave");
        request.setFromDate("2024-12-01");
        request.setToDate("2024-12-03");
        request.setReason("Updated reason");
        
        Map<String, String> approvedTrail = new HashMap<>();
        approvedTrail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        testLeave.setTrail(List.of(approvedTrail));
        
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        
        assertThrows(ResponseStatusException.class, () -> leaveService.updateLeave(1L, request, "employee@test.com"));
    }

    @Test
    void testDeleteLeave_Success() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        
        assertDoesNotThrow(() -> leaveService.deleteLeave(1L, "employee@test.com"));
        
        verify(leaveRepository).deleteById(1L);
    }

    @Test
    void testDeleteLeave_NotFound() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> leaveService.deleteLeave(1L, "employee@test.com"));
    }

    @Test
    void testDeleteLeave_Forbidden() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        
        assertThrows(ResponseStatusException.class, () -> leaveService.deleteLeave(1L, "other@test.com"));
    }

    @Test
    void testGetLeaveBalance() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("employee@test.com");
        empLeave.setFullName("Test Employee");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("sickLeave", 10);
        empLeave.setLeaves(leaves);
        
        when(employeeLeaveRepository.findByEmailId("employee@test.com")).thenReturn(Optional.of(empLeave));
        
        Map<String, Object> balance = leaveService.getLeaveBalance("employee@test.com");
        
        assertNotNull(balance);
        assertEquals("Test Employee", balance.get("employeeName"));
        assertEquals("employee@test.com", balance.get("email"));
    }

    @Test
    void testGetLeaveBalance_NotFound() {
        when(employeeLeaveRepository.findByEmailId("employee@test.com")).thenReturn(Optional.empty());
        
        Map<String, Object> balance = leaveService.getLeaveBalance("employee@test.com");
        
        assertNotNull(balance);
        assertTrue(balance.isEmpty());
    }

    @Test
    void testGetPendingLeavesFor_Manager() {
        when(leaveRepository.findAll()).thenReturn(List.of(testLeave));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER)).thenReturn(List.of(1L));
        when(leaveProcessService.getActiveLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN)).thenReturn(Collections.emptyList());
        
        List<Leave> pending = leaveService.getPendingLeavesFor("manager@test.com");
        
        assertNotNull(pending);
        assertEquals(1, pending.size());
    }

    @Test
    void testGetReviewedLeavesByReviewer() {
        Map<String, String> reviewTrail = new HashMap<>();
        reviewTrail.put(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_APPROVED);
        reviewTrail.put(LeaveConstants.TRAIL_REVIEWED_BY, "manager@test.com");
        testLeave.setTrail(List.of(reviewTrail));
        
        when(leaveRepository.findAll()).thenReturn(List.of(testLeave));
        
        List<Leave> reviewed = leaveService.getReviewedLeavesByReviewer("manager@test.com");
        
        assertNotNull(reviewed);
        assertEquals(1, reviewed.size());
    }

    @Test
    void testGetManagerLoggedLeaves() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("manager@test.com");
        empLeave.setFullName("Test Manager");
        
        Map<String, Object> leaveRecord = new HashMap<>();
        leaveRecord.put("leaveId", 1L);
        leaveRecord.put("employeeEmail", "employee@test.com");
        
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of(leaveRecord));
        empLeave.setLeaves(leaves);
        
        when(employeeLeaveRepository.findByEmailId("manager@test.com")).thenReturn(Optional.of(empLeave));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        
        Map<String, Object> result = leaveService.getManagerLoggedLeaves("manager@test.com");
        
        assertNotNull(result);
        assertEquals("Test Manager", result.get("managerName"));
        assertEquals("manager@test.com", result.get("managerEmail"));
    }

    @Test
    void testGetAdminLoggedLeaves() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setEmailId("admin@test.com");
        empLeave.setFullName("Test Admin");
        
        Map<String, Object> leaveRecord = new HashMap<>();
        leaveRecord.put("leaveId", 1L);
        leaveRecord.put("employeeEmail", "employee@test.com");
        
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", List.of(leaveRecord));
        empLeave.setLeaves(leaves);
        
        when(employeeLeaveRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(empLeave));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(testLeave));
        
        Map<String, Object> result = leaveService.getAdminLoggedLeaves("admin@test.com");
        
        assertNotNull(result);
        assertEquals("Test Admin", result.get("adminName"));
        assertEquals("admin@test.com", result.get("adminEmail"));
    }
}
