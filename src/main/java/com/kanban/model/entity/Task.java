package com.kanban.model.entity;

import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_task_status", columnList = "status"),
    @Index(name = "idx_task_deadline", columnList = "deadline"),
    @Index(name = "idx_task_assigned_to", columnList = "assigned_to_id"),
    @Index(name = "idx_task_team", columnList = "team_id"),
    @Index(name = "idx_task_created_by", columnList = "created_by_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column
    private LocalDateTime deadline;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_labels", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "label")
    private Set<String> labels = new HashSet<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Comment> comments = new HashSet<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Attachment> attachments = new HashSet<>();

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Timestamp of when the task actually transitioned to DONE. Unlike {@link #updatedAt},
     * this is not rewritten by unrelated edits, so it is a reliable completion date for
     * performance analytics. It is maintained automatically by the JPA lifecycle callbacks
     * below: set when the task becomes DONE and cleared when it moves out of DONE.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    @PreUpdate
    private void reconcileCompletedAt() {
        if (status == TaskStatus.DONE) {
            if (completedAt == null) {
                completedAt = LocalDateTime.now();
            }
        } else {
            completedAt = null;
        }
    }
}
