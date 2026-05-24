package com.leave_service.repository;

import com.leave_service.model.EmployeeLeave;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeLeaveRepositoryTest {

    @Mock
    private EmployeeLeaveRepository employeeLeaveRepository;

    @Test
    void findByEmailId_existingEmployee() {
        EmployeeLeave employeeLeave = new EmployeeLeave();
        employeeLeave.setEmailId("test@example.com");
        employeeLeave.setFullName("Test User");
        Map<String, Object> leaves = new HashMap<>();
        leaves.put("Sick", 10.0);
        employeeLeave.setLeaves(leaves);

        when(employeeLeaveRepository.findByEmailId("test@example.com"))
            .thenReturn(Optional.of(employeeLeave));

        Optional<EmployeeLeave> result = employeeLeaveRepository.findByEmailId("test@example.com");

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().getEmailId());
        assertEquals("Test User", result.get().getFullName());
    }

    @Test
    void findByEmailId_nonExistingEmployee() {
        when(employeeLeaveRepository.findByEmailId("nonexistent@example.com"))
            .thenReturn(Optional.empty());

        Optional<EmployeeLeave> result = employeeLeaveRepository.findByEmailId("nonexistent@example.com");

        assertFalse(result.isPresent());
    }

    @Test
    void save_employeeLeave() {
        EmployeeLeave employeeLeave = new EmployeeLeave();
        employeeLeave.setEmailId("new@example.com");
        employeeLeave.setFullName("New User");

        when(employeeLeaveRepository.save(any(EmployeeLeave.class)))
            .thenReturn(employeeLeave);

        EmployeeLeave saved = employeeLeaveRepository.save(employeeLeave);

        assertNotNull(saved);
        assertEquals("new@example.com", saved.getEmailId());
    }
}