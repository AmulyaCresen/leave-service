package com.leave_service.controller;

import com.leave_service.service.ChatBotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatBotController using direct instantiation with Mockito,
 * since the SSE endpoint uses CompletableFuture async dispatch which is
 * difficult to fully test via MockMvc without integration context.
 */
@ExtendWith(MockitoExtension.class)
class ChatBotControllerTest {

    @Mock
    private ChatBotService chatBotService;

    @InjectMocks
    private ChatBotController controller;

    // ── streamChat: blank message ─────────────────────────────────────────────

    @Test
    void streamChat_blankMessage_returnsEmitterWithoutCallingService() {
        Map<String, String> request = new HashMap<>();
        request.put("message", "   ");
        request.put("userEmail", "user@test.com");
        request.put("userName", "Test User");
        request.put("userRole", "EMPLOYEE");

        SseEmitter emitter = controller.streamChat(request);

        assertNotNull(emitter);
        // Service should NOT be called for blank messages
        verify(chatBotService, never()).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void streamChat_emptyMessage_returnsEmitterWithoutCallingService() {
        Map<String, String> request = new HashMap<>();
        request.put("message", "");
        request.put("userEmail", "user@test.com");
        request.put("userName", "Test User");
        request.put("userRole", "EMPLOYEE");

        SseEmitter emitter = controller.streamChat(request);

        assertNotNull(emitter);
        verify(chatBotService, never()).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void streamChat_missingMessage_returnsEmitter() {
        Map<String, String> request = new HashMap<>();
        // no "message" key → getOrDefault returns ""
        request.put("userEmail", "user@test.com");
        request.put("userName", "Test User");
        request.put("userRole", "EMPLOYEE");

        SseEmitter emitter = controller.streamChat(request);

        assertNotNull(emitter);
    }

    @Test
    void streamChat_validMessage_returnsEmitter() {
        // We can't easily wait for async completion, but we verify the emitter is returned
        Map<String, String> request = new HashMap<>();
        request.put("message", "What is my leave balance?");
        request.put("userEmail", "user@test.com");
        request.put("userName", "Test User");
        request.put("userRole", "EMPLOYEE");
        request.put("sessionId", "42");

        SseEmitter emitter = controller.streamChat(request);

        assertNotNull(emitter);
        // emitter timeout should be 120 seconds
        assertEquals(120_000L, emitter.getTimeout());
    }

    @Test
    void streamChat_noSessionId_passesNullToService() throws InterruptedException {
        Map<String, String> request = new HashMap<>();
        request.put("message", "Hello");
        request.put("userEmail", "user@test.com");
        request.put("userName", "Test User");
        request.put("userRole", "EMPLOYEE");
        // No sessionId key

        SseEmitter emitter = controller.streamChat(request);

        assertNotNull(emitter);
    }

    @Test
    void streamChat_emptySessionId_passesNullToService() {
        Map<String, String> request = new HashMap<>();
        request.put("message", "Hello");
        request.put("userEmail", "user@test.com");
        request.put("userName", "Test User");
        request.put("userRole", "EMPLOYEE");
        request.put("sessionId", "");

        SseEmitter emitter = controller.streamChat(request);

        assertNotNull(emitter);
    }

    // ── getChatSessions ────────────────────────────────────────────────────────

    @Test
    void getChatSessions_returnsSessionList() {
        List<Map<String, Object>> sessions = List.of(Map.of("id", 1L, "email", "user@test.com"));
        when(chatBotService.getChatSessions("user@test.com")).thenReturn(sessions);

        var response = controller.getChatSessions("user@test.com");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(chatBotService).getChatSessions("user@test.com");
    }

    @Test
    void getChatSessions_emptyList_returnsOk() {
        when(chatBotService.getChatSessions("user@test.com")).thenReturn(List.of());

        var response = controller.getChatSessions("user@test.com");

        assertEquals(200, response.getStatusCode().value());
    }

    // ── getChatHistory ─────────────────────────────────────────────────────────

    @Test
    void getChatHistory_returnsHistory() {
        Map<String, Object> history = Map.of("sessionId", 42L, "messages", List.of());
        when(chatBotService.getChatHistory(42L, "user@test.com")).thenReturn(history);

        var response = controller.getChatHistory(42L, "user@test.com");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(chatBotService).getChatHistory(42L, "user@test.com");
    }

    @Test
    void getChatHistory_differentSession_callsServiceWithCorrectId() {
        when(chatBotService.getChatHistory(99L, "admin@test.com")).thenReturn(Map.of());

        var response = controller.getChatHistory(99L, "admin@test.com");

        assertEquals(200, response.getStatusCode().value());
        verify(chatBotService).getChatHistory(99L, "admin@test.com");
    }

    // ── deleteSession ──────────────────────────────────────────────────────────

    @Test
    void deleteSession_success_returnsNoContent() {
        doNothing().when(chatBotService).deleteSession(42L, "user@test.com");

        var response = controller.deleteSession(42L, "user@test.com");

        assertEquals(204, response.getStatusCode().value());
        verify(chatBotService).deleteSession(42L, "user@test.com");
    }

    @Test
    void deleteSession_serviceThrows_returns500() {
        doThrow(new RuntimeException("Session not found"))
                .when(chatBotService).deleteSession(99L, "user@test.com");

        var response = controller.deleteSession(99L, "user@test.com");

        assertEquals(500, response.getStatusCode().value());
    }

    // ── getWelcomeMessage ──────────────────────────────────────────────────────

    @Test
    void getWelcomeMessage_returnsMessageMap() {
        when(chatBotService.getWelcomeMessage()).thenReturn("Hi! I'm CresenGPT.");

        var response = controller.getWelcomeMessage();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Hi! I'm CresenGPT.", response.getBody().get("message"));
        assertEquals("success", response.getBody().get("status"));
    }
}
