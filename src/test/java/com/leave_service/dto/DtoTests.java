package com.leave_service.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreateLeaveRequestTest {

    @Test
    void testCreateLeaveRequestGettersAndSetters() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick");
        req.setFromDate("2025-06-01");
        req.setToDate("2025-06-03");
        req.setReason("Medical");
        req.setComments("Urgent");
        req.setDayType("FULL_DAY");
        req.setHalfDaySession("MORNING");
        req.setManagerEmail("mgr@test.com");

        LeaveDayEntry day = new LeaveDayEntry();
        day.setDate("2025-06-01");
        req.setDays(List.of(day));

        assertEquals("Sick", req.getLeaveType());
        assertEquals("2025-06-01", req.getFromDate());
        assertEquals("2025-06-03", req.getToDate());
        assertEquals("Medical", req.getReason());
        assertEquals("Urgent", req.getComments());
        assertEquals("FULL_DAY", req.getDayType());
        assertEquals("MORNING", req.getHalfDaySession());
        assertEquals("mgr@test.com", req.getManagerEmail());
        assertEquals(1, req.getDays().size());
    }
}

class UpdateLeaveRequestTest {

    @Test
    void testUpdateLeaveRequestGettersAndSetters() {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual");
        req.setFromDate("2025-06-05");
        req.setToDate("2025-06-07");
        req.setReason("Personal");
        req.setComments("Family");
        req.setDayType("HALF_DAY");
        req.setHalfDaySession("AFTERNOON");

        assertEquals("Casual", req.getLeaveType());
        assertEquals("2025-06-05", req.getFromDate());
        assertEquals("2025-06-07", req.getToDate());
        assertEquals("Personal", req.getReason());
        assertEquals("Family", req.getComments());
        assertEquals("HALF_DAY", req.getDayType());
        assertEquals("AFTERNOON", req.getHalfDaySession());
    }
}

class HolidayRequestTest {

    @Test
    void testHolidayRequestGettersAndSetters() {
        HolidayRequest req = new HolidayRequest();
        req.setName("Diwali");
        req.setDate("2025-10-20");

        assertEquals("Diwali", req.getName());
        assertEquals("2025-10-20", req.getDate());
    }
}

class CreateLeaveTypeRequestTest {

    @Test
    void testCreateLeaveTypeRequestGettersAndSetters() {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Sick Leave");
        req.setLeaveUniqueName("sick_leave");
        req.setDescription("Medical purposes");
        req.setMaxDays(10);

        assertEquals("Sick Leave", req.getLeaveName());
        assertEquals("sick_leave", req.getLeaveUniqueName());
        assertEquals("Medical purposes", req.getDescription());
        assertEquals(10, req.getMaxDays());
    }
}

class LeaveDayEntryTest {

    @Test
    void testLeaveDayEntryGettersAndSetters() {
        LeaveDayEntry entry = new LeaveDayEntry();
        entry.setDate("2025-06-01");
        entry.setDayType("FULL_DAY");
        entry.setHalfDaySession("MORNING");
        entry.setStatus("APPROVED");
        entry.setReviewStage("MANAGER");
        entry.setReviewedBy("Manager Name");
        entry.setReason("Test reason");

        assertEquals("2025-06-01", entry.getDate());
        assertEquals("FULL_DAY", entry.getDayType());
        assertEquals("MORNING", entry.getHalfDaySession());
        assertEquals("APPROVED", entry.getStatus());
        assertEquals("MANAGER", entry.getReviewStage());
        assertEquals("Manager Name", entry.getReviewedBy());
        assertEquals("Test reason", entry.getReason());
    }

    @Test
    void testLeaveDayEntryNullValues() {
        LeaveDayEntry entry = new LeaveDayEntry();
        assertNull(entry.getDate());
        assertNull(entry.getDayType());
        assertNull(entry.getStatus());
    }
}
