package com.leave_service.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class HolidayTest {

    @Test
    void testHolidayGettersAndSetters() {
        Holiday holiday = new Holiday();
        holiday.setId(1L);
        holiday.setName("Diwali");
        holiday.setDate(LocalDate.of(2025, 10, 20));

        assertEquals(1L, holiday.getId());
        assertEquals("Diwali", holiday.getName());
        assertEquals(LocalDate.of(2025, 10, 20), holiday.getDate());
    }

    @Test
    void testHolidayNoArgsConstructor() {
        Holiday holiday = new Holiday();
        assertNotNull(holiday);
    }
}
