package com.kanban.model.entity;

import com.kanban.model.enums.EmployeeStatus;
import com.kanban.model.enums.EmploymentType;
import com.kanban.model.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column
    private String profilePicture;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill")
    private Set<String> skills = new HashSet<>();

    @Column
    private String department;

    @Column(name = "professional_role")
    private String professionalRole;

    @Column(name = "github_profile")
    private String githubProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_status")
    private EmployeeStatus employeeStatus = EmployeeStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column
    private LocalDateTime lastActive;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    private Set<Team> teams = new HashSet<>();

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Comment> comments = new HashSet<>();

    @OneToMany(mappedBy = "uploadedBy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Attachment> attachments = new HashSet<>();

    @Column(name = "admin_request_pending")
    private Boolean adminRequestPending = false;

    @Column(name = "admin_request_date")
    private LocalDateTime adminRequestDate;

    @Column(name = "admin_request_reason", columnDefinition = "TEXT")
    private String adminRequestReason;
}
