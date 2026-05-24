package com.leave_service.model;

import com.leave_service.dto.LeaveDayEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LeaveTest {

    @Test
    void testLeaveGettersAndSetters() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setEmailId("emp@test.com");
        leave.setManagerEmail("mgr@test.com");
        leave.setReason("Medical");
        leave.setComments("Urgent");
        leave.setCreatedAt(LocalDate.now());
        leave.setUpdatedAt(LocalDate.now());
        leave.setEditable(true);
        leave.setStatus("PENDING");
        leave.setDayType("FULL_DAY");
        leave.setHalfDaySession("MORNING");
        leave.setTotalDays(3.0);
        leave.setReviewedBy("mgr@test.com");

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        leave.setDays(List.of(day));

        Map<String, String> trail = new HashMap<>();
        trail.put("status", "PENDING");
        leave.setTrail(List.of(trail));

        assertEquals(1L, leave.getId());
        assertEquals("Sick", leave.getLeaveType());
        assertEquals(LocalDate.of(2025, 6, 1), leave.getFromDate());
        assertEquals(LocalDate.of(2025, 6, 3), leave.getToDate());
        assertEquals("emp@test.com", leave.getEmailId());
        assertEquals("mgr@test.com", leave.getManagerEmail());
        assertEquals("Medical", leave.getReason());
        assertEquals("Urgent", leave.getComments());
        assertNotNull(leave.getCreatedAt());
        assertNotNull(leave.getUpdatedAt());
        assertTrue(leave.getEditable());
        assertEquals("PENDING", leave.getStatus());
        assertEquals("FULL_DAY", leave.getDayType());
        assertEquals("MORNING", leave.getHalfDaySession());
        assertEquals(3.0, leave.getTotalDays());
        assertEquals("mgr@test.com", leave.getReviewedBy());
        assertEquals(1, leave.getDays().size());
        assertEquals(1, leave.getTrail().size());
    }

    @Test
    void testLeaveNoArgsConstructor() {
        Leave leave = new Leave();
        assertNotNull(leave);
        assertTrue(leave.getEditable());
    }
}
