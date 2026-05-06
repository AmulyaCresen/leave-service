package com.leave_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String message;
    private String response;
    private LocalDateTime timestamp;
    private String userId;
    private String sessionId;
    
    public ChatMessage(String message, String response) {
        this.message = message;
        this.response = response;
        this.timestamp = LocalDateTime.now();
    }
}