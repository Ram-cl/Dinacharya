package com.kanban.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class CreateMemberRequest {

    @NotBlank(message = "Email is required")
    @Email(regexp = ".+@.+", message = "Enter a valid work email")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @Size(max = 255, message = "Professional role must not exceed 255 characters")
    private String professionalRole;

    @Size(max = 255, message = "GitHub profile must not exceed 255 characters")
    private String githubProfile;

    @NotBlank(message = "Department is required")
    @Size(max = 255, message = "Department must not exceed 255 characters")
    private String department;

    private String password;

    private LocalDate joiningDate;
}
