package com.leave_service.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageTest {

    @Test
    void chatMessage_twoArgConstructor_setsFieldsAndTimestamp() {
        ChatMessage msg = new ChatMessage("Hello", "Hi there");

        assertEquals("Hello", msg.getMessage());
        assertEquals("Hi there", msg.getResponse());
        assertNotNull(msg.getTimestamp());
        assertNull(msg.getUserId());
        assertNull(msg.getSessionId());
    }

    @Test
    void chatMessage_allArgsConstructor_setsAllFields() {
        LocalDateTime ts = LocalDateTime.of(2025, 1, 15, 10, 30);
        ChatMessage msg = new ChatMessage("q", "a", ts, "user1", "sess1");

        assertEquals("q", msg.getMessage());
        assertEquals("a", msg.getResponse());
        assertEquals(ts, msg.getTimestamp());
        assertEquals("user1", msg.getUserId());
        assertEquals("sess1", msg.getSessionId());
    }

    @Test
    void chatMessage_noArgsConstructor_fieldsAreNull() {
        ChatMessage msg = new ChatMessage();
        assertNull(msg.getMessage());
        assertNull(msg.getResponse());
    }
}
