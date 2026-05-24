package com.leave_service.repository;
import com.leave_service.model.LeaveFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface LeaveFileRepository extends JpaRepository<LeaveFile, Long> {
    List<LeaveFile> findByLeaveId(Long leaveId);
    void deleteByLeaveId(Long leaveId);
}
