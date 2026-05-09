package com.leave_service.controller;

import com.leave_service.model.Leave;
import com.leave_service.model.LeaveFile;
import com.leave_service.repository.LeaveFileRepository;
import com.leave_service.service.LeaveProcessService;
import com.leave_service.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaveController.class)
@AutoConfigureMockMvc(addFilters = false)
class LeaveControllerFileOperationsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveService leaveService;

    @MockBean
    private LeaveProcessService leaveProcessService;

    @MockBean
    private LeaveFileRepository leaveFileRepository;

    @TempDir
    Path tempDir;

    @Autowired
    private LeaveController leaveController;

    private LeaveFile testLeaveFile;
    private Leave testLeave;

    @BeforeEach
    void setUp() {
        testLeave = new Leave();
        testLeave.setId(1L);
        testLeave.setEmailId("employee@test.com");

        testLeaveFile = new LeaveFile();
        testLeaveFile.setId(10L);
        testLeaveFile.setLeaveId(1L);
        testLeaveFile.setFileName("medical.pdf");
        testLeaveFile.setFilePath("medical.pdf");
        testLeaveFile.setFileSize(1024L);
        testLeaveFile.setUploadedAt(LocalDateTime.now());
        testLeaveFile.setUploadedBy("employee@test.com");

        // Use temp dir for file operations
        ReflectionTestUtils.setField(leaveController, "uploadDir", tempDir.toString());
    }

    // ── deleteFile ──────────────────────────────────────────────────────────

    @Test
    void deleteFile_ownerEmail_returnsNoContent() throws Exception {
        // Create the actual file so deleteIfExists succeeds
        Path file = tempDir.resolve("medical.pdf");
        Files.write(file, "content".getBytes());

        when(leaveFileRepository.findById(10L)).thenReturn(Optional.of(testLeaveFile));
        when(leaveService.getLeaveById(1L)).thenReturn(testLeave);
        doNothing().when(leaveFileRepository).deleteById(10L);

        mockMvc.perform(delete("/leave/files/10")
                        .header("X-User-Email", "employee@test.com"))
                .andExpect(status().isNoContent());

        verify(leaveFileRepository).deleteById(10L);
    }

    @Test
    void deleteFile_wrongEmail_returnsForbidden() throws Exception {
        when(leaveFileRepository.findById(10L)).thenReturn(Optional.of(testLeaveFile));
        when(leaveService.getLeaveById(1L)).thenReturn(testLeave);

        mockMvc.perform(delete("/leave/files/10")
                        .header("X-User-Email", "other@test.com"))
                .andExpect(status().isForbidden());

        verify(leaveFileRepository, never()).deleteById(any());
    }

    // ── downloadFile ────────────────────────────────────────────────────────

    @Test
    void downloadFile_fileExists_returnsOk() throws Exception {
        // Create a real file in temp dir
        Path file = tempDir.resolve("medical.pdf");
        Files.write(file, "PDF content here".getBytes());

        when(leaveFileRepository.findById(10L)).thenReturn(Optional.of(testLeaveFile));

        mockMvc.perform(get("/leave/files/10/download"))
                .andExpect(status().isOk());

        verify(leaveFileRepository).findById(10L);
    }

    @Test
    void downloadFile_fileNotExistsOnDisk_returnsNotFound() throws Exception {
        // file record exists in DB but not on disk
        when(leaveFileRepository.findById(10L)).thenReturn(Optional.of(testLeaveFile));
        // Don't create the actual file in tempDir

        mockMvc.perform(get("/leave/files/10/download"))
                .andExpect(status().isNotFound());
    }

}
