package com.leave_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_service.dto.CreateLeaveRequest;
import com.leave_service.dto.CreateLeaveTypeRequest;
import com.leave_service.dto.HolidayRequest;
import com.leave_service.dto.UpdateLeaveRequest;
import com.leave_service.model.Holiday;
import com.leave_service.model.Leave;
import com.leave_service.model.LeaveFile;
import com.leave_service.model.LeaveType;
import com.leave_service.repository.LeaveFileRepository;
import com.leave_service.service.LeaveProcessService;
import com.leave_service.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaveController.class)
@AutoConfigureMockMvc(addFilters = false)
class LeaveControllerAdditionalCoverageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LeaveService leaveService;

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
        testLeave.setEmailId("employee@test.com");
        testLeave.setLeaveType("Sick Leave");
        testLeave.setFromDate(LocalDate.of(2025, 6, 1));
        testLeave.setToDate(LocalDate.of(2025, 6, 3));
        testLeave.setReason("Medical");
        testLeave.setManagerEmail("manager@test.com");
        testLeave.setEditable(true);

        testLeaveType = new LeaveType();
        testLeaveType.setId(1);
        testLeaveType.setLeaveName("Sick Leave");
        testLeaveType.setLeaveUniqueName("SICK");
        testLeaveType.setDescription("Sick leave policy");
        testLeaveType.setMaxDays(10);
        testLeaveType.setCreatedAt(OffsetDateTime.now());

        testHoliday = new Holiday();
        testHoliday.setId(1L);
        testHoliday.setName("New Year");
        testHoliday.setDate(LocalDate.of(2025, 1, 1));
    }

    // ── Holidays ─────────────────────────────────────────────────────────────

    @Test
    void getHolidays_returnsHolidayList() throws Exception {
        when(leaveService.getAllHolidays()).thenReturn(List.of(testHoliday));

        mockMvc.perform(get("/leave/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("New Year"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getHolidays_emptyList_returnsOk() throws Exception {
        when(leaveService.getAllHolidays()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/leave/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createHoliday_returnsCreated() throws Exception {
        HolidayRequest req = new HolidayRequest();
        req.setName("New Year");
        req.setDate("2025-01-01");
        when(leaveService.createHoliday(any(HolidayRequest.class))).thenReturn(testHoliday);

        mockMvc.perform(post("/leave/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Year"));
    }

    @Test
    void updateHoliday_returnsOk() throws Exception {
        HolidayRequest req = new HolidayRequest();
        req.setName("Updated Holiday");
        req.setDate("2025-01-01");
        when(leaveService.updateHoliday(eq(1L), any(HolidayRequest.class))).thenReturn(testHoliday);

        mockMvc.perform(put("/leave/holidays/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Year"));
    }

    @Test
    void deleteHoliday_returnsNoContent() throws Exception {
        doNothing().when(leaveService).deleteHoliday(1L);

        mockMvc.perform(delete("/leave/holidays/1"))
                .andExpect(status().isNoContent());

        verify(leaveService).deleteHoliday(1L);
    }

    // ── Leave Types ───────────────────────────────────────────────────────────

    @Test
    void getLeaveTypes_returnsTypeList() throws Exception {
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(testLeaveType));

        mockMvc.perform(get("/leave/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveName").value("Sick Leave"))
                .andExpect(jsonPath("$[0].maxDays").value(10));
    }

    @Test
    void checkLeaveName_exists_returnsTrue() throws Exception {
        when(leaveService.leaveNameExists("Sick Leave")).thenReturn(true);

        mockMvc.perform(get("/leave/types/check-name").param("name", "Sick Leave"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void checkLeaveName_notExists_returnsFalse() throws Exception {
        when(leaveService.leaveNameExists("Unknown")).thenReturn(false);

        mockMvc.perform(get("/leave/types/check-name").param("name", "Unknown"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void checkLeaveUniqueName_exists_returnsTrue() throws Exception {
        when(leaveService.leaveUniqueNameExists("SICK")).thenReturn(true);

        mockMvc.perform(get("/leave/types/check-unique-name").param("uniqueName", "SICK"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void checkLeaveUniqueName_notExists_returnsFalse() throws Exception {
        when(leaveService.leaveUniqueNameExists("UNKNOWN")).thenReturn(false);

        mockMvc.perform(get("/leave/types/check-unique-name").param("uniqueName", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void createLeaveType_returnsCreated() throws Exception {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Sick Leave");
        req.setLeaveUniqueName("SICK");
        req.setDescription("Medical leave");
        req.setMaxDays(10);
        when(leaveService.createLeaveType(any(CreateLeaveTypeRequest.class))).thenReturn(testLeaveType);

        mockMvc.perform(post("/leave/types/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveName").value("Sick Leave"));
    }

    @Test
    void updateLeaveType_returnsOk() throws Exception {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest();
        req.setLeaveName("Updated Sick Leave");
        req.setLeaveUniqueName("SICK_UPDATED");
        req.setMaxDays(12);
        when(leaveService.updateLeaveType(eq(1), any(CreateLeaveTypeRequest.class))).thenReturn(testLeaveType);

        mockMvc.perform(put("/leave/types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteLeaveType_returnsNoContent() throws Exception {
        doNothing().when(leaveService).deleteLeaveType(1);

        mockMvc.perform(delete("/leave/types/1"))
                .andExpect(status().isNoContent());

        verify(leaveService).deleteLeaveType(1);
    }

    // ── Leave CRUD ────────────────────────────────────────────────────────────

    @Test
    void getLeaveById_returnsLeave() throws Exception {
        when(leaveService.getLeaveById(1L)).thenReturn(testLeave);

        mockMvc.perform(get("/leave/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailId").value("employee@test.com"))
                .andExpect(jsonPath("$.leaveType").value("Sick Leave"));
    }

    @Test
    void getAllLeaves_returnsLeaveList() throws Exception {
        when(leaveService.getAllLeaves()).thenReturn(List.of(testLeave));

        mockMvc.perform(get("/leave/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getPendingLeavesFor_returnsLeaveList() throws Exception {
        when(leaveService.getPendingLeavesFor("manager@test.com")).thenReturn(List.of(testLeave));

        mockMvc.perform(get("/leave/pending-for")
                        .header("X-User-Email", "manager@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emailId").value("employee@test.com"));
    }

    @Test
    void getMyLeaves_returnsLeaveList() throws Exception {
        when(leaveService.getLeavesByEmail("employee@test.com")).thenReturn(List.of(testLeave));

        mockMvc.perform(get("/leave/my")
                        .header("X-User-Email", "employee@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveType").value("Sick Leave"));
    }

    @Test
    void getReviewedLeaves_returnsReviewedList() throws Exception {
        when(leaveService.getReviewedLeavesByReviewer("manager@test.com")).thenReturn(List.of(testLeave));

        mockMvc.perform(get("/leave/reviewed-by-me")
                        .header("X-User-Email", "manager@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getManagerLoggedLeaves_returnsMap() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("approved_leaves", List.of());
        result.put("managerEmail", "manager@test.com");
        when(leaveService.getManagerLoggedLeaves("manager@test.com")).thenReturn(result);

        mockMvc.perform(get("/leave/manager-logged-leaves")
                        .header("X-User-Email", "manager@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerEmail").value("manager@test.com"));
    }

    @Test
    void getAdminLoggedLeaves_returnsMap() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("approved_leaves", List.of());
        result.put("adminEmail", "admin@test.com");
        when(leaveService.getAdminLoggedLeaves("admin@test.com")).thenReturn(result);

        mockMvc.perform(get("/leave/admin-logged-leaves")
                        .header("X-User-Email", "admin@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminEmail").value("admin@test.com"));
    }

    @Test
    void createLeave_returnsCreated() throws Exception {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveType("Sick Leave");
        req.setFromDate("2025-06-01");
        req.setToDate("2025-06-03");
        req.setReason("Medical");
        req.setManagerEmail("manager@test.com");
        when(leaveService.createLeave(any(CreateLeaveRequest.class), eq("employee@test.com"))).thenReturn(testLeave);

        mockMvc.perform(post("/leave/create")
                        .header("X-User-Email", "employee@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveType").value("Sick Leave"));
    }

    @Test
    void updateLeave_returnsUpdatedLeave() throws Exception {
        UpdateLeaveRequest req = new UpdateLeaveRequest();
        req.setLeaveType("Casual Leave");
        req.setFromDate("2025-06-01");
        req.setToDate("2025-06-05");
        req.setReason("Personal");
        when(leaveService.updateLeave(eq(1L), any(UpdateLeaveRequest.class), eq("employee@test.com")))
                .thenReturn(testLeave);

        mockMvc.perform(put("/leave/1")
                        .header("X-User-Email", "employee@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteLeave_returnsNoContent() throws Exception {
        doNothing().when(leaveService).deleteLeave(1L, "employee@test.com");

        mockMvc.perform(delete("/leave/1")
                        .header("X-User-Email", "employee@test.com"))
                .andExpect(status().isNoContent());

        verify(leaveService).deleteLeave(1L, "employee@test.com");
    }

    // ── Approve / Reject / Partial ────────────────────────────────────────────

    @Test
    void approveLeave_returnsOk() throws Exception {
        doNothing().when(leaveProcessService).approveLeave(1L, "manager@test.com");

        mockMvc.perform(post("/leave/1/approve")
                        .header("X-User-Email", "manager@test.com"))
                .andExpect(status().isOk());

        verify(leaveProcessService).approveLeave(1L, "manager@test.com");
    }

    @Test
    void rejectLeave_withReason_returnsOk() throws Exception {
        doNothing().when(leaveProcessService).rejectLeave(eq(1L), eq("manager@test.com"), anyString());

        mockMvc.perform(post("/leave/1/reject")
                        .header("X-User-Email", "manager@test.com")
                        .param("reason", "Not enough notice"))
                .andExpect(status().isOk());

        verify(leaveProcessService).rejectLeave(1L, "manager@test.com", "Not enough notice");
    }

    @Test
    void rejectLeave_noReason_usesDefault() throws Exception {
        doNothing().when(leaveProcessService).rejectLeave(eq(1L), eq("manager@test.com"), anyString());

        mockMvc.perform(post("/leave/1/reject")
                        .header("X-User-Email", "manager@test.com"))
                .andExpect(status().isOk());

        verify(leaveProcessService).rejectLeave(eq(1L), eq("manager@test.com"), anyString());
    }

    @Test
    void partialReview_returnsOk() throws Exception {
        List<Map<String, String>> dayDecisions = List.of(
                Map.of("date", "2025-06-01", "status", "APPROVED"),
                Map.of("date", "2025-06-02", "status", "REJECTED", "reason", "No cover")
        );
        doNothing().when(leaveProcessService).partialReview(eq(1L), eq("manager@test.com"), anyList());

        mockMvc.perform(post("/leave/1/partial-review")
                        .header("X-User-Email", "manager@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dayDecisions)))
                .andExpect(status().isOk());

        verify(leaveProcessService).partialReview(eq(1L), eq("manager@test.com"), anyList());
    }

    // ── Document Upload / Files ───────────────────────────────────────────────

    @Test
    void uploadDocument_success_returnsFileInfo() throws Exception {
        testLeave.setEmailId("employee@test.com");
        when(leaveService.getLeaveById(1L)).thenReturn(testLeave);

        // Controller uses the entity's own id after save (not the returned object),
        // so we must set the id on the argument passed to save()
        doAnswer(invocation -> {
            LeaveFile lf = invocation.getArgument(0);
            lf.setId(10L);
            return lf;
        }).when(leaveFileRepository).save(any(LeaveFile.class));

        MockMultipartFile file = new MockMultipartFile(
                "file", "medical.pdf", MediaType.APPLICATION_PDF_VALUE, "PDF content".getBytes());

        mockMvc.perform(multipart("/leave/1/upload-document")
                        .file(file)
                        .header("X-User-Email", "employee@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("medical.pdf"));
    }

    @Test
    void uploadDocument_forbidden_returnsForbidden() throws Exception {
        testLeave.setEmailId("employee@test.com");
        when(leaveService.getLeaveById(1L)).thenReturn(testLeave);

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, "content".getBytes());

        mockMvc.perform(multipart("/leave/1/upload-document")
                        .file(file)
                        .header("X-User-Email", "other@test.com"))
                .andExpect(status().isForbidden());

        verify(leaveFileRepository, never()).save(any());
    }

    @Test
    void getLeaveFiles_returnsFileList() throws Exception {
        LeaveFile file1 = new LeaveFile();
        file1.setId(1L);
        file1.setLeaveId(1L);
        file1.setFileName("medical.pdf");
        file1.setUploadedAt(LocalDateTime.now());

        when(leaveFileRepository.findByLeaveId(1L)).thenReturn(List.of(file1));

        mockMvc.perform(get("/leave/1/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("medical.pdf"));
    }

    @Test
    void getLeaveFiles_empty_returnsEmptyArray() throws Exception {
        when(leaveFileRepository.findByLeaveId(99L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/leave/99/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
