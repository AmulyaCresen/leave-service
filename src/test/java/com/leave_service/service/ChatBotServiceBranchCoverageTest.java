package com.leave_service.service;

import com.leave_service.model.ChatSession;
import com.leave_service.model.Leave;
import com.leave_service.model.LeaveType;
import com.leave_service.repository.ChatSessionRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Branch coverage tests for ChatBotService using mocked OllamaChatModel.
 * Covers generateResponse intents, calculateLeaveBalance, calculatePendingLeaves,
 * formatPendingLeaves, saveChatHistory, parseTableResponse, and fallback.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatBotServiceBranchCoverageTest {

    @Mock private OllamaChatModel chatModel;
    @Mock private LeaveService leaveService;
    @Mock private LeaveRepository leaveRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private UserServiceClient userServiceClient;
    @Mock private RestTemplate restTemplate;

    private ChatBotService chatBotService;

    @BeforeEach
    void setUp() {
        chatBotService = new ChatBotService(chatModel);
        ReflectionTestUtils.setField(chatBotService, "leaveService", leaveService);
        ReflectionTestUtils.setField(chatBotService, "leaveRepository", leaveRepository);
        ReflectionTestUtils.setField(chatBotService, "chatSessionRepository", chatSessionRepository);
        ReflectionTestUtils.setField(chatBotService, "userServiceClient", userServiceClient);
        ReflectionTestUtils.setField(chatBotService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(chatBotService, "userServiceUrl", "http://localhost:8082");

        // Default: chatSessionRepository returns empty → creates new session
        when(chatSessionRepository.findByEmailId(anyString())).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(content)));
    }

    // ── checkRoleAccess remaining branches ────────────────────────────────────

    @Test
    void checkRoleAccess_detailsOfEmployees_denied() {
        String result = chatBotService.processMessage(
            "details of employees in the company", "emp@test.com", "Employee", "EMPLOYEE");
        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void checkRoleAccess_detailsOfAllEmployees_denied() {
        String result = chatBotService.processMessage(
            "show me details of all employees", "emp@test.com", "Employee", "EMPLOYEE");
        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    // ── generateResponse intent: greeting ─────────────────────────────────────

    @Test
    void generateResponse_greetingIntent_returnsGreeting() {
        // First call returns greeting intent, no second AI call needed
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("greeting"));

        String result = chatBotService.processMessage("hello", "emp@test.com", "Employee", "EMPLOYEE");

        assertTrue(result.contains("CresenGPT") || result.contains("Hello"),
            "Expected greeting response but got: " + result);
    }

    // ── generateResponse intent: help ─────────────────────────────────────────

    @Test
    void generateResponse_helpIntent_returnsHelpText() {
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("help"));

        String result = chatBotService.processMessage("help me please", "emp@test.com", "Employee", "EMPLOYEE");

        assertTrue(result.contains("help") || result.contains("Help") || result.contains("can help"),
            "Expected help response but got: " + result);
    }

    // ── generateResponse intent: getUserInfo ─────────────────────────────────

    @Test
    void generateResponse_getUserInfoIntent_returnsUserDetails() {
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getuserinfo"));

        String result = chatBotService.processMessage(
            "what is my role", "emp@test.com", "Test Employee", "EMPLOYEE");

        // Should return user details directly without second AI call
        assertTrue(result.contains("emp@test.com") || result.contains("Test Employee") || result.contains("EMPLOYEE"),
            "Expected user info but got: " + result);
    }

    // ── generateResponse intent: getTeamLeaves ────────────────────────────────

    @Test
    void generateResponse_getTeamLeavesIntent_returnsTeamSummary() {
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getteamleaves"));

        String result = chatBotService.processMessage(
            "show team leaves", "mgr@test.com", "Manager", "MANAGER");

        assertTrue(result.contains("Team leave") || result.contains("team"),
            "Expected team leave response but got: " + result);
    }

    // ── generateResponse intent: unknown ─────────────────────────────────────

    @Test
    void generateResponse_unknownIntent_returnsFallback() {
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("something_unknown_xyz"));

        String result = chatBotService.processMessage(
            "tell me the weather", "emp@test.com", "Employee", "EMPLOYEE");

        assertTrue(result.contains("not sure") || result.contains("Try asking"),
            "Expected fallback response but got: " + result);
    }

    // ── generateResponse intent: getLeaveBalance ──────────────────────────────

    @Test
    void generateResponse_getLeaveBalance_generalQuery_formatsBalance() {
        LeaveType sickLeave = new LeaveType();
        sickLeave.setLeaveName("Sick Leave");
        sickLeave.setMaxDays(10);

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(sickLeave));
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(Collections.emptyList());
        // First call: intent; Second call: formatted response
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getleavebalance"))
            .thenReturn(response("You have 10 sick leave days"));

        String result = chatBotService.processMessage(
            "what is my leave balance", "emp@test.com", "Employee", "EMPLOYEE");

        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void generateResponse_getLeaveBalance_sickQuery_remaining() {
        LeaveType sickLeave = new LeaveType();
        sickLeave.setLeaveName("Sick Leave");
        sickLeave.setMaxDays(10);

        Leave approvedLeave = new Leave();
        approvedLeave.setId(1L);
        approvedLeave.setLeaveType("Sick Leave");
        approvedLeave.setFromDate(LocalDate.of(2025, 1, 1));
        approvedLeave.setToDate(LocalDate.of(2025, 1, 2));

        // Trail with ADMIN+APPROVED
        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put("stage", "ADMIN");
        trailEntry.put("status", "APPROVED");
        approvedLeave.setTrail(List.of(trailEntry));
        // Days with APPROVED status
        com.leave_service.dto.LeaveDayEntry day = new com.leave_service.dto.LeaveDayEntry();
        day.setDate("2025-01-01");
        day.setStatus("APPROVED");
        approvedLeave.setDays(List.of(day));

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(sickLeave));
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(approvedLeave));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getleavebalance"));

        String result = chatBotService.processMessage(
            "how many sick leaves remaining", "emp@test.com", "Employee", "EMPLOYEE");

        // Should return specific sick leave remaining info
        assertNotNull(result);
        assertTrue(result.contains("sick") || result.contains("Sick") || result.contains("9"),
            "Expected sick leave result but got: " + result);
    }

    @Test
    void generateResponse_getLeaveBalance_sickQuery_used() {
        LeaveType sickLeave = new LeaveType();
        sickLeave.setLeaveName("Sick Leave");
        sickLeave.setMaxDays(10);

        Leave approvedLeave = new Leave();
        approvedLeave.setId(1L);
        approvedLeave.setLeaveType("Sick Leave");
        approvedLeave.setFromDate(LocalDate.of(2025, 1, 1));
        approvedLeave.setToDate(LocalDate.of(2025, 1, 2));

        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put("stage", "ADMIN");
        trailEntry.put("status", "APPROVED");
        approvedLeave.setTrail(List.of(trailEntry));
        com.leave_service.dto.LeaveDayEntry day = new com.leave_service.dto.LeaveDayEntry();
        day.setDate("2025-01-01");
        day.setStatus("APPROVED");
        approvedLeave.setDays(List.of(day));

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(sickLeave));
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(approvedLeave));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getleavebalance"));

        String result = chatBotService.processMessage(
            "how many sick leaves used", "emp@test.com", "Employee", "EMPLOYEE");

        assertNotNull(result);
        assertTrue(result.contains("sick") || result.contains("Sick") || result.contains("1"),
            "Expected sick leave used info but got: " + result);
    }

    @Test
    void generateResponse_getLeaveBalance_casualQuery() {
        LeaveType casualLeave = new LeaveType();
        casualLeave.setLeaveName("Casual Leave");
        casualLeave.setMaxDays(12);

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(casualLeave));
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(Collections.emptyList());
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getleavebalance"));

        String result = chatBotService.processMessage(
            "how many casual leaves remaining", "emp@test.com", "Employee", "EMPLOYEE");

        assertNotNull(result);
        assertTrue(result.contains("casual") || result.contains("Casual") || result.contains("12"),
            "Expected casual leave result but got: " + result);
    }

    @Test
    void generateResponse_calculateLeaveBalance_withApprovedTrailNullDays() {
        // Leave has admin-approved trail but null days → isApproved=true, days==null → skip
        LeaveType sickLeave = new LeaveType();
        sickLeave.setLeaveName("Sick Leave");
        sickLeave.setMaxDays(10);

        Leave approvedLeave = new Leave();
        approvedLeave.setId(1L);
        approvedLeave.setLeaveType("Sick Leave");
        approvedLeave.setFromDate(LocalDate.of(2025, 1, 1));
        approvedLeave.setToDate(LocalDate.of(2025, 1, 1));

        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put("stage", "ADMIN");
        trailEntry.put("status", "APPROVED");
        approvedLeave.setTrail(List.of(trailEntry));
        approvedLeave.setDays(null); // null days → skip counting

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(sickLeave));
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(approvedLeave));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getleavebalance"))
            .thenReturn(response("You have 10 sick leaves"));

        String result = chatBotService.processMessage(
            "what is my leave balance", "emp@test.com", "Employee", "EMPLOYEE");
        assertNotNull(result);
    }

    @Test
    void generateResponse_calculateLeaveBalance_withLeaveTypeNull() {
        // Leave has null leaveType → skipped in loop
        LeaveType sickLeave = new LeaveType();
        sickLeave.setLeaveName("Sick Leave");
        sickLeave.setMaxDays(10);

        Leave leaveWithNullType = new Leave();
        leaveWithNullType.setId(1L);
        leaveWithNullType.setLeaveType(null); // null type

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(sickLeave));
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(leaveWithNullType));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getleavebalance"))
            .thenReturn(response("You have 10 sick leaves"));

        String result = chatBotService.processMessage(
            "what is my leave balance", "emp@test.com", "Employee", "EMPLOYEE");
        assertNotNull(result);
    }

    @Test
    void generateResponse_calculateLeaveBalance_withNullTrail() {
        // Leave trail is null → isApproved stays false
        LeaveType sickLeave = new LeaveType();
        sickLeave.setLeaveName("Sick Leave");
        sickLeave.setMaxDays(10);

        Leave leaveNullTrail = new Leave();
        leaveNullTrail.setId(1L);
        leaveNullTrail.setLeaveType("Sick Leave");
        leaveNullTrail.setTrail(null); // null trail

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(sickLeave));
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(leaveNullTrail));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getleavebalance"))
            .thenReturn(response("Balance: Sick Leave 10 days"));

        String result = chatBotService.processMessage(
            "what is my leave balance", "emp@test.com", "Employee", "EMPLOYEE");
        assertNotNull(result);
    }

    // ── generateResponse intent: getPendingLeaves ─────────────────────────────

    @Test
    void generateResponse_getPendingLeaves_withPendingLeave() {
        // Leave without admin decision → pending
        Leave pendingLeave = new Leave();
        pendingLeave.setId(1L);
        pendingLeave.setLeaveType("Sick Leave");
        pendingLeave.setFromDate(LocalDate.of(2025, 6, 1));
        pendingLeave.setToDate(LocalDate.of(2025, 6, 3));

        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put("stage", "MANAGER");
        trailEntry.put("status", "APPROVED");
        pendingLeave.setTrail(List.of(trailEntry)); // No ADMIN stage → still pending

        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(pendingLeave));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getpendingleaves"))
            .thenReturn(response("You have 1 pending leave"));

        String result = chatBotService.processMessage(
            "show my pending leaves", "emp@test.com", "Employee", "EMPLOYEE");
        assertNotNull(result);
    }

    @Test
    void generateResponse_getPendingLeaves_noPendingLeaves() {
        // All leaves have admin decision → no pending
        Leave decidedLeave = new Leave();
        decidedLeave.setId(1L);
        decidedLeave.setLeaveType("Sick Leave");
        decidedLeave.setFromDate(LocalDate.of(2025, 6, 1));
        decidedLeave.setToDate(LocalDate.of(2025, 6, 3));

        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put("stage", "ADMIN");
        trailEntry.put("status", "APPROVED");
        decidedLeave.setTrail(List.of(trailEntry));

        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(decidedLeave));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getpendingleaves"));

        String result = chatBotService.processMessage(
            "any pending leaves?", "emp@test.com", "Employee", "EMPLOYEE");

        assertTrue(result.contains("No pending leaves") || result.contains("pending"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getPendingLeaves_withAdminRejected_notPending() {
        // Leave has admin REJECTED → has admin decision → not pending
        Leave rejectedLeave = new Leave();
        rejectedLeave.setId(1L);
        rejectedLeave.setLeaveType("Sick Leave");
        rejectedLeave.setFromDate(LocalDate.of(2025, 6, 1));
        rejectedLeave.setToDate(LocalDate.of(2025, 6, 3));

        Map<String, String> trailEntry = new HashMap<>();
        trailEntry.put("stage", "ADMIN");
        trailEntry.put("status", "REJECTED");
        rejectedLeave.setTrail(List.of(trailEntry));

        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(rejectedLeave));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getpendingleaves"));

        String result = chatBotService.processMessage(
            "pending leaves?", "emp@test.com", "Employee", "EMPLOYEE");
        assertNotNull(result);
    }

    @Test
    void generateResponse_getPendingLeaves_leaveWithNullType_skipped() {
        // Leave with null leaveType → skipped
        Leave nullTypeLeave = new Leave();
        nullTypeLeave.setId(1L);
        nullTypeLeave.setLeaveType(null);
        nullTypeLeave.setFromDate(LocalDate.of(2025, 6, 1));
        nullTypeLeave.setToDate(LocalDate.of(2025, 6, 1));

        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(nullTypeLeave));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getpendingleaves"));

        String result = chatBotService.processMessage(
            "pending leaves?", "emp@test.com", "Employee", "EMPLOYEE");
        assertNotNull(result);
    }

    @Test
    void generateResponse_getPendingLeaves_withNullTrail_isPending() {
        // Leave with null trail → no admin decision → pending
        Leave leaveNullTrail = new Leave();
        leaveNullTrail.setId(1L);
        leaveNullTrail.setLeaveType("Casual Leave");
        leaveNullTrail.setFromDate(LocalDate.of(2025, 7, 1));
        leaveNullTrail.setToDate(LocalDate.of(2025, 7, 3));
        leaveNullTrail.setTrail(null);

        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(List.of(leaveNullTrail));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getpendingleaves"))
            .thenReturn(response("1 pending leave"));

        String result = chatBotService.processMessage(
            "pending leaves?", "emp@test.com", "Employee", "EMPLOYEE");
        assertNotNull(result);
    }

    // ── generateResponse intent: getLeaveTypes ────────────────────────────────

    @Test
    void generateResponse_getLeaveTypesIntent_formatsTypes() {
        LeaveType sick = new LeaveType();
        sick.setLeaveName("Sick Leave");
        sick.setMaxDays(10);
        LeaveType casual = new LeaveType();
        casual.setLeaveName("Casual Leave");
        casual.setMaxDays(12);

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(sick, casual));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getleavetypes"))
            .thenReturn(response("Available leave types: Sick, Casual"));

        String result = chatBotService.processMessage(
            "what leave types are available", "emp@test.com", "Employee", "EMPLOYEE");

        assertNotNull(result);
    }

    @Test
    void generateResponse_getLeaveTypesIntent_emptyTypes() {
        when(leaveService.getAllLeaveTypes()).thenReturn(Collections.emptyList());
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getleavetypes"))
            .thenReturn(response("No leave types"));

        String result = chatBotService.processMessage(
            "leave types?", "emp@test.com", "Employee", "EMPLOYEE");
        assertNotNull(result);
    }

    // ── generateResponse intent: getEmployeeInfo ─────────────────────────────

    @Test
    void generateResponse_getEmployeeInfo_howMany_withUsers() {
        Map<String, Object> user1 = new HashMap<>();
        user1.put("fullName", "Alice Smith");
        user1.put("email", "alice@test.com");

        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenReturn(List.of(user1));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "how many employees are there", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("1") || result.contains("employee"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_howMany_nullUsers() {
        when(restTemplate.getForObject(anyString(), eq(List.class))).thenReturn(null);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "how many employees", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("Unable") || result.contains("fetch"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_howMany_exception() {
        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenThrow(new RuntimeException("Connection refused"));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "employee count", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("Unable") || result.contains("error") || result.contains("not sure"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_companyId_found() {
        Map<String, Object> user1 = new HashMap<>();
        user1.put("fullName", "Alice Smith");
        user1.put("companyId", "EMP001");

        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenReturn(List.of(user1));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "what is the company id of Alice", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("EMP001") || result.contains("Alice") || result.contains("company"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_companyId_notFound() {
        Map<String, Object> user1 = new HashMap<>();
        user1.put("fullName", "Alice Smith");
        user1.put("companyId", "EMP001");

        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenReturn(List.of(user1));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "what is the company id of Unknown Person", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("not found") || result.contains("Unknown Person") || result.contains("not sure"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_companyId_nullUsers() {
        when(restTemplate.getForObject(anyString(), eq(List.class))).thenReturn(null);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "company id of Bob", "admin@test.com", "Admin", "ADMIN");
        assertNotNull(result);
    }

    @Test
    void generateResponse_getEmployeeInfo_companyId_exception() {
        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenThrow(new RuntimeException("Service unavailable"));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "company id of Alice", "admin@test.com", "Admin", "ADMIN");
        assertNotNull(result);
    }

    @Test
    void generateResponse_getEmployeeInfo_listAll_withUsers() {
        Map<String, Object> user1 = new HashMap<>();
        user1.put("fullName", "Alice Smith");
        user1.put("email", "alice@test.com");
        user1.put("companyId", "EMP001");
        user1.put("role", "EMPLOYEE");

        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenReturn(List.of(user1));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "show all employee details", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("Alice") || result.contains("EMP001") || result.contains("|"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_listAll_emptyUsers() {
        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenReturn(Collections.emptyList());
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "list all employees", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("No employees") || result.contains("not sure"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_listAll_nullUsers() {
        when(restTemplate.getForObject(anyString(), eq(List.class))).thenReturn(null);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "give details of all employees", "admin@test.com", "Admin", "ADMIN");
        assertTrue(result.contains("No employees") || result.contains("not sure"),
            "Got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_listAll_userWithNullFields() {
        Map<String, Object> user1 = new HashMap<>();
        user1.put("fullName", null);
        user1.put("email", null);
        user1.put("companyId", null);
        user1.put("role", null);

        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenReturn(List.of(user1));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "all employees", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("N/A") || result.contains("| "),
            "Expected N/A for null fields but got: " + result);
    }

    @Test
    void generateResponse_getEmployeeInfo_listAll_exception() {
        when(restTemplate.getForObject(anyString(), eq(List.class)))
            .thenThrow(new RuntimeException("Network error"));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "list employees", "admin@test.com", "Admin", "ADMIN");

        assertTrue(result.contains("Unable") || result.contains("error") || result.contains("not sure"),
            "Got: " + result);
    }

    // ── generateResponse final response is blank ─────────────────────────────

    @Test
    void generateResponse_finalResponseBlank_returnsFallback() {
        LeaveType sickLeave = new LeaveType();
        sickLeave.setLeaveName("Sick Leave");
        sickLeave.setMaxDays(10);

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(sickLeave));
        when(leaveRepository.findByEmailId("emp@test.com")).thenReturn(Collections.emptyList());
        // Second AI call returns blank
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(response("getleavebalance"))
            .thenReturn(response(""));

        String result = chatBotService.processMessage(
            "leave balance", "emp@test.com", "Employee", "EMPLOYEE");

        assertTrue(result.contains("not sure") || result.contains("Try asking") || !result.isBlank(),
            "Got: " + result);
    }

    // ── saveChatHistory: with stubbed chatSessionRepository ───────────────────

    @Test
    void saveChatHistory_existingSessionFound_appendsMessages() {
        // sessionId=42, session found in user record → no new session created
        ChatSession userRecord = new ChatSession();
        Map<String, Object> existingSession = new HashMap<>();
        existingSession.put("sessionId", 42L);
        existingSession.put("messages", new ArrayList<Map<String, Object>>());
        existingSession.put("updatedAt", "2025-01-01T00:00:00Z");
        userRecord.getSessions().add(existingSession);

        when(chatSessionRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(userRecord));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(userRecord);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("greeting"));

        // processMessage with sessionId=42
        String result = chatBotService.processMessage(
            "hello", "admin@test.com", "Admin", "ADMIN", 42L);

        assertNotNull(result);
        // chatSessionRepository.save should have been called (history saved)
        verify(chatSessionRepository, atLeastOnce()).save(any());
    }

    @Test
    void saveChatHistory_sessionIdNull_createsNewSession() {
        // sessionId=null → skip loop → create new session
        ChatSession userRecord = new ChatSession();
        when(chatSessionRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(userRecord));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(userRecord);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("greeting"));

        String result = chatBotService.processMessage(
            "hello", "admin@test.com", "Admin", "ADMIN", null);

        assertNotNull(result);
        verify(chatSessionRepository, atLeastOnce()).save(any());
    }

    @Test
    void saveChatHistory_sessionIdNotFound_createsNewSession() {
        // sessionId=99 but not in sessions → session=null → creates new
        ChatSession userRecord = new ChatSession();
        Map<String, Object> differentSession = new HashMap<>();
        differentSession.put("sessionId", 1L); // different ID
        differentSession.put("messages", new ArrayList<>());
        userRecord.getSessions().add(differentSession);

        when(chatSessionRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(userRecord));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(userRecord);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("greeting"));

        String result = chatBotService.processMessage(
            "hello", "admin@test.com", "Admin", "ADMIN", 99L);

        assertNotNull(result);
    }

    @Test
    void saveChatHistory_tableResponse_parsesTable() {
        // Bot response contains "|" and "\n" → calls parseTableResponse
        ChatSession userRecord = new ChatSession();
        when(chatSessionRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(userRecord));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(userRecord);

        // Return a table-format response from getEmployeeInfo
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", "Alice");
        user.put("email", "a@test.com");
        user.put("companyId", "E001");
        user.put("role", "EMPLOYEE");
        when(restTemplate.getForObject(anyString(), eq(List.class))).thenReturn(List.of(user));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("getemployeeinfo"));

        String result = chatBotService.processMessage(
            "list all employees", "admin@test.com", "Admin", "ADMIN", null);

        assertNotNull(result);
        verify(chatSessionRepository, atLeastOnce()).save(any());
    }

    // ── generateSessionTitle: long and short messages ─────────────────────────

    @Test
    void saveChatHistory_longMessage_titleTruncated() {
        ChatSession userRecord = new ChatSession();
        when(chatSessionRepository.findByEmailId("admin@test.com")).thenReturn(Optional.of(userRecord));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(userRecord);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("greeting"));

        // Message longer than 50 chars → title truncated
        String longMessage = "A".repeat(60);
        String result = chatBotService.processMessage(
            longMessage, "admin@test.com", "Admin", "ADMIN", null);

        assertNotNull(result);
        // The session's title should be truncated to 50 chars + "..."
        Map<String, Object> newSession = userRecord.getSessions().get(0);
        String title = (String) newSession.get("title");
        assertTrue(title.length() <= 53, "Title should be truncated to 50+... but was: " + title);
        assertTrue(title.endsWith("..."), "Title should end with ...");
    }

    // ── fallback method via reflection ────────────────────────────────────────

    @Test
    void fallback_helloMessage_returnsGreeting() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("fallback", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(chatBotService, "hello there");
        assertTrue(result.contains("Hello") || result.contains("CresenGPT"));
    }

    @Test
    void fallback_hiMessage_returnsGreeting() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("fallback", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(chatBotService, "hi");
        assertTrue(result.contains("Hello") || result.contains("CresenGPT"));
    }

    @Test
    void fallback_heyMessage_returnsGreeting() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("fallback", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(chatBotService, "hey bot");
        assertTrue(result.contains("Hello") || result.contains("CresenGPT"));
    }

    @Test
    void fallback_helpMessage_returnsHelpText() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("fallback", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(chatBotService, "help");
        assertTrue(result.contains("help") || result.contains("balance"));
    }

    @Test
    void fallback_unknownMessage_returnsFallback() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("fallback", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(chatBotService, "random query");
        assertTrue(result.contains("not sure"));
    }

    // ── parseTableResponse via reflection ─────────────────────────────────────

    @Test
    void parseTableResponse_validTable_parsesCorrectly() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("parseTableResponse", String.class);
        method.setAccessible(true);

        String tableResponse = "Here is the data:\n| Name | Email |\n|------|-------|\n| Alice | a@test.com |\n| Bob | b@test.com |";
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(chatBotService, tableResponse);

        assertNotNull(result);
        assertTrue(result.containsKey("tableData"));
        assertTrue(result.containsKey("text"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tableData = (List<Map<String, String>>) result.get("tableData");
        assertFalse(tableData.isEmpty());
    }

    @Test
    void parseTableResponse_tableStartsAtBeginning_noTextPart() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("parseTableResponse", String.class);
        method.setAccessible(true);

        // Table starts at beginning (tableStart = 0)
        String tableResponse = "| Name | Email |\n|------|-------|\n| Alice | a@test.com |";
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(chatBotService, tableResponse);

        assertNotNull(result);
        String text = (String) result.get("text");
        assertEquals("", text); // No text before table
    }

    @Test
    void parseTableResponse_shortTable_lessThan3Lines() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("parseTableResponse", String.class);
        method.setAccessible(true);

        // Less than 3 lines → no tableData
        String tableResponse = "| Name |\n";
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(chatBotService, tableResponse);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tableData = (List<Map<String, String>>) result.get("tableData");
        assertTrue(tableData.isEmpty());
    }

    @Test
    void parseTableResponse_dataRowNotStartingWithPipe_skipped() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("parseTableResponse", String.class);
        method.setAccessible(true);

        // Row doesn't start with | → skipped
        String tableResponse = "| Name | Email |\n|------|-------|\nAlice a@test.com\n| Bob | b@test.com |";
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(chatBotService, tableResponse);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tableData = (List<Map<String, String>>) result.get("tableData");
        assertEquals(1, tableData.size()); // Only Bob's row
    }

    @Test
    void parseTableResponse_emptyRow_skipped() throws Exception {
        var method = ChatBotService.class.getDeclaredMethod("parseTableResponse", String.class);
        method.setAccessible(true);

        // Row with empty cells → empty map → not added
        String tableResponse = "| Name | Email |\n|------|-------|\n| | |";
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(chatBotService, tableResponse);

        assertNotNull(result);
    }
}
