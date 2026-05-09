package com.leave_service.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatSessionTest {

    @Test
    void testGettersAndSetters() {
        ChatSession session = new ChatSession();

        session.setId(1L);
        session.setEmailId("user@test.com");

        OffsetDateTime now = OffsetDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);

        List<Map<String, Object>> sessions = new ArrayList<>();
        sessions.add(Map.of("role", "user", "content", "Hello"));
        sessions.add(Map.of("role", "assistant", "content", "Hi there!"));
        session.setSessions(sessions);

        assertEquals(1L, session.getId());
        assertEquals("user@test.com", session.getEmailId());
        assertEquals(now, session.getCreatedAt());
        assertEquals(now, session.getUpdatedAt());
        assertNotNull(session.getSessions());
        assertEquals(2, session.getSessions().size());
        assertEquals("user", session.getSessions().get(0).get("role"));
    }

    @Test
    void testNoArgsConstructor_sessionsDefaultsToEmptyList() {
        ChatSession session = new ChatSession();

        assertNotNull(session.getSessions());
        assertTrue(session.getSessions().isEmpty());
    }

    @Test
    void testOnCreate_setsTimestamps() throws Exception {
        ChatSession session = new ChatSession();
        session.setEmailId("user@test.com");

        // Invoke @PrePersist via reflection
        java.lang.reflect.Method onCreateMethod = ChatSession.class.getDeclaredMethod("onCreate");
        onCreateMethod.setAccessible(true);
        onCreateMethod.invoke(session);

        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getUpdatedAt());
    }

    @Test
    void testOnUpdate_updatesTimestamp() throws Exception {
        ChatSession session = new ChatSession();

        // Call onCreate first
        java.lang.reflect.Method onCreateMethod = ChatSession.class.getDeclaredMethod("onCreate");
        onCreateMethod.setAccessible(true);
        onCreateMethod.invoke(session);

        OffsetDateTime createdAt = session.getCreatedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(10);

        // Invoke @PreUpdate via reflection
        java.lang.reflect.Method onUpdateMethod = ChatSession.class.getDeclaredMethod("onUpdate");
        onUpdateMethod.setAccessible(true);
        onUpdateMethod.invoke(session);

        assertNotNull(session.getUpdatedAt());
        // updatedAt should be >= createdAt
        assertFalse(session.getUpdatedAt().isBefore(createdAt));
    }

    @Test
    void testSetSessions_replacesExistingList() {
        ChatSession session = new ChatSession();

        List<Map<String, Object>> first = new ArrayList<>();
        first.add(Map.of("id", "1"));
        session.setSessions(first);

        List<Map<String, Object>> second = new ArrayList<>();
        second.add(Map.of("id", "2"));
        second.add(Map.of("id", "3"));
        session.setSessions(second);

        assertEquals(2, session.getSessions().size());
        assertEquals("2", session.getSessions().get(0).get("id"));
    }

    @Test
    void testEmailId_nullableByDefault() {
        ChatSession session = new ChatSession();
        assertNull(session.getEmailId());
    }

    @Test
    void testId_nullByDefault() {
        ChatSession session = new ChatSession();
        assertNull(session.getId());
    }
}
