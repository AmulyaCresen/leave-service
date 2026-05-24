package com.leave_service.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LeaveTypeTest {

    @Test
    void testLeaveTypeGettersAndSetters() {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(1);
        leaveType.setLeaveName("Sick Leave");
        leaveType.setLeaveUniqueName("sick_leave");
        leaveType.setDescription("For medical purposes");
        leaveType.setMaxDays(10);
        leaveType.setCreatedAt(OffsetDateTime.now());
        leaveType.setUpdatedAt(OffsetDateTime.now());

        assertEquals(1, leaveType.getId());
        assertEquals("Sick Leave", leaveType.getLeaveName());
        assertEquals("sick_leave", leaveType.getLeaveUniqueName());
        assertEquals("For medical purposes", leaveType.getDescription());
        assertEquals(10, leaveType.getMaxDays());
        assertNotNull(leaveType.getCreatedAt());
        assertNotNull(leaveType.getUpdatedAt());
    }

    @Test
    void testLeaveTypeNoArgsConstructor() {
        LeaveType leaveType = new LeaveType();
        assertNotNull(leaveType);
    }
}
