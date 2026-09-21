package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.exception.UnauthorizedException;
import com.kanban.mapper.UserMapper;
import com.kanban.model.dto.request.CreateMemberRequest;
import com.kanban.model.dto.request.UpdateMemberRequest;
import com.kanban.model.dto.request.UpdateUserRequest;
import com.kanban.model.dto.response.UserResponse;
import com.kanban.model.entity.Team;
import com.kanban.model.entity.User;
import com.kanban.model.enums.EmployeeStatus;
import com.kanban.model.enums.EmploymentType;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.AttachmentRepository;
import com.kanban.repository.AttendanceRecordRepository;
import com.kanban.repository.CommentRepository;
import com.kanban.repository.EmployeePerformanceSnapshotRepository;
import com.kanban.repository.PasswordResetTokenRepository;
import com.kanban.repository.TaskRepository;
import com.kanban.repository.TeamRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final EmployeePerformanceSnapshotRepository employeePerformanceSnapshotRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return userMapper.toResponse(user);
    }

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return userMapper.toResponse(user);
    }

    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
            .map(userMapper::toResponse);
    }

    public Page<UserResponse> getUsersByDepartmentAndSkills(String department, String skill, Pageable pageable) {
        return userRepository.findByDepartmentAndSkills(department, skill, pageable)
            .map(userMapper::toResponse);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        userMapper.updateUserFromRequest(request, user);
        user = userRepository.save(user);

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateDirectoryMember(UUID id, UpdateMemberRequest request) {
        User user = getUserEntityById(id);

        if (StringUtils.hasText(request.getEmail())) {
            String email = request.getEmail().trim().toLowerCase();
            userRepository.findByEmail(email).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("Email already registered");
                }
            });
            user.setEmail(email);
        }

        if (request.getProfessionalRole() != null) {
            user.setProfessionalRole(StringUtils.hasText(request.getProfessionalRole())
                ? request.getProfessionalRole().trim()
                : null);
        }
        if (request.getGithubProfile() != null) {
            user.setGithubProfile(normalizeGithub(request.getGithubProfile()));
        }
        if (StringUtils.hasText(request.getDepartment())) {
            user.setDepartment(request.getDepartment().trim());
        }
        if (request.getEmployeeStatus() != null) {
            user.setEmployeeStatus(request.getEmployeeStatus());
        }
        if (request.getJoiningDate() != null) {
            user.setJoiningDate(request.getJoiningDate());
        }

        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void updateLastActive(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setLastActive(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setIsActive(false);
        userRepository.save(user);
    }

    public Page<UserResponse> getUsersByRole(UserRole role, Pageable pageable) {
        return userRepository.findByRole(role, pageable)
            .map(userMapper::toResponse);
    }

    public long getActiveUserCount() {
        return userRepository.countByIsActiveTrue();
    }

    public User getUserEntityById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public UserResponse updateEmploymentType(UUID id, EmploymentType employmentType, UUID currentUserId) {
        User currentUser = getUserEntityById(currentUserId);
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException(
                "Only admin can change employment type"
            );
        }

        User user = getUserEntityById(id);
        user.setEmploymentType(employmentType);
        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateEmployeeStatus(UUID id, EmployeeStatus employeeStatus, UUID currentUserId) {
        getUserEntityById(currentUserId);
        User user = getUserEntityById(id);
        user.setEmployeeStatus(employeeStatus);
        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<String> getDistinctDepartments() {
        return userRepository.findDistinctDepartments();
    }

    @Transactional
    public UserResponse createMember(CreateMemberRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email is required");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        String departmentName = request.getDepartment() == null ? "Engineering" : request.getDepartment().trim();
        if (!StringUtils.hasText(departmentName)) {
            departmentName = "Engineering";
        }
        boolean generatedPassword = !StringUtils.hasText(request.getPassword());
        String rawPassword = generatedPassword
            ? "Welcome@" + ThreadLocalRandom.current().nextInt(1000, 9999)
            : request.getPassword();

        User user = User.builder()
            .email(email)
            .password(passwordEncoder.encode(rawPassword))
            .name(request.getName().trim())
            .department(departmentName)
            .professionalRole(StringUtils.hasText(request.getProfessionalRole()) ? request.getProfessionalRole().trim() : null)
            .githubProfile(normalizeGithub(request.getGithubProfile()))
            .employeeStatus(EmployeeStatus.ONBOARDING)
            .role(UserRole.USER)
            .employmentType(EmploymentType.FULL_TIME)
            .isActive(true)
            .joiningDate(request.getJoiningDate() != null ? request.getJoiningDate() : java.time.LocalDate.now())
            .skills(new HashSet<>())
            .teams(new HashSet<>())
            .comments(new HashSet<>())
            .attachments(new HashSet<>())
            .build();

        user = userRepository.save(user);

        UserResponse response = userMapper.toResponse(user);
        if (generatedPassword) {
            response.setTemporaryPassword(rawPassword);
        }

        String toEmail = user.getEmail();
        String employeeName = user.getName();
        try {
            Runnable sendWelcome = () -> emailService.sendEnrollmentEmail(toEmail, employeeName, rawPassword);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            sendWelcome.run();
                        } catch (Exception ex) {
                            log.warn("Welcome email failed for {}: {}", toEmail, ex.getMessage());
                        }
                    }
                });
            } else {
                sendWelcome.run();
            }
        } catch (Exception ex) {
            log.warn("Could not queue welcome email for {}: {}", toEmail, ex.getMessage());
        }

        return response;
    }

    @Transactional
    public void deleteMember(UUID id, UUID currentUserId) {
        if (id.equals(currentUserId)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }

        User target = getUserEntityById(id);
        User currentUser = getUserEntityById(currentUserId);

        if (target.getRole() == UserRole.ADMIN && currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only an admin can delete another admin");
        }

        for (Team team : teamRepository.findByLead_Id(id)) {
            team.setLead(currentUser);
            if (team.getMembers() == null) {
                team.setMembers(new HashSet<>());
            }
            team.getMembers().add(currentUser);
            teamRepository.save(team);
        }

        taskRepository.deleteByAssignedTo_Id(id);
        taskRepository.reassignCreator(id, currentUser);
        commentRepository.deleteByAuthor_Id(id);
        attachmentRepository.deleteByUploadedBy_Id(id);
        attendanceRecordRepository.deleteByUser_Id(id);
        employeePerformanceSnapshotRepository.deleteByUser_Id(id);
        passwordResetTokenRepository.deleteByUser_Id(id);
        teamRepository.removeFromAllTeams(id);

        userRepository.delete(target);
    }

    private String normalizeGithub(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim()
            .replaceFirst("(?i)^https?://", "")
            .replaceFirst("(?i)^www\\.", "")
            .replaceFirst("(?i)^github\\.com/", "")
            .replaceFirst("^@", "");
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        value = value.trim();
        return StringUtils.hasText(value) ? value : null;
    }

}
