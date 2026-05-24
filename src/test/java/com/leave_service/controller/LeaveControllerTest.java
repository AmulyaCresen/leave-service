package com.leave_service.controller;

import com.leave_service.dto.CreateLeaveRequest;
import com.leave_service.dto.CreateLeaveTypeRequest;
import com.leave_service.dto.HolidayRequest;
import com.leave_service.dto.UpdateLeaveRequest;
import com.leave_service.model.Holiday;
import com.leave_service.model.Leave;
import com.leave_service.model.LeaveType;
import com.leave_service.repository.LeaveFileRepository;
import com.leave_service.service.LeaveProcessService;
import com.leave_service.service.LeaveService;
import com.leave_service.service.LeaveWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaveController.class)
@AutoConfigureMockMvc(addFilters = false)
class LeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveService leaveService;

    @MockBean
    private LeaveWorkflowService leaveWorkflowService;

    @MockBean
    private LeaveProcessService leaveProcessService;

    @MockBean
    private LeaveFileRepository leaveFileRepository;

    private Leave testLeave;
    private LeaveType testLeaveType;
    private Holiday testHoliday;

    @BeforeEach
    void setUp() {
        testLeave = new Leave();
        testLeave.setId(1L);
        testLeave.setEmailId("test@example.com");
        testLeave.setLeaveType("Sick Leave");
        testLeave.setFromDate(LocalDate.now());
        testLeave.setToDate(LocalDate.now().plusDays(2));
        testLeave.setReason("Medical");
        testLeave.setStatus("PENDING");

        testLeaveType = new LeaveType();
        testLeaveType.setId(1);
        testLeaveType.setLeaveName("Sick Leave");
        testLeaveType.setLeaveUniqueName("SICK");
        testLeaveType.setMaxDays(10);

        testHoliday = new Holiday();
        testHoliday.setId(1L);
        testHoliday.setName("New Year");
        testHoliday.setDate(LocalDate.of(2024, 1, 1));
    }

    @Test
    void testGetAllHolidays() throws Exception {
        when(leaveService.getAllHolidays()).thenReturn(List.of(testHoliday));

        mockMvc.perform(get("/leave/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("New Year"));
    }

    @Test
    void testCreateHoliday() throws Exception {
        when(leaveService.createHoliday(any(HolidayRequest.class))).thenReturn(testHoliday);

        mockMvc.perform(post("/leave/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Year\",\"date\":\"2024-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Year"));
    }

    @Test
    void testGetAllLeaveTypes() throws Exception {
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(testLeaveType));

        mockMvc.perform(get("/leave/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveName").value("Sick Leave"));
    }

    @Test
    void testCreateLeaveType() throws Exception {
        when(leaveService.createLeaveType(any(CreateLeaveTypeRequest.class))).thenReturn(testLeaveType);

        mockMvc.perform(post("/leave/types/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"leaveName\":\"Sick Leave\",\"leaveUniqueName\":\"SICK\",\"maxDays\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveName").value("Sick Leave"));
    }

    @Test
    void testGetAllLeaves() throws Exception {
        when(leaveService.getAllLeaves()).thenReturn(List.of(testLeave));

        mockMvc.perform(get("/leave/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveType").value("Sick Leave"));
    }

    @Test
    void testGetLeaveById() throws Exception {
        when(leaveService.getLeaveById(1L)).thenReturn(testLeave);

        mockMvc.perform(get("/leave/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveType").value("Sick Leave"));
    }

    @Test
    void testGetMyLeaves() throws Exception {
        when(leaveService.getLeavesByEmail(anyString())).thenReturn(List.of(testLeave));

        mockMvc.perform(get("/leave/my")
                .header("X-User-Email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveType").value("Sick Leave"));
    }

    @Test
    void testCreateLeave() throws Exception {
        when(leaveService.createLeave(any(CreateLeaveRequest.class), anyString())).thenReturn(testLeave);

        mockMvc.perform(post("/leave/create")
                .header("X-User-Email", "test@example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"leaveType\":\"Sick Leave\",\"fromDate\":\"2024-12-01\",\"toDate\":\"2024-12-03\",\"reason\":\"Medical\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveType").value("Sick Leave"));
    }

    @Test
    void testUpdateLeave() throws Exception {
        when(leaveService.updateLeave(anyLong(), any(UpdateLeaveRequest.class), anyString())).thenReturn(testLeave);

        mockMvc.perform(put("/leave/1")
                .header("X-User-Email", "test@example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"leaveType\":\"Sick Leave\",\"fromDate\":\"2024-12-01\",\"toDate\":\"2024-12-03\",\"reason\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveType").value("Sick Leave"));
    }

    @Test
    void testDeleteLeave() throws Exception {
        doNothing().when(leaveService).deleteLeave(anyLong(), anyString());

        mockMvc.perform(delete("/leave/1")
                .header("X-User-Email", "test@example.com"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetPendingLeaves() throws Exception {
        when(leaveService.getPendingLeavesFor(anyString())).thenReturn(List.of(testLeave));

        mockMvc.perform(get("/leave/pending-for")
                .header("X-User-Email", "manager@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveType").value("Sick Leave"));
    }

    @Test
    void testApproveLeave() throws Exception {
        doNothing().when(leaveWorkflowService).processManagerApproval(any());

        // The actual approveLeave endpoint is on LeaveProcessService
        // This test just verifies the endpoint can be invoked without error
        mockMvc.perform(post("/leave/1/approve")
                .header("X-User-Email", "manager@example.com"))
                .andExpect(status().isOk());
    }

    @Test
    void testRejectLeave() throws Exception {
        // The actual rejectLeave endpoint is on LeaveProcessService
        mockMvc.perform(post("/leave/1/reject")
                .header("X-User-Email", "manager@example.com")
                .param("reason", "Not approved"))
                .andExpect(status().isOk());
    }
}
