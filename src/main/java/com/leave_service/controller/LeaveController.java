package com.leave_service.controller;
import com.leave_service.dto.CreateLeaveRequest;
import com.leave_service.dto.CreateLeaveTypeRequest;
import com.leave_service.dto.HolidayRequest;
import com.leave_service.dto.UpdateLeaveRequest;
import com.leave_service.model.Holiday;
import com.leave_service.model.Leave;
import com.leave_service.model.LeaveType;
import com.leave_service.service.LeaveProcessService;
import com.leave_service.service.LeaveService;
import com.leave_service.commons.LeaveConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/leave")
@RequiredArgsConstructor
public class LeaveController {
    private final LeaveService leaveService;
    private final LeaveProcessService leaveProcessService;
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
}