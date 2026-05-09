package com.leave_service.service;

import com.leave_service.dto.TaskRequest;
import com.leave_service.model.Task;
import com.leave_service.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private Task testTask;
    private TaskRequest testRequest;

    @BeforeEach
    void setUp() {
        testTask = new Task();
        testTask.setId(1L);
        testTask.setEmailId("employee@test.com");
        testTask.setTitle("Test Task");
        testTask.setDescription("Test Description");
        testTask.setStatus("PENDING");
        testTask.setPriority("HIGH");
        testTask.setManagerEmail("manager@test.com");
        testTask.setDueDate(LocalDateTime.now().plusDays(7));
        testTask.setCreatedAt(LocalDateTime.now());

        testRequest = new TaskRequest();
        testRequest.setTitle("New Task");
        testRequest.setDescription("New Description");
        testRequest.setStatus("TODO");
        testRequest.setPriority("MEDIUM");
        testRequest.setManagerEmail("manager@test.com");
        testRequest.setDueDate("2024-12-31");
    }

    @Test
    void testCreateTask_Success() {
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task created = taskService.createTask("employee@test.com", testRequest);

        assertNotNull(created);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testCreateTask_WithoutDueDate() {
        testRequest.setDueDate(null);
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task created = taskService.createTask("employee@test.com", testRequest);

        assertNotNull(created);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testCreateTask_WithEmptyDueDate() {
        testRequest.setDueDate("");
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task created = taskService.createTask("employee@test.com", testRequest);

        assertNotNull(created);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testCreateTask_WithInvalidDueDate() {
        testRequest.setDueDate("invalid-date");
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task created = taskService.createTask("employee@test.com", testRequest);

        assertNotNull(created);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testCreateTask_WithDefaultStatus() {
        testRequest.setStatus(null);
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task created = taskService.createTask("employee@test.com", testRequest);

        assertNotNull(created);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testCreateTask_WithDefaultPriority() {
        testRequest.setPriority(null);
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task created = taskService.createTask("employee@test.com", testRequest);

        assertNotNull(created);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testCreateTask_TitleRequired() {
        testRequest.setTitle(null);

        assertThrows(RuntimeException.class, () -> 
            taskService.createTask("employee@test.com", testRequest));
    }

    @Test
    void testCreateTask_TitleEmpty() {
        testRequest.setTitle("   ");

        assertThrows(RuntimeException.class, () -> 
            taskService.createTask("employee@test.com", testRequest));
    }

    @Test
    void testCreateTask_ManagerEmailRequired() {
        testRequest.setManagerEmail(null);

        assertThrows(RuntimeException.class, () -> 
            taskService.createTask("employee@test.com", testRequest));
    }

    @Test
    void testCreateTask_ManagerEmailEmpty() {
        testRequest.setManagerEmail("   ");

        assertThrows(RuntimeException.class, () -> 
            taskService.createTask("employee@test.com", testRequest));
    }

    @Test
    void testGetTasksByEmail() {
        when(taskRepository.findByEmailIdOrderByCreatedAtDesc("employee@test.com"))
            .thenReturn(List.of(testTask));

        List<Task> tasks = taskService.getTasksByEmail("employee@test.com");

        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals("employee@test.com", tasks.get(0).getEmailId());
        verify(taskRepository).findByEmailIdOrderByCreatedAtDesc("employee@test.com");
    }

    @Test
    void testGetTasksByEmailAndStatus() {
        when(taskRepository.findByEmailIdAndStatusOrderByCreatedAtDesc("employee@test.com", "PENDING"))
            .thenReturn(List.of(testTask));

        List<Task> tasks = taskService.getTasksByEmailAndStatus("employee@test.com", "PENDING");

        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals("PENDING", tasks.get(0).getStatus());
        verify(taskRepository).findByEmailIdAndStatusOrderByCreatedAtDesc("employee@test.com", "PENDING");
    }

    @Test
    void testGetAllTasks() {
        when(taskRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(testTask));

        List<Task> tasks = taskService.getAllTasks();

        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        verify(taskRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void testGetTasksByStatus() {
        when(taskRepository.findByStatusOrderByCreatedAtDesc("PENDING"))
            .thenReturn(List.of(testTask));

        List<Task> tasks = taskService.getTasksByStatus("PENDING");

        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals("PENDING", tasks.get(0).getStatus());
        verify(taskRepository).findByStatusOrderByCreatedAtDesc("PENDING");
    }

    @Test
    void testGetTasksByManagerOrOwner() {
        when(taskRepository.findByManagerOrOwner("manager@test.com"))
            .thenReturn(List.of(testTask));

        List<Task> tasks = taskService.getTasksByManagerOrOwner("manager@test.com");

        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        verify(taskRepository).findByManagerOrOwner("manager@test.com");
    }

    @Test
    void testGetTasksByManagerOrOwnerAndStatus() {
        when(taskRepository.findByManagerOrOwnerAndStatus("manager@test.com", "PENDING"))
            .thenReturn(List.of(testTask));

        List<Task> tasks = taskService.getTasksByManagerOrOwnerAndStatus("manager@test.com", "PENDING");

        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        verify(taskRepository).findByManagerOrOwnerAndStatus("manager@test.com", "PENDING");
    }

    @Test
    void testUpdateTask_Success() {
        TaskRequest updateRequest = new TaskRequest();
        updateRequest.setTitle("Updated Task");
        updateRequest.setDescription("Updated Description");
        updateRequest.setStatus("COMPLETED");
        updateRequest.setPriority("LOW");
        updateRequest.setManagerEmail("newmanager@test.com");
        updateRequest.setDueDate("2024-12-31");

        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task updated = taskService.updateTask(1L, "employee@test.com", updateRequest);

        assertNotNull(updated);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testUpdateTask_WithDateTimeFormat() {
        TaskRequest updateRequest = new TaskRequest();
        updateRequest.setTitle("Updated Task");
        updateRequest.setDueDate("2024-12-31T10:30:00");

        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task updated = taskService.updateTask(1L, "employee@test.com", updateRequest);

        assertNotNull(updated);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testUpdateTask_PartialUpdate() {
        TaskRequest updateRequest = new TaskRequest();
        updateRequest.setStatus("IN_PROGRESS");

        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        Task updated = taskService.updateTask(1L, "employee@test.com", updateRequest);

        assertNotNull(updated);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void testUpdateTask_NotFound() {
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> 
            taskService.updateTask(1L, "employee@test.com", testRequest));
    }

    @Test
    void testUpdateTask_Unauthorized() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        assertThrows(RuntimeException.class, () -> 
            taskService.updateTask(1L, "other@test.com", testRequest));
    }

    @Test
    void testDeleteTask_Success() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        assertDoesNotThrow(() -> taskService.deleteTask(1L, "employee@test.com"));

        verify(taskRepository).delete(testTask);
    }

    @Test
    void testDeleteTask_NotFound() {
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> 
            taskService.deleteTask(1L, "employee@test.com"));
    }

    @Test
    void testDeleteTask_Unauthorized() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        assertThrows(RuntimeException.class, () -> 
            taskService.deleteTask(1L, "other@test.com"));
    }
}
