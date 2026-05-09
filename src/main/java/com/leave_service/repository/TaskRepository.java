package com.leave_service.repository;

import com.leave_service.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByEmailIdOrderByCreatedAtDesc(String emailId);
    List<Task> findByEmailIdAndStatusOrderByCreatedAtDesc(String emailId, String status);
    List<Task> findByStatusOrderByCreatedAtDesc(String status);
    List<Task> findAllByOrderByCreatedAtDesc();
    
    @Query("SELECT t FROM Task t WHERE (t.managerEmail = :email OR t.emailId = :email) ORDER BY t.createdAt DESC")
    List<Task> findByManagerOrOwner(@Param("email") String email);
    
    @Query("SELECT t FROM Task t WHERE (t.managerEmail = :email OR t.emailId = :email) AND t.status = :status ORDER BY t.createdAt DESC")
    List<Task> findByManagerOrOwnerAndStatus(@Param("email") String email, @Param("status") String status);
}
