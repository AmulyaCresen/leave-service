package com.leave_service.repository;

import com.leave_service.model.LeaveType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveTypeRepositoryTest {

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Test
    void findById_existingLeaveType() {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(1);
        leaveType.setLeaveName("Sick Leave");
        leaveType.setMaxDays(10);

        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));

        Optional<LeaveType> result = leaveTypeRepository.findById(1);

        assertTrue(result.isPresent());
        assertEquals("Sick Leave", result.get().getLeaveName());
        assertEquals(Integer.valueOf(10), result.get().getMaxDays());
    }

    @Test
    void findByLeaveUniqueName_existingLeaveType() {
        LeaveType leaveType = new LeaveType();
        leaveType.setLeaveName("Annual Leave");
        leaveType.setLeaveUniqueName("ANNUAL");
        leaveType.setMaxDays(25);

        when(leaveTypeRepository.findByLeaveUniqueName("ANNUAL")).thenReturn(Optional.of(leaveType));

        Optional<LeaveType> result = leaveTypeRepository.findByLeaveUniqueName("ANNUAL");

        assertTrue(result.isPresent());
        assertEquals("Annual Leave", result.get().getLeaveName());
    }

    @Test
    void findByLeaveUniqueName_nonExistingLeaveType() {
        when(leaveTypeRepository.findByLeaveUniqueName("NON_EXISTENT")).thenReturn(Optional.empty());

        Optional<LeaveType> result = leaveTypeRepository.findByLeaveUniqueName("NON_EXISTENT");

        assertFalse(result.isPresent());
    }

    @Test
    void findAll_leaveTypes() {
        LeaveType sick = new LeaveType();
        sick.setLeaveName("Sick Leave");
        
        LeaveType annual = new LeaveType();
        annual.setLeaveName("Annual Leave");

        List<LeaveType> leaveTypes = List.of(sick, annual);
        when(leaveTypeRepository.findAll()).thenReturn(leaveTypes);

        List<LeaveType> result = leaveTypeRepository.findAll();

        assertEquals(2, result.size());
    }

    @Test
    void save_leaveType() {
        LeaveType leaveType = new LeaveType();
        leaveType.setLeaveName("Maternity Leave");
        leaveType.setMaxDays(90);

        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(leaveType);

        LeaveType saved = leaveTypeRepository.save(leaveType);

        assertNotNull(saved);
        assertEquals("Maternity Leave", saved.getLeaveName());
    }
}