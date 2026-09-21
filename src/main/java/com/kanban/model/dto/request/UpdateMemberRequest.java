package com.kanban.model.dto.request;

import com.kanban.model.enums.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberRequest {

    @Size(max = 255, message = "Professional role must not exceed 255 characters")
    private String professionalRole;

    @Size(max = 255, message = "GitHub profile must not exceed 255 characters")
    private String githubProfile;

    @Email(regexp = ".+@.+", message = "Enter a valid work email")
    private String email;

    @Size(max = 255, message = "Department must not exceed 255 characters")
    private String department;

    private EmployeeStatus employeeStatus;

    private LocalDate joiningDate;
}
