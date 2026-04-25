package com.leave_service.model;
import com.leave_service.dto.LeaveDayEntry;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
@Entity
@Table(name = "leave", schema = "leave")
@Getter
@Setter
@NoArgsConstructor
public class Leave {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "leave_type")
    private String leaveType;
    @Column(name = "from_date")
    private LocalDate fromDate;
    @Column(name = "to_date")
    private LocalDate toDate;
    @Column(name = "email_id")
    private String emailId;
    @Column(name = "manager_email")
    private String managerEmail;
    @Column(name = "reason")
    private String reason;
    @Column(name = "comments")
    private String comments;
    @Column(name = "created_at")
    private LocalDate createdAt;
    @Column(name = "updated_at")
    private LocalDate updatedAt;
    @Column(name = "editable")
    private Boolean editable = true;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "days", columnDefinition = "jsonb")
    private List<LeaveDayEntry> days;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trail", columnDefinition = "jsonb")
    private List<Map<String, String>> trail;
    @Transient
    private String status;
    @Transient
    private String dayType;
    @Transient
    private String halfDaySession;
    @Transient
    private Double totalDays;
    @Transient
    private String reviewedBy;
}