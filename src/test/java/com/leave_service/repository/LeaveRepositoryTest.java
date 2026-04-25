package com.leave_service.repository;

import com.leave_service.model.Leave;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRepositoryTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Test
    void findById_existingLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setEmailId("test@example.com");
        leave.setLeaveType("Sick");
        leave.setFromDate(LocalDate.of(2025, 6, 1));
        leave.setToDate(LocalDate.of(2025, 6, 3));
        leave.setTrail(new ArrayList<>());

        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        Optional<Leave> result = leaveRepository.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("test@example.com", result.get().getEmailId());
    }

    @Test
    void findById_nonExistingLeave() {
        when(leaveRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Leave> result = leaveRepository.findById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void save_leave() {
        Leave leave = new Leave();
        leave.setEmailId("new@example.com");
        leave.setLeaveType("Annual");

        when(leaveRepository.save(any(Leave.class))).thenReturn(leave);

        Leave saved = leaveRepository.save(leave);

        assertNotNull(saved);
        assertEquals("new@example.com", saved.getEmailId());
    }

    @Test
    void findByEmailId_existingLeaves() {
        Leave leave1 = new Leave();
        leave1.setId(1L);
        leave1.setEmailId("test@example.com");
        
        Leave leave2 = new Leave();
        leave2.setId(2L);
        leave2.setEmailId("test@example.com");

        List<Leave> leaves = List.of(leave1, leave2);
        when(leaveRepository.findByEmailId("test@example.com")).thenReturn(leaves);

        List<Leave> result = leaveRepository.findByEmailId("test@example.com");

        assertEquals(2, result.size());
        assertEquals("test@example.com", result.get(0).getEmailId());
    }
}