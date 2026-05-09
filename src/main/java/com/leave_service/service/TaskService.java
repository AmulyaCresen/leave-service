package com.leave_service.service;

import com.leave_service.dto.TaskRequest;
import com.leave_service.model.Task;
import com.leave_service.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("todoTaskService")
@RequiredArgsConstructor
public class TaskService {
    
    private final TaskRepository taskRepository;
    
    @Transactional
    public Task createTask(String emailId, TaskRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Title is required");
        }
        if (request.getManagerEmail() == null || request.getManagerEmail().trim().isEmpty()) {
            throw new RuntimeException("Manager email is required");
        }
        
        Task task = new Task();
        task.setEmailId(emailId);
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription() != null ? request.getDescription().trim() : "");
        task.setStatus(request.getStatus() != null && !request.getStatus().isEmpty() ? request.getStatus() : "PENDING");
        task.setPriority(request.getPriority() != null && !request.getPriority().isEmpty() ? request.getPriority() : "MEDIUM");
        task.setManagerEmail(request.getManagerEmail().trim());
        
        if (request.getDueDate() != null && !request.getDueDate().trim().isEmpty()) {
            try {
                task.setDueDate(java.time.LocalDateTime.parse(request.getDueDate() + "T00:00:00"));
            } catch (Exception e) {
                
            }
        }
        
        return taskRepository.save(task);
    }
    
    public List<Task> getTasksByEmail(String emailId) {
        return taskRepository.findByEmailIdOrderByCreatedAtDesc(emailId);
    }
    
    public List<Task> getTasksByEmailAndStatus(String emailId, String status) {
        return taskRepository.findByEmailIdAndStatusOrderByCreatedAtDesc(emailId, status);
    }
    
    public List<Task> getAllTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }
    
    public List<Task> getTasksByStatus(String status) {
        return taskRepository.findByStatusOrderByCreatedAtDesc(status);
    }
    
    public List<Task> getTasksByManagerOrOwner(String email) {
        return taskRepository.findByManagerOrOwner(email);
    }
    
    public List<Task> getTasksByManagerOrOwnerAndStatus(String email, String status) {
        return taskRepository.findByManagerOrOwnerAndStatus(email, status);
    }
    
    @Transactional
    public Task updateTask(Long taskId, String emailId, TaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        
        if (!task.getEmailId().equals(emailId)) {
            throw new RuntimeException("Unauthorized to update this task");
        }
        
        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getManagerEmail() != null) task.setManagerEmail(request.getManagerEmail());
        
        if (request.getDueDate() != null && !request.getDueDate().isEmpty()) {
            String raw = request.getDueDate();
            if (raw.contains("T")) {
                task.setDueDate(java.time.LocalDateTime.parse(raw));
            } else {
                task.setDueDate(java.time.LocalDateTime.parse(raw + "T00:00:00"));
            }
        }
        
        return taskRepository.save(task);
    }
    
    @Transactional
    public void deleteTask(Long taskId, String emailId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        
        if (!task.getEmailId().equals(emailId)) {
            throw new RuntimeException("Unauthorized to delete this task");
        }
        
        taskRepository.delete(task);
    }
}
