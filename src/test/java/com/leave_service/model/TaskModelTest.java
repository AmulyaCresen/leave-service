package com.leave_service.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TaskModelTest {

    @Test
    void task_gettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();

        Task task = new Task();
        task.setId(1L);
        task.setEmailId("emp@test.com");
        task.setTitle("Fix bug");
        task.setDescription("Fix the login bug");
        task.setStatus("PENDING");
        task.setPriority("HIGH");
        task.setDueDate(now.plusDays(5));
        task.setManagerEmail("mgr@test.com");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        assertEquals(1L, task.getId());
        assertEquals("emp@test.com", task.getEmailId());
        assertEquals("Fix bug", task.getTitle());
        assertEquals("Fix the login bug", task.getDescription());
        assertEquals("PENDING", task.getStatus());
        assertEquals("HIGH", task.getPriority());
        assertEquals(now.plusDays(5), task.getDueDate());
        assertEquals("mgr@test.com", task.getManagerEmail());
        assertEquals(now, task.getCreatedAt());
        assertEquals(now, task.getUpdatedAt());
    }

    @Test
    void task_noArgsConstructor() {
        Task task = new Task();
        assertNull(task.getId());
        assertNull(task.getEmailId());
        assertNull(task.getTitle());
    }

    @Test
    void task_allArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        Task task = new Task(1L, "emp@test.com", "Title", "Desc", "TODO", "LOW",
                now, "mgr@test.com", now, now);

        assertEquals(1L, task.getId());
        assertEquals("emp@test.com", task.getEmailId());
        assertEquals("Title", task.getTitle());
        assertEquals("Desc", task.getDescription());
        assertEquals("TODO", task.getStatus());
        assertEquals("LOW", task.getPriority());
        assertEquals(now, task.getDueDate());
        assertEquals("mgr@test.com", task.getManagerEmail());
    }

    @Test
    void task_equalsAndHashCode() {
        LocalDateTime now = LocalDateTime.now();
        Task task1 = new Task(1L, "emp@test.com", "Title", "Desc", "PENDING", "HIGH",
                now, "mgr@test.com", now, now);
        Task task2 = new Task(1L, "emp@test.com", "Title", "Desc", "PENDING", "HIGH",
                now, "mgr@test.com", now, now);

        assertEquals(task1, task2);
        assertEquals(task1.hashCode(), task2.hashCode());
    }

    @Test
    void task_toStringNotNull() {
        Task task = new Task();
        task.setId(1L);
        task.setTitle("Test");
        assertNotNull(task.toString());
    }

    @Test
    void task_onCreate_setsCreatedAndUpdatedAt() {
        Task task = new Task();
        task.onCreate();
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
    }

    @Test
    void task_onUpdate_setsUpdatedAt() {
        Task task = new Task();
        task.onUpdate();
        assertNotNull(task.getUpdatedAt());
    }
}
