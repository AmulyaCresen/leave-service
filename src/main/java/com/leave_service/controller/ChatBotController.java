package com.leave_service.controller;

import com.leave_service.service.ChatBotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chatbot")
public class ChatBotController {

    @Autowired
    private ChatBotService chatBotService;
    
    private final Executor chatExecutor = Executors.newFixedThreadPool(10);

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
                    boolean hasTable = response.contains("|") && response.contains("\n");
                    
                    if (hasTable) {
                        String[] lines = response.split("\n");
                        for (int i = 0; i < lines.length; i++) {
                            emitter.send(SseEmitter.event().data(lines[i]));
                            if (i < lines.length - 1) {
                                emitter.send(SseEmitter.event().data("<NEWLINE>"));
                            }
                        }
                    } else {
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
        }, chatExecutor);

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
    
    @PostMapping("/quick-response")
    public ResponseEntity<?> getQuickResponse(@RequestBody Map<String, String> request) {
        String action = request.get("action");
        String email = request.get("userEmail");
        String name = request.get("userName");
        String role = request.get("userRole");
        
        String response = chatBotService.getQuickResponseDirect(action, email, name, role);
        return ResponseEntity.ok(Map.of("response", response, "status", "success"));
    }
    
    @PostMapping("/save-quick-history")
    public ResponseEntity<?> saveQuickHistory(@RequestBody Map<String, Object> request) {
        String userMessage = (String) request.get("userMessage");
        String botResponse = (String) request.get("botResponse");
        String email = (String) request.get("email");
        Long sessionId = request.get("sessionId") != null ? 
            Long.parseLong(request.get("sessionId").toString()) : null;
        Long latency = request.get("latency") != null ? 
            Long.parseLong(request.get("latency").toString()) : 0L;
        
        Long newSessionId = chatBotService.saveQuickChatHistory(userMessage, botResponse, email, sessionId, latency);
        return ResponseEntity.ok(Map.of("status", "success", "sessionId", newSessionId));
    }
    
    @DeleteMapping("/sessions/all")
    public ResponseEntity<?> deleteAllSessions(@RequestHeader("X-User-Email") String email) {
        try {
            chatBotService.deleteAllSessions(email);
            return ResponseEntity.ok(Map.of("status", "success", "message", "All chat sessions deleted"));
        } catch (Exception e) {
            System.err.println("[ChatBotController] Delete all sessions error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to delete all sessions",
                "message", e.getMessage()
            ));
        }
    }
    
    @DeleteMapping("/admin/clear-all-history")
    public ResponseEntity<?> clearAllChatHistory() {
        try {
            chatBotService.clearAllChatHistory();
            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "All chat history cleared for all employees"
            ));
        } catch (Exception e) {
            System.err.println("[ChatBotController] Clear all history error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to clear all chat history",
                "message", e.getMessage()
            ));
        }
    }
}