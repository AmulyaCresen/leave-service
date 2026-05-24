package com.leave_service.repository;
import com.leave_service.model.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {
    Optional<EmployeeLeave> findByEmailId(String emailId);
    Optional<EmployeeLeave> findByUserId(Long userId);
}