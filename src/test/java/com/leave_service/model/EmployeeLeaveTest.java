package com.leave_service.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeLeaveTest {

    @Test
    void testEmployeeLeaveGettersAndSetters() {
        EmployeeLeave empLeave = new EmployeeLeave();
        empLeave.setId(1L);
        empLeave.setUserId(100L);
        empLeave.setFullName("John Doe");
        empLeave.setEmailId("john@test.com");
        empLeave.setGender("Male");

        Map<String, Object> leaves = new HashMap<>();
        leaves.put("approved_leaves", 5);
        empLeave.setLeaves(leaves);

        assertEquals(1L, empLeave.getId());
        assertEquals(100L, empLeave.getUserId());
        assertEquals("John Doe", empLeave.getFullName());
        assertEquals("john@test.com", empLeave.getEmailId());
        assertEquals("Male", empLeave.getGender());
        assertNotNull(empLeave.getLeaves());
        assertEquals(5, empLeave.getLeaves().get("approved_leaves"));
    }

    @Test
    void testEmployeeLeaveNoArgsConstructor() {
        EmployeeLeave empLeave = new EmployeeLeave();
        assertNotNull(empLeave);
    }
}
