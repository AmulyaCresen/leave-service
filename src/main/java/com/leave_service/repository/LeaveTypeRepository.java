package com.leave_service.repository;
import com.leave_service.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Integer> {
    Optional<LeaveType> findByLeaveName(String leaveName);
    Optional<LeaveType> findByLeaveUniqueName(String leaveUniqueName);
}