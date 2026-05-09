package com.leave_service.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LeaveFileTest {

    @Test
    void testNoArgsConstructor() {
        LeaveFile file = new LeaveFile();
        assertNotNull(file);
        assertNull(file.getId());
        assertNull(file.getLeaveId());
        assertNull(file.getFileName());
        assertNull(file.getFilePath());
        assertNull(file.getFileSize());
        assertNull(file.getUploadedAt());
        assertNull(file.getUploadedBy());
    }

    @Test
    void testGettersAndSetters() {
        LeaveFile file = new LeaveFile();

        file.setId(42L);
        file.setLeaveId(1L);
        file.setFileName("medical_certificate.pdf");
        file.setFilePath("uploads/leave-documents/1_1234567890_medical_certificate.pdf");
        file.setFileSize(204800L);
        LocalDateTime now = LocalDateTime.now();
        file.setUploadedAt(now);
        file.setUploadedBy("employee@test.com");

        assertEquals(42L, file.getId());
        assertEquals(1L, file.getLeaveId());
        assertEquals("medical_certificate.pdf", file.getFileName());
        assertEquals("uploads/leave-documents/1_1234567890_medical_certificate.pdf", file.getFilePath());
        assertEquals(204800L, file.getFileSize());
        assertEquals(now, file.getUploadedAt());
        assertEquals("employee@test.com", file.getUploadedBy());
    }

    @Test
    void testSetFileName_withSpecialChars() {
        LeaveFile file = new LeaveFile();
        file.setFileName("medical certificate (2025).pdf");
        assertEquals("medical certificate (2025).pdf", file.getFileName());
    }

    @Test
    void testSetFileSize_zero() {
        LeaveFile file = new LeaveFile();
        file.setFileSize(0L);
        assertEquals(0L, file.getFileSize());
    }

    @Test
    void testSetFileSize_large() {
        LeaveFile file = new LeaveFile();
        file.setFileSize(10_485_760L); // 10 MB
        assertEquals(10_485_760L, file.getFileSize());
    }

    @Test
    void testSetLeaveId_various() {
        LeaveFile file = new LeaveFile();
        file.setLeaveId(100L);
        assertEquals(100L, file.getLeaveId());
        file.setLeaveId(9999L);
        assertEquals(9999L, file.getLeaveId());
    }

    @Test
    void testUploadedAt_pastDate() {
        LeaveFile file = new LeaveFile();
        LocalDateTime past = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        file.setUploadedAt(past);
        assertEquals(2024, file.getUploadedAt().getYear());
        assertEquals(1, file.getUploadedAt().getMonthValue());
        assertEquals(15, file.getUploadedAt().getDayOfMonth());
    }
}
