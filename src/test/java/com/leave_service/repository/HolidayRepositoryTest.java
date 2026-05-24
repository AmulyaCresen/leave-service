package com.leave_service.repository;

import com.leave_service.model.Holiday;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayRepositoryTest {

    @Mock
    private HolidayRepository holidayRepository;

    @Test
    void findById_existingHoliday() {
        Holiday holiday = new Holiday();
        holiday.setId(1L);
        holiday.setName("New Year");
        holiday.setDate(LocalDate.of(2025, 1, 1));

        when(holidayRepository.findById(1L)).thenReturn(Optional.of(holiday));

        Optional<Holiday> result = holidayRepository.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("New Year", result.get().getName());
    }

    @Test
    void findAll_holidays() {
        Holiday holiday1 = new Holiday();
        holiday1.setName("New Year");
        holiday1.setDate(LocalDate.of(2025, 1, 1));
        
        Holiday holiday2 = new Holiday();
        holiday2.setName("Christmas");
        holiday2.setDate(LocalDate.of(2025, 12, 25));

        List<Holiday> holidays = List.of(holiday1, holiday2);
        when(holidayRepository.findAll()).thenReturn(holidays);

        List<Holiday> result = holidayRepository.findAll();

        assertEquals(2, result.size());
    }

    @Test
    void save_holiday() {
        Holiday holiday = new Holiday();
        holiday.setName("Independence Day");
        holiday.setDate(LocalDate.of(2025, 7, 4));

        when(holidayRepository.save(any(Holiday.class))).thenReturn(holiday);

        Holiday saved = holidayRepository.save(holiday);

        assertNotNull(saved);
        assertEquals("Independence Day", saved.getName());
    }
}