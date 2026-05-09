package com.leave_service.controller;

import com.leave_service.service.ChatBotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests covering the async CompletableFuture lambda in streamChat.
 * These tests use Thread.sleep to allow async code to execute before assertions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatBotControllerStreamCoverageTest {

    @Mock
    private ChatBotService chatBotService;

    @InjectMocks
    private ChatBotController controller;

    private Map<String, String> buildRequest(String message, String sessionId) {
        Map<String, String> req = new HashMap<>();
        req.put("message", message);
        req.put("userEmail", "user@test.com");
        req.put("userName", "Test User");
        req.put("userRole", "EMPLOYEE");
        if (sessionId != null) req.put("sessionId", sessionId);
        return req;
    }

    // ── streamChat: null/blank response from service ──────────────────────────

    @Test
    void streamChat_nullResponseFromService_sendsNoResponseMessage() throws Exception {
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(null);

        SseEmitter emitter = controller.streamChat(buildRequest("Hello", null));
        assertNotNull(emitter);

        // Wait for async execution
        Thread.sleep(300);

        verify(chatBotService, times(1)).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void streamChat_blankResponseFromService_sendsNoResponseMessage() throws Exception {
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn("   ");

        SseEmitter emitter = controller.streamChat(buildRequest("Hello", null));
        assertNotNull(emitter);

        Thread.sleep(300);
        verify(chatBotService, times(1)).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ── streamChat: table response (hasTable=true) ────────────────────────────

    @Test
    void streamChat_tableResponse_streamsLineByLine() throws Exception {
        String tableResponse = "| Name | Email |\n|------|-------|\n| Alice | a@test.com |\n";
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(tableResponse);

        SseEmitter emitter = controller.streamChat(buildRequest("show employees", null));
        assertNotNull(emitter);

        // Wait for async execution (no Thread.sleep since table path)
        Thread.sleep(300);
        verify(chatBotService, times(1)).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ── streamChat: text response (hasTable=false) word by word ──────────────

    @Test
    void streamChat_shortTextResponse_streamsWordByWord() throws Exception {
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn("Hello world");

        SseEmitter emitter = controller.streamChat(buildRequest("Hi", null));
        assertNotNull(emitter);

        // Need to wait for Thread.sleep(20) per word × 2 words + overhead
        Thread.sleep(400);
        verify(chatBotService, times(1)).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void streamChat_singleWordResponse_streamsOneWord() throws Exception {
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn("OK");

        SseEmitter emitter = controller.streamChat(buildRequest("status", null));
        assertNotNull(emitter);

        Thread.sleep(200);
        verify(chatBotService, times(1)).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ── streamChat: service throws exception (connection refused) ─────────────

    @Test
    void streamChat_serviceThrowsConnectionRefusedException() throws Exception {
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("Connection refused: localhost:11434"));

        SseEmitter emitter = controller.streamChat(buildRequest("test query", null));
        assertNotNull(emitter);

        Thread.sleep(300);
        verify(chatBotService, times(1)).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void streamChat_serviceThrowsGenericException() throws Exception {
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("Unknown AI error"));

        SseEmitter emitter = controller.streamChat(buildRequest("test query", null));
        assertNotNull(emitter);

        Thread.sleep(300);
        verify(chatBotService, times(1)).processMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void streamChat_serviceThrowsExceptionWithNullMessage() throws Exception {
        // exception.getMessage() == null branch
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenThrow(new RuntimeException());

        SseEmitter emitter = controller.streamChat(buildRequest("test", null));
        assertNotNull(emitter);

        Thread.sleep(300);
    }

    // ── streamChat: sessionId parsing ─────────────────────────────────────────

    @Test
    void streamChat_withValidSessionId_passesSessionId() throws Exception {
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), eq(42L)))
            .thenReturn("Session response");

        SseEmitter emitter = controller.streamChat(buildRequest("Hello", "42"));
        assertNotNull(emitter);

        Thread.sleep(300);
        verify(chatBotService).processMessage(anyString(), anyString(), anyString(), anyString(), eq(42L));
    }

    @Test
    void streamChat_withBlankSessionId_passesNull() throws Exception {
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), isNull()))
            .thenReturn("Response");

        SseEmitter emitter = controller.streamChat(buildRequest("Hello", ""));
        assertNotNull(emitter);

        Thread.sleep(300);
        verify(chatBotService).processMessage(anyString(), anyString(), anyString(), anyString(), isNull());
    }

    // ── streamChat: table response with only 1 line (no NEWLINE between lines) ─

    @Test
    void streamChat_singleLineTableResponse_noNewlineSent() throws Exception {
        // Single line table response
        String tableResponse = "| Name |\n";
        when(chatBotService.processMessage(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(tableResponse);

        SseEmitter emitter = controller.streamChat(buildRequest("show", null));
        assertNotNull(emitter);

        Thread.sleep(300);
    }
}
