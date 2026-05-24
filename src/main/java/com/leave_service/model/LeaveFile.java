package com.leave_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity
@Table(name = "leave_file", schema = "leave")
@Getter
@Setter
@NoArgsConstructor
public class LeaveFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "leave_id")
    private Long leaveId;
    @Column(name = "file_name")
    private String fileName;
    @Column(name = "file_path")
    private String filePath;
    @Column(name = "file_size")
    private Long fileSize;
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;
    @Column(name = "uploaded_by")
    private String uploadedBy;
}
