package com.leave_service.controller;

import com.leave_service.service.ChatBotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chatbot")
public class ChatBotController {

    @Autowired
    private ChatBotService chatBotService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, String> request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        String message = request.getOrDefault("message", "").trim();
        String email   = request.get("userEmail");
        String name    = request.get("userName");
        String role    = request.get("userRole");
        String sessionIdStr = request.get("sessionId");
        Long sessionId = (sessionIdStr != null && !sessionIdStr.isBlank()) ? Long.parseLong(sessionIdStr) : null;

        if (message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Message cannot be empty"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String response = chatBotService.processMessage(message, email, name, role, sessionId);
                if (response == null || response.isBlank()) {
                    emitter.send(SseEmitter.event().data("No response generated"));
                } else {
                    // Check if response contains table (has pipe characters and multiple lines)
                    boolean hasTable = response.contains("|") && response.contains("\n");
                    
                    if (hasTable) {
                        // For tables, send line by line but mark newlines with special token
                        String[] lines = response.split("\n");
                        for (int i = 0; i < lines.length; i++) {
                            emitter.send(SseEmitter.event().data(lines[i]));
                            if (i < lines.length - 1) {
                                emitter.send(SseEmitter.event().data("<NEWLINE>"));
                            }
                        }
                    } else {
                        // For regular text, stream word by word
                        String[] words = response.split("\\s+");
                        for (String word : words) {
                            if (!word.isEmpty()) {
                                emitter.send(SseEmitter.event().data(word));
                                Thread.sleep(20);
                            }
                        }
                    }
                }
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (Exception e) {
                try {
                    String errMsg = e.getMessage() != null && e.getMessage().contains("Connection refused")
                        ? "Cannot connect to Ollama. Please ensure Ollama is running on localhost:11434."
                        : "AI service error: " + e.getMessage();
                    emitter.send(SseEmitter.event().name("error").data(errMsg));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }
    
    @GetMapping("/sessions")
    public ResponseEntity<?> getChatSessions(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(chatBotService.getChatSessions(email));
    }
    
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getChatHistory(
            @PathVariable Long sessionId,
            @RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(chatBotService.getChatHistory(sessionId, email));
    }
    
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> deleteSession(
            @PathVariable Long sessionId,
            @RequestHeader("X-User-Email") String email) {
        try {
            chatBotService.deleteSession(sessionId, email);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            System.err.println("[ChatBotController] Delete error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to delete session",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/welcome")
    public ResponseEntity<Map<String, String>> getWelcomeMessage() {
        return ResponseEntity.ok(Map.of(
            "message", chatBotService.getWelcomeMessage(),
            "status", "success"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "CresenGPT"));
    }
}