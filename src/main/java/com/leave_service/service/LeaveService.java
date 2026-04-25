package com.leave_service.service;
import com.leave_service.commons.LeaveConstants;
import com.leave_service.dto.CreateLeaveRequest;
import com.leave_service.dto.CreateLeaveTypeRequest;
import com.leave_service.dto.HolidayRequest;
import com.leave_service.dto.LeaveDayEntry;
import com.leave_service.dto.UpdateLeaveRequest;
import com.leave_service.model.Holiday;
import com.leave_service.model.Leave;
import com.leave_service.model.LeaveType;
import com.leave_service.repository.EmployeeLeaveRepository;
import com.leave_service.repository.HolidayRepository;
import com.leave_service.repository.LeaveRepository;
import com.leave_service.repository.LeaveTypeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class LeaveService {
    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);
    @PersistenceContext
    private EntityManager entityManager;
    private final LeaveRepository leaveRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveProcessService leaveProcessService;
    @Value("${app.mail.manager}")
    private String managerEmail;
    @Value("${app.mail.admin}")
    private String adminEmail;
    private void hydrateTransients(Leave leave) {
        List<Map<String, String>> trail = leave.getTrail();
        if (trail != null && !trail.isEmpty()) {
            Map<String, String> latest = trail.get(trail.size() - 1);
            
            if (leave.getDays() != null && !leave.getDays().isEmpty()) {
                List<LeaveDayEntry> days = leave.getDays();
                long approvedCount = days.stream().filter(d -> LeaveConstants.STATUS_APPROVED.equals(d.getStatus())).count();
                long rejectedCount = days.stream().filter(d -> LeaveConstants.STATUS_REJECTED.equals(d.getStatus())).count();
                long pendingCount = days.stream().filter(d -> d.getStatus() == null || LeaveConstants.STATUS_PENDING.equals(d.getStatus())).count();

                boolean hasAdminApproval = trail.stream().anyMatch(entry ->
                    LeaveConstants.STAGE_ADMIN.equals(entry.get(LeaveConstants.TRAIL_STAGE)) &&
                    (LeaveConstants.STATUS_APPROVED.equals(entry.get(LeaveConstants.TRAIL_STATUS)) ||
                     LeaveConstants.STATUS_PARTIAL.equals(entry.get(LeaveConstants.TRAIL_STATUS)))
                );
                boolean hasManagerApproval = trail.stream().anyMatch(entry ->
                    LeaveConstants.STATUS_MANAGER_APPROVED.equals(entry.get(LeaveConstants.TRAIL_STATUS)) ||
                    (LeaveConstants.STATUS_APPROVED.equals(entry.get(LeaveConstants.TRAIL_STATUS)) &&
                     LeaveConstants.STAGE_MANAGER.equals(entry.get(LeaveConstants.TRAIL_STAGE)))
                );

                if (hasAdminApproval) {
                    if (approvedCount > 0 && rejectedCount > 0) {
                        leave.setStatus(LeaveConstants.STATUS_PARTIAL);
                    } else if (rejectedCount == days.size()) {
                        leave.setStatus(LeaveConstants.STATUS_REJECTED);
                    } else {
                        leave.setStatus(LeaveConstants.STATUS_APPROVED);
                    }
                } else if (hasManagerApproval) {
                    leave.setStatus(LeaveConstants.STATUS_MANAGER_APPROVED);
                } else if (rejectedCount == days.size()) {
                    leave.setStatus(LeaveConstants.STATUS_REJECTED);
                } else if (pendingCount > 0) {
                    leave.setStatus(LeaveConstants.STATUS_PENDING);
                } else {
                    leave.setStatus(latest.getOrDefault(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING));
                }
            } else {
                leave.setStatus(latest.getOrDefault(LeaveConstants.TRAIL_STATUS, LeaveConstants.STATUS_PENDING));
            }
            
            leave.setDayType(latest.getOrDefault(LeaveConstants.TRAIL_DAY_TYPE, LeaveConstants.DAY_TYPE_FULL));
            leave.setHalfDaySession(latest.get(LeaveConstants.TRAIL_HALF_DAY_SESSION));
            leave.setReviewedBy(latest.get(LeaveConstants.TRAIL_REVIEWED_BY));
        } else {
            leave.setStatus(LeaveConstants.STATUS_PENDING);
            leave.setDayType(LeaveConstants.DAY_TYPE_FULL);
        }
        if (leave.getDays() != null && !leave.getDays().isEmpty()) {
            double total = leave.getDays().stream()
                .mapToDouble(d -> LeaveConstants.DAY_TYPE_HALF.equals(d.getDayType()) ? 0.5 : 1.0)
                .sum();
            leave.setTotalDays(total);
        }
    }
    private List<Leave> hydrateAll(List<Leave> leaves) {
        leaves.forEach(this::hydrateTransients);
        return leaves;
    }
    private void appendTrail(Leave leave, String status, String dayType, String halfDaySession) {
        List<Map<String, String>> trail = leave.getTrail() != null ? leave.getTrail() : new ArrayList<>();
        Map<String, String> entry = new HashMap<>();
        entry.put(LeaveConstants.TRAIL_STATUS,   status);
        entry.put(LeaveConstants.TRAIL_DAY_TYPE, dayType != null ? dayType : LeaveConstants.DAY_TYPE_FULL);
        entry.put(LeaveConstants.TRAIL_DATE,     java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (halfDaySession != null) entry.put(LeaveConstants.TRAIL_HALF_DAY_SESSION, halfDaySession);
        
        if (LeaveConstants.STATUS_PENDING.equals(status) && (trail.isEmpty() || 
            trail.stream().noneMatch(e -> LeaveConstants.TYPE_LEAVE_APPLIED.equals(e.get("type"))))) {
            entry.put("type", LeaveConstants.TYPE_LEAVE_APPLIED);
        }
        
        trail.add(entry);
        leave.setTrail(trail);
    }
    public List<Holiday> getAllHolidays() {
        return holidayRepository.findAll();
    }
    public Holiday createHoliday(HolidayRequest request) {
        LocalDate date = LocalDate.parse(request.getDate());
        if (holidayRepository.findByDate(date).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Holiday already exists on this date");
        Holiday h = new Holiday();
        h.setName(request.getName());
        h.setDate(date);
        return holidayRepository.save(h);
    }
    public Holiday updateHoliday(Long id, HolidayRequest request) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday not found"));
        LocalDate date = LocalDate.parse(request.getDate());
        holidayRepository.findByDate(date)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(e -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Holiday already exists on this date"); });
        h.setName(request.getName());
        h.setDate(date);
        return holidayRepository.save(h);
    }
    @Transactional
    public void deleteHoliday(Long id) {
        if (!holidayRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday not found");
        holidayRepository.deleteById(id);
        holidayRepository.flush();
        entityManager.createNativeQuery(
            "SELECT setval('leave.holidays_id_seq', COALESCE((SELECT MAX(id) FROM leave.holidays), 0) + 1, false)"
        ).getSingleResult();
    }
    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }
    public boolean leaveNameExists(String leaveName) {
        return leaveTypeRepository.findByLeaveName(leaveName).isPresent();
    }
    public boolean leaveUniqueNameExists(String uniqueName) {
        return leaveTypeRepository.findByLeaveUniqueName(uniqueName).isPresent();
    }
    public LeaveType createLeaveType(CreateLeaveTypeRequest request) {
        if (leaveTypeRepository.findByLeaveName(request.getLeaveName()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Leave name already exists");
        if (leaveTypeRepository.findByLeaveUniqueName(request.getLeaveUniqueName()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Leave unique name already exists");
        LeaveType lt = new LeaveType();
        lt.setLeaveName(request.getLeaveName());
        lt.setLeaveUniqueName(request.getLeaveUniqueName());
        lt.setDescription(request.getDescription());
        lt.setMaxDays(request.getMaxDays());
        lt.setCreatedAt(java.time.OffsetDateTime.now());
        LeaveType saved = leaveTypeRepository.save(lt);
        log.info("[LEAVE] Created leave type: {}", saved.getLeaveName());
        return saved;
    }
    public LeaveType updateLeaveType(Integer id, CreateLeaveTypeRequest request) {
        LeaveType lt = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave type not found"));
        leaveTypeRepository.findByLeaveName(request.getLeaveName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(e -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Leave name already exists"); });
        leaveTypeRepository.findByLeaveUniqueName(request.getLeaveUniqueName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(e -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Leave unique name already exists"); });
        lt.setLeaveName(request.getLeaveName());
        lt.setLeaveUniqueName(request.getLeaveUniqueName());
        lt.setDescription(request.getDescription());
        lt.setMaxDays(request.getMaxDays());
        lt.setUpdatedAt(java.time.OffsetDateTime.now());
        LeaveType saved = leaveTypeRepository.save(lt);
        log.info("[LEAVE] Updated leave type id={}", id);
        return saved;
    }
    public void deleteLeaveType(Integer id) {
        if (!leaveTypeRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave type not found");
        leaveTypeRepository.deleteById(id);
        log.info("[LEAVE] Deleted leave type id={}", id);
    }
    public Leave getLeaveById(Long id) {
        Leave leave = leaveRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave not found"));
        hydrateTransients(leave);
        return leave;
    }
    public List<Leave> getAllLeaves() {
        return hydrateAll(leaveRepository.findAll());
    }
    public List<Leave> getPendingLeavesFor(String reviewerEmail) {
        List<Long> managerTaskLeaveIds = getLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_MANAGER);
        List<Long> adminTaskLeaveIds   = getLeaveIdsForTask(LeaveConstants.TASK_DEF_KEY_ADMIN);
        return hydrateAll(leaveRepository.findAll()).stream()
            .filter(l -> !reviewerEmail.equals(l.getEmailId()))
            .filter(l -> {
                if (reviewerEmail.equals(adminEmail)) {
                    if (!adminTaskLeaveIds.contains(l.getId())) return false;

                    List<Map<String, String>> trail = l.getTrail();
                    if (trail != null) {
                        boolean managerRejected = trail.stream().anyMatch(entry ->
                            LeaveConstants.STATUS_REJECTED.equals(entry.get(LeaveConstants.TRAIL_STATUS)) &&
                            LeaveConstants.STAGE_MANAGER.equals(entry.get(LeaveConstants.TRAIL_STAGE)) &&
                            !LeaveConstants.TYPE_DAY_DECISION.equals(entry.get("type"))
                        );
                        if (managerRejected) return false;
                    }

                    if (l.getDays() != null && !l.getDays().isEmpty()) {
                        List<LeaveDayEntry> approvedDays = l.getDays().stream()
                            .filter(day -> LeaveConstants.STATUS_APPROVED.equals(day.getStatus())
                                && LeaveConstants.STAGE_MANAGER.equals(day.getReviewStage()))
                            .collect(Collectors.toList());
                        if (approvedDays.isEmpty()) {
                            approvedDays = l.getDays().stream()
                                .filter(day -> !LeaveConstants.STATUS_REJECTED.equals(day.getStatus()))
                                .collect(Collectors.toList());
                        }
                        if (approvedDays.isEmpty()) return false;
                        l.setDays(approvedDays);
                        l.setFromDate(LocalDate.parse(approvedDays.get(0).getDate()));
                        l.setToDate(LocalDate.parse(approvedDays.get(approvedDays.size() - 1).getDate()));
                    }
                    return true;
                }
                return (managerTaskLeaveIds.contains(l.getId()) && reviewerEmail.equals(l.getManagerEmail())) ||
                    (adminTaskLeaveIds.contains(l.getId()) && adminEmail.equals(l.getEmailId()));
            })
            .collect(Collectors.toList());
    }
    private List<Long> getLeaveIdsForTask(String taskDefKey) {
        return leaveProcessService.getActiveLeaveIdsForTask(taskDefKey);
    }
    public List<Leave> getLeavesByEmail(String email) {
        return hydrateAll(leaveRepository.findByEmailId(email));
    }

    public List<Leave> getReviewedLeavesByReviewer(String reviewerEmail) {
        log.info("[getReviewedLeavesByReviewer] Getting reviewed leaves for reviewer: {}", reviewerEmail);
        
        List<Leave> allLeaves = hydrateAll(leaveRepository.findAll());
        log.info("[getReviewedLeavesByReviewer] Total leaves in database: {}", allLeaves.size());
        
        // Debug: Log some trail entries to see what's in the database
        allLeaves.stream().limit(3).forEach(leave -> {
            log.info("[getReviewedLeavesByReviewer] Sample leave {} trail: {}", leave.getId(), leave.getTrail());
        });
        
        List<Leave> filteredLeaves = allLeaves.stream()
            .filter(l -> !reviewerEmail.equals(l.getEmailId())) // Exclude reviewer's own leaves
            .filter(l -> {
                List<Map<String, String>> trail = l.getTrail();
                if (trail == null || trail.isEmpty()) {
                    log.debug("[getReviewedLeavesByReviewer] Leave {} has no trail", l.getId());
                    return false;
                }
                
                boolean reviewedByMe = trail.stream().anyMatch(entry -> {
                    String trailReviewer = entry.get(LeaveConstants.TRAIL_REVIEWED_BY);
                    boolean matches = reviewerEmail.equals(trailReviewer);
                    if (matches) {
                        log.info("[getReviewedLeavesByReviewer] Found match for leave {} - reviewer: {}, trail entry: {}", 
                            l.getId(), reviewerEmail, entry);
                    }
                    return matches;
                });
                
                if (reviewedByMe) {
                    log.info("[getReviewedLeavesByReviewer] Including leave {} for reviewer {}", l.getId(), reviewerEmail);
                } else {
                    log.debug("[getReviewedLeavesByReviewer] Excluding leave {} - not reviewed by {}", l.getId(), reviewerEmail);
                }
                
                return reviewedByMe;
            })
            .collect(Collectors.toList());
            
        log.info("[getReviewedLeavesByReviewer] Returning {} reviewed leaves for reviewer: {}", 
            filteredLeaves.size(), reviewerEmail);
        return filteredLeaves;
    }

    public Map<String, Object> getManagerLoggedLeaves(String managerEmail) {
        log.info("[getManagerLoggedLeaves] Getting logged leaves for manager: {}", managerEmail);
        
        return employeeLeaveRepository.findByEmailId(managerEmail)
            .map(empLeave -> {
                Map<String, Object> leaves = empLeave.getLeaves();
                if (leaves != null && leaves.containsKey("approved_leaves")) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("approved_leaves", leaves.get("approved_leaves"));
                    result.put("managerName", empLeave.getFullName());
                    result.put("managerEmail", empLeave.getEmailId());
                    log.info("[getManagerLoggedLeaves] Found {} approved leaves for manager: {}", 
                        ((List<?>) leaves.get("approved_leaves")).size(), managerEmail);
                    return result;
                }
                log.info("[getManagerLoggedLeaves] No approved leaves found for manager: {}", managerEmail);
                return new HashMap<String, Object>();
            })
            .orElse(new HashMap<>());
    }

    public Map<String, Object> getAdminLoggedLeaves(String adminEmail) {
        log.info("[getAdminLoggedLeaves] Getting logged leaves for admin: {}", adminEmail);
        
        return employeeLeaveRepository.findByEmailId(adminEmail)
            .map(empLeave -> {
                Map<String, Object> leaves = empLeave.getLeaves();
                if (leaves != null && leaves.containsKey("approved_leaves")) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("approved_leaves", leaves.get("approved_leaves"));
                    result.put("adminName", empLeave.getFullName());
                    result.put("adminEmail", empLeave.getEmailId());
                    log.info("[getAdminLoggedLeaves] Found {} approved leaves for admin: {}", 
                        ((List<?>) leaves.get("approved_leaves")).size(), adminEmail);
                    return result;
                }
                log.info("[getAdminLoggedLeaves] No approved leaves found for admin: {}", adminEmail);
                return new HashMap<String, Object>();
            })
            .orElse(new HashMap<>());
    }
    public Leave updateLeave(Long id, UpdateLeaveRequest request, String email) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave not found"));
        if (!leave.getEmailId().equals(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        hydrateTransients(leave);
        if (!LeaveConstants.STATUS_PENDING.equals(leave.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending leaves can be edited");
        String dayType = request.getDayType() != null ? request.getDayType() : LeaveConstants.DAY_TYPE_FULL;
        String halfDaySession = LeaveConstants.DAY_TYPE_HALF.equals(dayType) ? request.getHalfDaySession() : null;
        leave.setLeaveType(request.getLeaveType());
        leave.setFromDate(LocalDate.parse(request.getFromDate()));
        leave.setToDate(LocalDate.parse(request.getToDate()));
        leave.setReason(request.getReason());
        leave.setComments(request.getComments());
        leave.setUpdatedAt(LocalDate.now());
        appendTrail(leave, LeaveConstants.STATUS_PENDING, dayType, halfDaySession);
        Leave saved = leaveRepository.save(leave);
        hydrateTransients(saved);
        return saved;
    }
    public void deleteLeave(Long id, String email) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave not found"));
        if (!leave.getEmailId().equals(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        hydrateTransients(leave);
        if (!LeaveConstants.STATUS_PENDING.equals(leave.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending leaves can be deleted");
        leaveRepository.deleteById(id);
        log.info("[LEAVE] Deleted leave id={} by {}", id, email);
    }
    public Leave createLeave(CreateLeaveRequest request, String createdBy) {
        List<LeaveDayEntry> days = request.getDays();
        String dayType;
        String halfDaySession;
        LocalDate fromDate;
        LocalDate toDate;
        if (days != null && !days.isEmpty()) {
            fromDate     = LocalDate.parse(days.get(0).getDate());
            toDate       = LocalDate.parse(days.get(days.size() - 1).getDate());
            dayType      = days.get(0).getDayType() != null ? days.get(0).getDayType() : LeaveConstants.DAY_TYPE_FULL;
            halfDaySession = LeaveConstants.DAY_TYPE_HALF.equals(dayType) ? days.get(0).getHalfDaySession() : null;
        } else {
            dayType      = request.getDayType() != null ? request.getDayType() : LeaveConstants.DAY_TYPE_FULL;
            halfDaySession = LeaveConstants.DAY_TYPE_HALF.equals(dayType) ? request.getHalfDaySession() : null;
            fromDate     = LocalDate.parse(request.getFromDate());
            toDate       = LocalDate.parse(request.getToDate());
        }
        Leave leave = new Leave();
        leave.setLeaveType(request.getLeaveType());
        leave.setFromDate(fromDate);
        leave.setToDate(toDate);
        leave.setReason(request.getReason());
        leave.setComments(request.getComments());
        leave.setEmailId(createdBy);
        leave.setManagerEmail(request.getManagerEmail() != null && !request.getManagerEmail().isBlank() 
            ? request.getManagerEmail() : managerEmail);
        leave.setCreatedAt(LocalDate.now());
        leave.setEditable(true);
        leave.setDays(days);
        List<Leave> overlapping = leaveRepository.findOverlapping(createdBy, fromDate, toDate, -1L);
        overlapping.forEach(this::hydrateTransients);
        if (days != null && !days.isEmpty()) {
            Set<String> submittedDates = days.stream().map(LeaveDayEntry::getDate).collect(Collectors.toSet());
            boolean hasConflict = overlapping.stream()
                .filter(l -> LeaveConstants.STATUS_PENDING.equals(l.getStatus()) || LeaveConstants.STATUS_APPROVED.equals(l.getStatus()))
                .anyMatch(l -> {
                    if (l.getDays() != null && !l.getDays().isEmpty())
                        return l.getDays().stream().anyMatch(d -> submittedDates.contains(d.getDate()));
                    String lf = l.getFromDate().toString();
                    String lt = l.getToDate().toString();
                    return submittedDates.stream().anyMatch(d -> d.compareTo(lf) >= 0 && d.compareTo(lt) <= 0);
                });
            if (hasConflict)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a leave overlapping these dates.");
        } else {
            boolean hasConflict = overlapping.stream()
                .anyMatch(l -> LeaveConstants.STATUS_PENDING.equals(l.getStatus()) || LeaveConstants.STATUS_APPROVED.equals(l.getStatus()));
            if (hasConflict)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a leave overlapping these dates.");
        }
        appendTrail(leave, LeaveConstants.STATUS_PENDING, dayType, halfDaySession);
        Leave saved = leaveRepository.save(leave);
        hydrateTransients(saved);
        log.info("[LEAVE] Created leave id={} by {}", saved.getId(), createdBy);
        String resolvedManager = (request.getManagerEmail() != null && !request.getManagerEmail().isBlank())
            ? request.getManagerEmail() : managerEmail;
        startLeaveProcessAsync(saved, saved.getManagerEmail(), adminEmail);
        return saved;
    }

    @Async
    public void startLeaveProcessAsync(Leave leave, String managerEmail, String adminEmail) {
        leaveProcessService.startLeaveProcess(leave, managerEmail, adminEmail);
    }
}
