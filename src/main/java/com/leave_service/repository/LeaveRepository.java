package com.leave_service.repository;
import com.leave_service.model.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LeaveRepository extends JpaRepository<Leave, Long> {
    List<Leave> findByEmailId(String emailId);
    @org.springframework.data.jpa.repository.Query(
        "SELECT l FROM Leave l WHERE l.emailId = :email AND l.id <> :excludeId " +
        "AND l.fromDate <= :toDate AND l.toDate >= :fromDate")
    List<Leave> findOverlapping(
        @org.springframework.data.repository.query.Param("email") String email,
        @org.springframework.data.repository.query.Param("fromDate") java.time.LocalDate fromDate,
        @org.springframework.data.repository.query.Param("toDate") java.time.LocalDate toDate,
        @org.springframework.data.repository.query.Param("excludeId") Long excludeId);
}