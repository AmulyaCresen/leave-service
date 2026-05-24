package com.leave_service.controller;

import com.leave_service.dto.TaskRequest;
import com.leave_service.model.Task;
import com.leave_service.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    
    private final TaskService taskService;
    
    public TaskController(@Qualifier("todoTaskService") TaskService taskService) {
        this.taskService = taskService;
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestHeader("X-User-Email") String emailId,
            @RequestBody TaskRequest request) {
        try {
            Task task = taskService.createTask(emailId, request);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Task created successfully");
            response.put("task", task);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping
    public ResponseEntity<List<Task>> getTasks(
            @RequestHeader("X-User-Email") String emailId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String status) {
        if ("ADMIN".equals(role)) {
            
            if (status != null && !status.isEmpty()) {
                return ResponseEntity.ok(taskService.getTasksByStatus(status));
            }
            return ResponseEntity.ok(taskService.getAllTasks());
        } else if ("MANAGER".equals(role)) {
            
            if (status != null && !status.isEmpty()) {
                return ResponseEntity.ok(taskService.getTasksByManagerOrOwnerAndStatus(emailId, status));
            }
            return ResponseEntity.ok(taskService.getTasksByManagerOrOwner(emailId));
        } else {
            
            if (status != null && !status.isEmpty()) {
                return ResponseEntity.ok(taskService.getTasksByEmailAndStatus(emailId, status));
            }
            return ResponseEntity.ok(taskService.getTasksByEmail(emailId));
        }
    }
    
    @PutMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> updateTask(
            @PathVariable Long taskId,
            @RequestHeader("X-User-Email") String emailId,
            @RequestBody TaskRequest request) {
        try {
            Task task = taskService.updateTask(taskId, emailId, request);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Task updated successfully");
            response.put("task", task);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> deleteTask(
            @PathVariable Long taskId,
            @RequestHeader("X-User-Email") String emailId) {
        try {
            taskService.deleteTask(taskId, emailId);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Task deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
