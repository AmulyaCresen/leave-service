package com.leave_service.controller;
import com.leave_service.dto.CreateLeaveRequest;
import com.leave_service.dto.CreateLeaveTypeRequest;
import com.leave_service.dto.HolidayRequest;
import com.leave_service.dto.UpdateLeaveRequest;
import com.leave_service.model.Holiday;
import com.leave_service.model.Leave;
import com.leave_service.model.LeaveType;
import com.leave_service.model.LeaveFile;
import com.leave_service.repository.LeaveFileRepository;
import com.leave_service.service.LeaveProcessService;
import com.leave_service.service.LeaveService;
import com.leave_service.commons.LeaveConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/leave")
@RequiredArgsConstructor
public class LeaveController {
    private final LeaveService leaveService;
    private final LeaveProcessService leaveProcessService;
    private final LeaveFileRepository leaveFileRepository;

    @Value("${app.upload.dir:uploads/leave-documents}")
    private String uploadDir;
    @GetMapping("/holidays")
    public ResponseEntity<List<Holiday>> getHolidays() {
        return ResponseEntity.ok(leaveService.getAllHolidays());
    }
    @PostMapping("/holidays")
    public ResponseEntity<Holiday> createHoliday(@RequestBody HolidayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.createHoliday(request));
    }
    @PutMapping("/holidays/{id}")
    public ResponseEntity<Holiday> updateHoliday(@PathVariable Long id, @RequestBody HolidayRequest request) {
        return ResponseEntity.ok(leaveService.updateHoliday(id, request));
    }
    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        leaveService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/types")
    public ResponseEntity<List<LeaveType>> getLeaveTypes() {
        return ResponseEntity.ok(leaveService.getAllLeaveTypes());
    }
    @GetMapping("/types/check-name")
    public ResponseEntity<Boolean> checkLeaveName(@RequestParam String name) {
        return ResponseEntity.ok(leaveService.leaveNameExists(name));
    }
    @GetMapping("/types/check-unique-name")
    public ResponseEntity<Boolean> checkLeaveUniqueName(@RequestParam String uniqueName) {
        return ResponseEntity.ok(leaveService.leaveUniqueNameExists(uniqueName));
    }
    @PostMapping("/types/create")
    public ResponseEntity<LeaveType> createLeaveType(@RequestBody CreateLeaveTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.createLeaveType(request));
    }
    @PutMapping("/types/{id}")
    public ResponseEntity<LeaveType> updateLeaveType(@PathVariable Integer id, @RequestBody CreateLeaveTypeRequest request) {
        return ResponseEntity.ok(leaveService.updateLeaveType(id, request));
    }
    @DeleteMapping("/types/{id}")
    public ResponseEntity<Void> deleteLeaveType(@PathVariable Integer id) {
        leaveService.deleteLeaveType(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}")
    public ResponseEntity<Leave> getLeaveById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.getLeaveById(id));
    }
    @GetMapping("/all")
    public ResponseEntity<List<Leave>> getAllLeaves() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }
    @GetMapping("/pending-for")
    public ResponseEntity<List<Leave>> getPendingLeavesFor(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(leaveService.getPendingLeavesFor(email));
    }
    @PostMapping("/{id}/partial-review")
    public ResponseEntity<Void> partialReview(
            @PathVariable Long id,
            @RequestBody java.util.List<java.util.Map<String, String>> dayDecisions,
            @RequestHeader("X-User-Email") String reviewedBy) {
        leaveProcessService.partialReview(id, reviewedBy, dayDecisions);
        return ResponseEntity.ok().build();
    }
    @PutMapping("/{id}")
    public ResponseEntity<Leave> updateLeave(
            @PathVariable Long id,
            @RequestBody UpdateLeaveRequest request,
            @RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(leaveService.updateLeave(id, request, email));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLeave(
            @PathVariable Long id,
            @RequestHeader("X-User-Email") String email) {
        leaveService.deleteLeave(id, email);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/my")
    public ResponseEntity<List<Leave>> getMyLeaves(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(leaveService.getLeavesByEmail(email));
    }

    @GetMapping("/reviewed-by-me")
    public ResponseEntity<List<Leave>> getReviewedLeaves(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(leaveService.getReviewedLeavesByReviewer(email));
    }

    @GetMapping("/manager-logged-leaves")
    public ResponseEntity<java.util.Map<String, Object>> getManagerLoggedLeaves(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(leaveService.getManagerLoggedLeaves(email));
    }

    @GetMapping("/admin-logged-leaves")
    public ResponseEntity<java.util.Map<String, Object>> getAdminLoggedLeaves(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(leaveService.getAdminLoggedLeaves(email));
    }
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveLeave(
            @PathVariable Long id,
            @RequestHeader("X-User-Email") String reviewedBy) {
        leaveProcessService.approveLeave(id, reviewedBy);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectLeave(
            @PathVariable Long id,
            @RequestParam(defaultValue = LeaveConstants.NO_REASON_PROVIDED) String reason,
            @RequestHeader("X-User-Email") String reviewedBy) {
        leaveProcessService.rejectLeave(id, reviewedBy, reason);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/create")
    public ResponseEntity<Leave> createLeave(
            @RequestBody CreateLeaveRequest request,
            @RequestHeader("X-User-Email") String createdBy) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.createLeave(request, createdBy));
    }

    @PostMapping("/{id}/upload-document")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Email") String email) {
        try {
            Leave leave = leaveService.getLeaveById(id);
            if (!email.equals(leave.getEmailId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
            String safeName = id + "_" + System.currentTimeMillis() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            Path dest = dir.resolve(safeName);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            
            LeaveFile leaveFile = new LeaveFile();
            leaveFile.setLeaveId(id);
            leaveFile.setFileName(originalName);
            leaveFile.setFilePath(safeName);
            leaveFile.setFileSize(file.getSize());
            leaveFile.setUploadedAt(LocalDateTime.now());
            leaveFile.setUploadedBy(email);
            leaveFileRepository.save(leaveFile);
            
            return ResponseEntity.ok(Map.of(
                "id", leaveFile.getId(),
                "fileName", originalName,
                "fileSize", file.getSize()
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Upload failed"));
        }
    }

    @GetMapping("/{id}/files")
    public ResponseEntity<List<LeaveFile>> getLeaveFiles(@PathVariable Long id) {
        return ResponseEntity.ok(leaveFileRepository.findByLeaveId(id));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Email") String email) {
        try {
            LeaveFile leaveFile = leaveFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
            Leave leave = leaveService.getLeaveById(leaveFile.getLeaveId());
            if (!email.equals(leave.getEmailId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            Path filePath = Paths.get(uploadDir).resolve(leaveFile.getFilePath());
            Files.deleteIfExists(filePath);
            leaveFileRepository.deleteById(fileId);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        try {
            LeaveFile leaveFile = leaveFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
            Path filePath = Paths.get(uploadDir).resolve(leaveFile.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + leaveFile.getFileName() + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
