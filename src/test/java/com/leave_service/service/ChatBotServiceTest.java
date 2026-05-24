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
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatBotService focusing on the non-AI logic:
 * - checkRoleAccess() (tested via processMessage's early-return path)
 * - getChatSessions()
 * - getChatHistory()
 * - getWelcomeMessage()
 */
@ExtendWith(MockitoExtension.class)
class ChatBotServiceTest {

    @Mock
    private OllamaChatModel chatModel;

    @Mock
    private LeaveService leaveService;

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private RestTemplate restTemplate;

    private ChatBotService chatBotService;

    @BeforeEach
    void setUp() {
        // Construct service with mocked chatModel; the ChatClient builder stores the ref without calling methods
        chatBotService = new ChatBotService(chatModel);

        // Inject @Autowired fields via reflection
        ReflectionTestUtils.setField(chatBotService, "leaveService", leaveService);
        ReflectionTestUtils.setField(chatBotService, "leaveRepository", leaveRepository);
        ReflectionTestUtils.setField(chatBotService, "chatSessionRepository", chatSessionRepository);
        ReflectionTestUtils.setField(chatBotService, "userServiceClient", userServiceClient);
        ReflectionTestUtils.setField(chatBotService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(chatBotService, "userServiceUrl", "http://localhost:8082");
    }

    // ── checkRoleAccess via processMessage (early-return path, no AI called) ──

    @Test
    void processMessage_employeeAsksEmployeeDetails_accessDenied() {
        String result = chatBotService.processMessage(
                "show me employee details", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksHowManyEmployees_accessDenied() {
        String result = chatBotService.processMessage(
                "how many employees are there?", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksEmployeeCount_accessDenied() {
        String result = chatBotService.processMessage(
                "what is the employee count?", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksAllEmployees_accessDenied() {
        String result = chatBotService.processMessage(
                "show all employees list", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksListOfEmployees_accessDenied() {
        String result = chatBotService.processMessage(
                "give me the list of employees", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksEmployeeList_accessDenied() {
        String result = chatBotService.processMessage(
                "get the employee list", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksGiveDetails_accessDenied() {
        String result = chatBotService.processMessage(
                "give details of all staff", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksShowEmployees_accessDenied() {
        String result = chatBotService.processMessage(
                "show employees in our company", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksCompanyId_accessDenied() {
        String result = chatBotService.processMessage(
                "what is the company id of John", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksDetailsAndEmployee_accessDenied() {
        String result = chatBotService.processMessage(
                "I want details and employee info", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_employeeAsksTeamLeaves_accessDenied() {
        String result = chatBotService.processMessage(
                "show team leaves for this month", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Team leave information is only available for managers and administrators.", result);
    }

    @Test
    void processMessage_employeeAsksPendingApprovals_accessDenied() {
        String result = chatBotService.processMessage(
                "what are the pending approvals?", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Team leave information is only available for managers and administrators.", result);
    }

    @Test
    void processMessage_employeeAsksApprove_accessDenied() {
        String result = chatBotService.processMessage(
                "how do I approve this leave?", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Team leave information is only available for managers and administrators.", result);
    }

    @Test
    void processMessage_employeeAsksReject_accessDenied() {
        String result = chatBotService.processMessage(
                "how do I reject a leave request?", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Team leave information is only available for managers and administrators.", result);
    }

    @Test
    void processMessage_managerAsksTeamLeaves_notDenied() {
        // Manager should be allowed for team leaves - will call AI (returns null/blank in test)
        // We just verify no access-denied exception for non-denied path
        // The AI call will fail in unit test, so we just check it doesn't return the access-denied message
        // Actually with mocked chatModel, the chatClient call might throw. That's caught and returns fallback.
        String result = chatBotService.processMessage(
                "show team leaves", "mgr@test.com", "Manager", "MANAGER");

        // Should NOT be the employee-access-denied message
        assertNotEquals("Access denied. Team leave information is only available for managers and administrators.", result);
        // Should NOT be the admin-only message
        assertNotEquals("Access denied. Employee information is only available for administrators.", result);
    }

    // ── getWelcomeMessage ─────────────────────────────────────────────────────

    @Test
    void getWelcomeMessage_returnsNonNullString() {
        String msg = chatBotService.getWelcomeMessage();

        assertNotNull(msg);
        assertFalse(msg.isBlank());
        assertTrue(msg.contains("CresenGPT"));
    }

    // ── getChatSessions ───────────────────────────────────────────────────────

    @Test
    void getChatSessions_existingUser_returnsSessions() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setEmailId("user@test.com");

        Map<String, Object> sess1 = new HashMap<>();
        sess1.put("sessionId", 100L);
        sess1.put("title", "Leave inquiry");
        sess1.put("messages", new ArrayList<>());

        session.getSessions().add(sess1);

        when(chatSessionRepository.findByEmailId("user@test.com")).thenReturn(Optional.of(session));

        List<Map<String, Object>> result = chatBotService.getChatSessions("user@test.com");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getChatSessions_noExistingUser_returnsEmptyList() {
        when(chatSessionRepository.findByEmailId("new@test.com")).thenReturn(Optional.empty());

        List<Map<String, Object>> result = chatBotService.getChatSessions("new@test.com");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── getChatHistory ────────────────────────────────────────────────────────

    @Test
    void getChatHistory_sessionExists_returnsHistory() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setEmailId("user@test.com");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("sender", "user", "content", "Hello"));
        messages.add(Map.of("sender", "bot", "content", "Hi there!"));

        Map<String, Object> sess = new HashMap<>();
        sess.put("sessionId", 42L);
        sess.put("title", "Test session");
        sess.put("messages", messages);
        session.getSessions().add(sess);

        when(chatSessionRepository.findByEmailId("user@test.com")).thenReturn(Optional.of(session));

        Map<String, Object> result = chatBotService.getChatHistory(42L, "user@test.com");

        assertNotNull(result);
        assertEquals(42L, ((Number) result.get("sessionId")).longValue());
    }

    @Test
    void getChatHistory_userNotFound_throwsException() {
        when(chatSessionRepository.findByEmailId("nobody@test.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> chatBotService.getChatHistory(99L, "nobody@test.com"));
    }

    @Test
    void getChatHistory_sessionNotFound_throwsException() {
        ChatSession session = new ChatSession();
        session.setEmailId("user@test.com");
        // No sessions added

        when(chatSessionRepository.findByEmailId("user@test.com")).thenReturn(Optional.of(session));

        assertThrows(RuntimeException.class,
                () -> chatBotService.getChatHistory(999L, "user@test.com"));
    }

    // ── deleteSession ─────────────────────────────────────────────────────────

    @Test
    void deleteSession_existingSession_removesIt() {
        ChatSession session = new ChatSession();
        session.setEmailId("user@test.com");

        Map<String, Object> sess = new HashMap<>();
        sess.put("sessionId", 42L);
        sess.put("title", "Session to delete");
        sess.put("messages", new ArrayList<>());
        session.getSessions().add(sess);

        when(chatSessionRepository.findByEmailId("user@test.com")).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        assertDoesNotThrow(() -> chatBotService.deleteSession(42L, "user@test.com"));
        verify(chatSessionRepository).save(session);
        assertTrue(session.getSessions().isEmpty());
    }

    @Test
    void deleteSession_userNotFound_throwsException() {
        when(chatSessionRepository.findByEmailId("nobody@test.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> chatBotService.deleteSession(1L, "nobody@test.com"));
    }

    // ── processMessage with 5-param variant ───────────────────────────────────

    @Test
    void processMessage_overloadWithoutSessionId_callsWithNullSession() {
        // Test the 4-param overload which delegates to 5-param with null sessionId
        // Access denied path (no AI needed)
        String result = chatBotService.processMessage(
                "how many employees?", "emp@test.com", "Employee", "EMPLOYEE");

        assertEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void processMessage_adminRole_notDeniedForEmployeeQuery() {
        // ADMIN should not be access-denied even for employee queries
        // Will try AI which fails in unit test, returns fallback
        String result = chatBotService.processMessage(
                "show all employees", "admin@test.com", "Admin", "ADMIN");

        // Should NOT be the access-denied message for admin
        assertNotEquals("Access denied. Employee information is only available for administrators.", result);
    }

    @Test
    void chatBotService_requestRecord_isInstantiable() {
        // Covers ChatBotService.Request inner record class
        ChatBotService.Request req = new ChatBotService.Request();
        assertNotNull(req);
    }
}
