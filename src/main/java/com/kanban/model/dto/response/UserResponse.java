package com.kanban.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kanban.model.enums.EmployeeStatus;
import com.kanban.model.enums.EmploymentType;
import com.kanban.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private UUID id;
    private String email;
    private String name;
    private UserRole role;
    private String profilePicture;
    private String bio;
    private Set<String> skills;
    private String department;
    private String professionalRole;
    private String githubProfile;
    private EmployeeStatus employeeStatus;
    private EmploymentType employmentType;
    private Boolean isActive;
    private LocalDateTime lastActive;
    private LocalDate joiningDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String temporaryPassword;
}
