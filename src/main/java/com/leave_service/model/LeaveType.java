package com.leave_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
@Entity
@Table(name = "leave_types", schema = "leave")
@Getter
@Setter
@NoArgsConstructor
public class LeaveType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "leave_name")
    private String leaveName;
    @Column(name = "leave_unique_name")
    private String leaveUniqueName;
    @Column(name = "description")
    private String description;
    @Column(name = "max_days")
    private Integer maxDays;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}