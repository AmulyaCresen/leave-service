package com.leave_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_service.dto.TaskRequest;
import com.leave_service.model.Task;
import com.leave_service.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "todoTaskService")
    private TaskService taskService;

    private Task testTask;
    private TaskRequest taskRequest;

    @BeforeEach
    void setUp() {
        testTask = new Task();
        testTask.setId(1L);
        testTask.setEmailId("employee@test.com");
        testTask.setTitle("Fix Bug #123");
        testTask.setDescription("Fix the NPE in login flow");
        testTask.setStatus("PENDING");
        testTask.setPriority("HIGH");
        testTask.setManagerEmail("manager@test.com");
        testTask.setCreatedAt(LocalDateTime.now());
        testTask.setUpdatedAt(LocalDateTime.now());

        taskRequest = new TaskRequest();
        taskRequest.setTitle("Fix Bug #123");
        taskRequest.setDescription("Fix the NPE in login flow");
        taskRequest.setStatus("PENDING");
        taskRequest.setPriority("HIGH");
        taskRequest.setManagerEmail("manager@test.com");
        taskRequest.setDueDate("2025-12-31");
    }

    // ── POST /api/tasks ──────────────────────────────────────────────────────

    @Test
    void createTask_success_returnsOkWithTask() throws Exception {
        when(taskService.createTask(eq("employee@test.com"), any(TaskRequest.class))).thenReturn(testTask);

        mockMvc.perform(post("/api/tasks")
                        .header("X-User-Email", "employee@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Task created successfully"))
                .andExpect(jsonPath("$.task.title").value("Fix Bug #123"));
    }

    @Test
    void createTask_serviceThrows_returnsBadRequest() throws Exception {
        when(taskService.createTask(eq("employee@test.com"), any(TaskRequest.class)))
                .thenThrow(new RuntimeException("Title is required"));

        mockMvc.perform(post("/api/tasks")
                        .header("X-User-Email", "employee@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Title is required"));
    }

    // ── GET /api/tasks ──────────────────────────────────────────────────────

    @Test
    void getTasks_adminRole_noStatus_returnsAllTasks() throws Exception {
        when(taskService.getAllTasks()).thenReturn(List.of(testTask));

        mockMvc.perform(get("/api/tasks")
                        .header("X-User-Email", "admin@test.com")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(taskService).getAllTasks();
    }

    @Test
    void getTasks_adminRole_withStatus_returnsFilteredTasks() throws Exception {
        when(taskService.getTasksByStatus("PENDING")).thenReturn(List.of(testTask));

        mockMvc.perform(get("/api/tasks")
                        .header("X-User-Email", "admin@test.com")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(taskService).getTasksByStatus("PENDING");
    }

    @Test
    void getTasks_managerRole_noStatus_returnsManagerOrOwnerTasks() throws Exception {
        when(taskService.getTasksByManagerOrOwner("manager@test.com")).thenReturn(List.of(testTask));

        mockMvc.perform(get("/api/tasks")
                        .header("X-User-Email", "manager@test.com")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(taskService).getTasksByManagerOrOwner("manager@test.com");
    }

    @Test
    void getTasks_managerRole_withStatus_returnsFilteredManagerTasks() throws Exception {
        when(taskService.getTasksByManagerOrOwnerAndStatus("manager@test.com", "DONE"))
                .thenReturn(List.of(testTask));

        mockMvc.perform(get("/api/tasks")
                        .header("X-User-Email", "manager@test.com")
                        .header("X-User-Role", "MANAGER")
                        .param("status", "DONE"))
                .andExpect(status().isOk());

        verify(taskService).getTasksByManagerOrOwnerAndStatus("manager@test.com", "DONE");
    }

    @Test
    void getTasks_employeeRole_noStatus_returnsOwnTasks() throws Exception {
        when(taskService.getTasksByEmail("employee@test.com")).thenReturn(List.of(testTask));

        mockMvc.perform(get("/api/tasks")
                        .header("X-User-Email", "employee@test.com")
                        .header("X-User-Role", "EMPLOYEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emailId").value("employee@test.com"));

        verify(taskService).getTasksByEmail("employee@test.com");
    }

    @Test
    void getTasks_employeeRole_withStatus_returnsFilteredOwnTasks() throws Exception {
        when(taskService.getTasksByEmailAndStatus("employee@test.com", "PENDING"))
                .thenReturn(List.of(testTask));

        mockMvc.perform(get("/api/tasks")
                        .header("X-User-Email", "employee@test.com")
                        .header("X-User-Role", "EMPLOYEE")
                        .param("status", "PENDING"))
                .andExpect(status().isOk());

        verify(taskService).getTasksByEmailAndStatus("employee@test.com", "PENDING");
    }

    // ── PUT /api/tasks/{taskId} ──────────────────────────────────────────────

    @Test
    void updateTask_success_returnsUpdatedTask() throws Exception {
        when(taskService.updateTask(eq(1L), eq("employee@test.com"), any(TaskRequest.class)))
                .thenReturn(testTask);

        mockMvc.perform(put("/api/tasks/1")
                        .header("X-User-Email", "employee@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Task updated successfully"))
                .andExpect(jsonPath("$.task.id").value(1));
    }

    @Test
    void updateTask_unauthorized_returnsBadRequest() throws Exception {
        when(taskService.updateTask(eq(1L), eq("other@test.com"), any(TaskRequest.class)))
                .thenThrow(new RuntimeException("Unauthorized to update this task"));

        mockMvc.perform(put("/api/tasks/1")
                        .header("X-User-Email", "other@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized to update this task"));
    }

    @Test
    void updateTask_notFound_returnsBadRequest() throws Exception {
        when(taskService.updateTask(eq(99L), anyString(), any(TaskRequest.class)))
                .thenThrow(new RuntimeException("Task not found"));

        mockMvc.perform(put("/api/tasks/99")
                        .header("X-User-Email", "employee@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // ── DELETE /api/tasks/{taskId} ───────────────────────────────────────────

    @Test
    void deleteTask_success_returnsOk() throws Exception {
        doNothing().when(taskService).deleteTask(1L, "employee@test.com");

        mockMvc.perform(delete("/api/tasks/1")
                        .header("X-User-Email", "employee@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Task deleted successfully"));
    }

    @Test
    void deleteTask_notFound_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Task not found"))
                .when(taskService).deleteTask(99L, "employee@test.com");

        mockMvc.perform(delete("/api/tasks/99")
                        .header("X-User-Email", "employee@test.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Task not found"));
    }

    @Test
    void deleteTask_unauthorized_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Unauthorized to delete this task"))
                .when(taskService).deleteTask(1L, "other@test.com");

        mockMvc.perform(delete("/api/tasks/1")
                        .header("X-User-Email", "other@test.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
