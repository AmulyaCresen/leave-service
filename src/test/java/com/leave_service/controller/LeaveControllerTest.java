package com.leave_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_service.commons.LeaveConstants;
import com.leave_service.dto.*;
import com.leave_service.model.Holiday;
import com.leave_service.model.Leave;
import com.leave_service.model.LeaveType;
import com.leave_service.service.LeaveProcessService;
import com.leave_service.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LeaveControllerTest {

    @Mock private LeaveService leaveService;
    @Mock private LeaveProcessService leaveProcessService;
    @InjectMocks private LeaveController leaveController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(leaveController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private Leave buildLeave(Long id, String email) {
        Leave l = new Leave();
        l.setId(id);
        l.setEmailId(email);
        l.setLeaveType("Sick");
        l.setFromDate(LocalDate.of(2025, 6, 1));
        l.setToDate(LocalDate.of(2025, 6, 3));
        l.setReason("Fever");
        l.setStatus(LeaveConstants.STATUS_PENDING);
        return l;
    }

    @Test
    void getHolidays_returnsOk() throws Exception {
        Holiday h = new Holiday(); h.setId(1L); h.setName("Diwali"); h.setDate(LocalDate.of(2025, 10, 20));
        when(leaveService.getAllHolidays()).thenReturn(List.of(h));

        mockMvc.perform(get("/leave/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Diwali"));
    }

    @Test
    void createHoliday_returnsCreated() throws Exception {
        HolidayRequest req = new HolidayRequest(); req.setName("Holi"); req.setDate("2025-03-14");
        Holiday saved = new Holiday(); saved.setId(2L); saved.setName("Holi"); saved.setDate(LocalDate.of(2025, 3, 14));
        when(leaveService.createHoliday(any())).thenReturn(saved);

        mockMvc.perform(post("/leave/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Holi"));
    }

    @Test
    void updateHoliday_returnsOk() throws Exception {
        HolidayRequest req = new HolidayRequest(); req.setName("Updated"); req.setDate("2025-03-14");
        Holiday updated = new Holiday(); updated.setId(1L); updated.setName("Updated");
        when(leaveService.updateHoliday(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/leave/holidays/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void deleteHoliday_returnsNoContent() throws Exception {
        doNothing().when(leaveService).deleteHoliday(1L);

        mockMvc.perform(delete("/leave/holidays/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getLeaveTypes_returnsOk() throws Exception {
        LeaveType lt = new LeaveType(); lt.setId(1); lt.setLeaveName("Sick");
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(lt));

        mockMvc.perform(get("/leave/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveName").value("Sick"));
    }

    @Test
    void checkLeaveName_returnsTrue() throws Exception {
        when(leaveService.leaveNameExists("Sick")).thenReturn(true);

        mockMvc.perform(get("/leave/types/check-name").param("name", "Sick"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void checkLeaveUniqueName_returnsFalse() throws Exception {
        when(leaveService.leaveUniqueNameExists("sick_leave")).thenReturn(false);

        mockMvc.perform(get("/leave/types/check-unique-name").param("uniqueName", "sick_leave"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void createLeaveType_returnsCreated() throws Exception {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Sick"); req.setLeaveUniqueName("sick"); req.setMaxDays(10);
        LeaveType saved = new LeaveType(); saved.setId(1); saved.setLeaveName("Sick");
        when(leaveService.createLeaveType(any())).thenReturn(saved);

        mockMvc.perform(post("/leave/types/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveName").value("Sick"));
    }

    @Test
    void updateLeaveType_returnsOk() throws Exception {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Casual"); req.setLeaveUniqueName("casual"); req.setMaxDays(12);
        LeaveType updated = new LeaveType(); updated.setId(1); updated.setLeaveName("Casual");
        when(leaveService.updateLeaveType(eq(1), any())).thenReturn(updated);

        mockMvc.perform(put("/leave/types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveName").value("Casual"));
    }

    @Test
    void deleteLeaveType_returnsNoContent() throws Exception {
        doNothing().when(leaveService).deleteLeaveType(1);

        mockMvc.perform(delete("/leave/types/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getLeaveById_returnsOk() throws Exception {
        Leave leave = buildLeave(1L, "emp@test.com");
        when(leaveService.getLeaveById(1L)).thenReturn(leave);

        mockMvc.perform(get("/leave/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailId").value("emp@test.com"));
    }

    @Test
    void getAllLeaves_returnsOk() throws Exception {
        when(leaveService.getAllLeaves()).thenReturn(List.of(buildLeave(1L, "emp@test.com")));

        mockMvc.perform(get("/leave/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emailId").value("emp@test.com"));
    }

    @Test
    void getPendingLeavesFor_returnsOk() throws Exception {
        when(leaveService.getPendingLeavesFor("mgr@test.com")).thenReturn(List.of(buildLeave(1L, "emp@test.com")));

        mockMvc.perform(get("/leave/pending-for").header("X-User-Email", "mgr@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void getMyLeaves_returnsOk() throws Exception {
        when(leaveService.getLeavesByEmail("emp@test.com")).thenReturn(List.of(buildLeave(1L, "emp@test.com")));

        mockMvc.perform(get("/leave/my").header("X-User-Email", "emp@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void getReviewedLeaves_returnsOk() throws Exception {
        when(leaveService.getReviewedLeavesByReviewer("mgr@test.com")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/leave/reviewed-by-me").header("X-User-Email", "mgr@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void getManagerLoggedLeaves_returnsOk() throws Exception {
        when(leaveService.getManagerLoggedLeaves("mgr@test.com")).thenReturn(Map.of("approved_leaves", List.of()));

        mockMvc.perform(get("/leave/manager-logged-leaves").header("X-User-Email", "mgr@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void getAdminLoggedLeaves_returnsOk() throws Exception {
        when(leaveService.getAdminLoggedLeaves("admin@test.com")).thenReturn(Map.of("approved_leaves", List.of()));

        mockMvc.perform(get("/leave/admin-logged-leaves").header("X-User-Email", "admin@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void partialReview_returnsOk() throws Exception {
        List<Map<String, String>> dayDecisions = List.of(Map.of("date", "2025-06-01", "status", "APPROVED"));
        doNothing().when(leaveProcessService).partialReview(eq(1L), eq("mgr@test.com"), anyList());

        mockMvc.perform(post("/leave/1/partial-review")
                        .header("X-User-Email", "mgr@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dayDecisions)))
                .andExpect(status().isOk());
    }

    @Test
    void updateLeave_returnsOk() throws Exception {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual"); req.setFromDate("2025-06-05"); req.setToDate("2025-06-07");
        req.setReason("Personal"); req.setDayType("FULL_DAY");
        Leave updated = buildLeave(1L, "emp@test.com");
        when(leaveService.updateLeave(eq(1L), any(), eq("emp@test.com"))).thenReturn(updated);

        mockMvc.perform(put("/leave/1")
                        .header("X-User-Email", "emp@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteLeave_returnsNoContent() throws Exception {
        doNothing().when(leaveService).deleteLeave(eq(1L), eq("emp@test.com"));

        mockMvc.perform(delete("/leave/1").header("X-User-Email", "emp@test.com"))
                .andExpect(status().isNoContent());
    }

    @Test
    void approveLeave_returnsOk() throws Exception {
        doNothing().when(leaveProcessService).approveLeave(eq(1L), eq("mgr@test.com"));

        mockMvc.perform(post("/leave/1/approve").header("X-User-Email", "mgr@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectLeave_returnsOk() throws Exception {
        doNothing().when(leaveProcessService).rejectLeave(eq(1L), eq("mgr@test.com"), anyString());

        mockMvc.perform(post("/leave/1/reject")
                        .header("X-User-Email", "mgr@test.com")
                        .param("reason", "Not enough notice"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectLeave_withDefaultReason_returnsOk() throws Exception {
        doNothing().when(leaveProcessService).rejectLeave(eq(1L), eq("mgr@test.com"), eq(LeaveConstants.NO_REASON_PROVIDED));

        mockMvc.perform(post("/leave/1/reject").header("X-User-Email", "mgr@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void createLeave_returnsCreated() throws Exception {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick"); req.setFromDate("2025-06-10"); req.setToDate("2025-06-12");
        req.setReason("Medical"); req.setDayType("FULL_DAY"); req.setManagerEmail("mgr@test.com");
        Leave created = buildLeave(1L, "emp@test.com");
        when(leaveService.createLeave(any(), eq("emp@test.com"))).thenReturn(created);

        mockMvc.perform(post("/leave/create")
                        .header("X-User-Email", "emp@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailId").value("emp@test.com"));
    }
}
