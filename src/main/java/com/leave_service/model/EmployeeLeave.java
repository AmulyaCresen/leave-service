package com.leave_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
@Entity
@Table(name = "employee_leave", schema = "leave")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeLeave {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", unique = true)
    private Long userId;
    @Column(name = "full_name")
    private String fullName;
    @Column(name = "email_id", unique = true)
    private String emailId;
    @Column(name = "gender")
    private String gender;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leaves", columnDefinition = "jsonb")
    private Map<String, Object> leaves;
}