package com.leave_service.model;

import com.leave_service.dto.LeaveDayEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LeaveModelTest {

    @Test
    void testLeaveGettersAndSetters() {
        Leave leave = new Leave();
        
        leave.setId(1L);
        leave.setEmailId("test@example.com");
        leave.setLeaveType("Sick Leave");
        leave.setFromDate(LocalDate.of(2024, 12, 1));
        leave.setToDate(LocalDate.of(2024, 12, 3));
        leave.setReason("Medical");
        leave.setComments("Test comments");
        leave.setManagerEmail("manager@example.com");
        leave.setCreatedAt(LocalDate.now());
        leave.setUpdatedAt(LocalDate.now());
        leave.setEditable(true);
        leave.setDocumentPath("/path/to/doc");
        
        List<Map<String, String>> trail = new ArrayList<>();
        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put("status", "PENDING");
        trail.add(trailEntry);
        leave.setTrail(trail);
        
        List<LeaveDayEntry> days = new ArrayList<>();
        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2024-12-01");
        day.setDayType("FULL_DAY");
        days.add(day);
        leave.setDays(days);
        
        leave.setStatus("PENDING");
        leave.setDayType("FULL_DAY");
        leave.setHalfDaySession("MORNING");
        leave.setReviewedBy("manager@example.com");
        leave.setTotalDays(3.0);
        
        assertEquals(1L, leave.getId());
        assertEquals("test@example.com", leave.getEmailId());
        assertEquals("Sick Leave", leave.getLeaveType());
        assertEquals(LocalDate.of(2024, 12, 1), leave.getFromDate());
        assertEquals(LocalDate.of(2024, 12, 3), leave.getToDate());
        assertEquals("Medical", leave.getReason());
        assertEquals("Test comments", leave.getComments());
        assertEquals("manager@example.com", leave.getManagerEmail());
        assertNotNull(leave.getCreatedAt());
        assertNotNull(leave.getUpdatedAt());
        assertTrue(leave.getEditable());
        assertEquals("/path/to/doc", leave.getDocumentPath());
        assertNotNull(leave.getTrail());
        assertEquals(1, leave.getTrail().size());
        assertNotNull(leave.getDays());
        assertEquals(1, leave.getDays().size());
        assertEquals("PENDING", leave.getStatus());
        assertEquals("FULL_DAY", leave.getDayType());
        assertEquals("MORNING", leave.getHalfDaySession());
        assertEquals("manager@example.com", leave.getReviewedBy());
        assertEquals(3.0, leave.getTotalDays());
    }

    @Test
    void testLeaveDayEntryGettersAndSetters() {
        LeaveDayEntry day = new LeaveDayEntry();
        
        day.setDate("2024-12-01");
        day.setDayType("HALF_DAY");
        day.setHalfDaySession("AFTERNOON");
        day.setStatus("APPROVED");
        day.setReviewStage("MANAGER");
        day.setReason("Approved by manager");
        
        assertEquals("2024-12-01", day.getDate());
        assertEquals("HALF_DAY", day.getDayType());
        assertEquals("AFTERNOON", day.getHalfDaySession());
        assertEquals("APPROVED", day.getStatus());
        assertEquals("MANAGER", day.getReviewStage());
        assertEquals("Approved by manager", day.getReason());
    }

    @Test
    void testLeaveTypeGettersAndSetters() {
        LeaveType leaveType = new LeaveType();
        
        leaveType.setId(1);
        leaveType.setLeaveName("Sick Leave");
        leaveType.setLeaveUniqueName("SICK");
        leaveType.setDescription("Sick leave description");
        leaveType.setMaxDays(10);
        leaveType.setCreatedAt(java.time.OffsetDateTime.now());
        leaveType.setUpdatedAt(java.time.OffsetDateTime.now());
        
        assertEquals(1, leaveType.getId());
        assertEquals("Sick Leave", leaveType.getLeaveName());
        assertEquals("SICK", leaveType.getLeaveUniqueName());
        assertEquals("Sick leave description", leaveType.getDescription());
        assertEquals(10, leaveType.getMaxDays());
        assertNotNull(leaveType.getCreatedAt());
        assertNotNull(leaveType.getUpdatedAt());
    }

    @Test
    void testHolidayGettersAndSetters() {
        Holiday holiday = new Holiday();
        
        holiday.setId(1L);
        holiday.setName("New Year");
        holiday.setDate(LocalDate.of(2024, 1, 1));
        
        assertEquals(1L, holiday.getId());
        assertEquals("New Year", holiday.getName());
        assertEquals(LocalDate.of(2024, 1, 1), holiday.getDate());
    }

    @Test
    void testTaskGettersAndSetters() {
        Task task = new Task();
        
        task.setId(1L);
        task.setEmailId("test@example.com");
        task.setTitle("Test Task");
        task.setDescription("Test Description");
        task.setStatus("PENDING");
        task.setPriority("HIGH");
        task.setManagerEmail("manager@example.com");
        task.setDueDate(java.time.LocalDateTime.now());
        task.setCreatedAt(java.time.LocalDateTime.now());
        
        assertEquals(1L, task.getId());
        assertEquals("test@example.com", task.getEmailId());
        assertEquals("Test Task", task.getTitle());
        assertEquals("Test Description", task.getDescription());
        assertEquals("PENDING", task.getStatus());
        assertEquals("HIGH", task.getPriority());
        assertEquals("manager@example.com", task.getManagerEmail());
        assertNotNull(task.getDueDate());
        assertNotNull(task.getCreatedAt());
    }

    @Test
    void testLeaveFileGettersAndSetters() {
        LeaveFile leaveFile = new LeaveFile();
        
        leaveFile.setId(1L);
        leaveFile.setLeaveId(100L);
        leaveFile.setFileName("document.pdf");
        leaveFile.setFilePath("/uploads/document.pdf");
        leaveFile.setFileSize(1024L);
        leaveFile.setUploadedAt(java.time.LocalDateTime.now());
        leaveFile.setUploadedBy("test@example.com");
        
        assertEquals(1L, leaveFile.getId());
        assertEquals(100L, leaveFile.getLeaveId());
        assertEquals("document.pdf", leaveFile.getFileName());
        assertEquals("/uploads/document.pdf", leaveFile.getFilePath());
        assertEquals(1024L, leaveFile.getFileSize());
        assertNotNull(leaveFile.getUploadedAt());
        assertEquals("test@example.com", leaveFile.getUploadedBy());
    }
}
