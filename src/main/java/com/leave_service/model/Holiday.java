package com.leave_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
@Entity
@Table(name = "holidays", schema = "leave")
@Getter
@Setter
@NoArgsConstructor
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "name")
    private String name;
    @Column(name = "date")
    private LocalDate date;
}