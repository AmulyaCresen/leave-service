package com.leave_service.commons;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class LeaveConstantsTest {

    @Test
    void testPrivateConstructor() throws Exception {
        Constructor<LeaveConstants> constructor = LeaveConstants.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void testConstants() {
        assertEquals("leaveId", LeaveConstants.VAR_LEAVE_ID);
        assertEquals("employeeEmail", LeaveConstants.VAR_EMPLOYEE_EMAIL);
        assertEquals("PENDING", LeaveConstants.STATUS_PENDING);
        assertEquals("APPROVED", LeaveConstants.STATUS_APPROVED);
        assertEquals("REJECTED", LeaveConstants.STATUS_REJECTED);
        assertEquals("FULL_DAY", LeaveConstants.DAY_TYPE_FULL);
        assertEquals("HALF_DAY", LeaveConstants.DAY_TYPE_HALF);
        assertEquals("No reason provided", LeaveConstants.NO_REASON_PROVIDED);
        assertEquals("MANAGER", LeaveConstants.STAGE_MANAGER);
        assertEquals("ADMIN", LeaveConstants.STAGE_ADMIN);
        assertEquals("PARTIAL", LeaveConstants.STATUS_PARTIAL);
        assertEquals("MANAGER_APPROVED", LeaveConstants.STATUS_MANAGER_APPROVED);
        assertEquals("leaveApprovalProcess", LeaveConstants.PROCESS_KEY);
        assertEquals("leave-", LeaveConstants.PROCESS_BUSINESS_KEY);
        assertEquals("usertask_manager", LeaveConstants.TASK_DEF_KEY_MANAGER);
        assertEquals("usertask_admin", LeaveConstants.TASK_DEF_KEY_ADMIN);
        assertEquals("system", LeaveConstants.SYSTEM_USER);
        assertEquals("approved_leaves", LeaveConstants.APPROVED_LEAVES_KEY);
        assertEquals("yyyy-MM-dd HH:mm:ss", LeaveConstants.TRAIL_DT_PATTERN);
    }
}
