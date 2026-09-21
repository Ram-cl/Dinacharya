package com.kanban.model.entity;

import com.kanban.model.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "time_entries", indexes = {
    @Index(name = "idx_time_entry_user", columnList = "user_id"),
    @Index(name = "idx_time_entry_date", columnList = "entry_date"),
    @Index(name = "idx_time_entry_user_date", columnList = "user_id,entry_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "entry_time")
    private LocalTime entryTime;

    @Column(name = "exit_time")
    private LocalTime exitTime;

    @Column(name = "hours_worked")
    private Double hoursWorked;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, columnDefinition = "VARCHAR(50)")
    private AttendanceStatus status;

    @Column(name = "break_duration_minutes")
    private Integer breakDurationMinutes;

    @Column(name = "remark", length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
