package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.mapper.UserMapper;
import com.kanban.model.dto.request.LoginRequest;
import com.kanban.model.dto.request.RefreshTokenRequest;
import com.kanban.model.dto.request.RegisterRequest;
import com.kanban.model.dto.request.ResetPasswordRequest;
import com.kanban.model.dto.response.AuthResponse;
import com.kanban.model.dto.response.TokenValidResponse;
import com.kanban.model.dto.response.UserResponse;
import com.kanban.model.entity.PasswordResetToken;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.PasswordResetTokenRepository;
import com.kanban.repository.UserRepository;
import com.kanban.security.CustomUserDetailsService;
import com.kanban.security.JwtUtil;
import com.kanban.security.PasswordResetRateLimiter;
import com.kanban.security.PasswordResetTokenFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenFactory passwordResetTokenFactory;
    private final PasswordResetRateLimiter passwordResetRateLimiter;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        request.setEmail(email);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = userMapper.fromRegisterRequest(request);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);
        user.setIsActive(true);
        user.setEmployeeStatus(com.kanban.model.enums.EmployeeStatus.ACTIVE);
        if (user.getEmploymentType() == null) {
            user.setEmploymentType(com.kanban.model.enums.EmploymentType.FULL_TIME);
        }

        user = userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateAccessToken(userDetails, user.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails, user.getId());

        UserResponse userResponse = userMapper.toResponse(user);

        auditService.logUserRegistration(user.getId());

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .user(userResponse)
            .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        request.setEmail(email);

        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setLastActive(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateAccessToken(userDetails, user.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails, user.getId());

        UserResponse userResponse = userMapper.toResponse(user);

        auditService.logUserLogin(user.getId());

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .user(userResponse)
            .build();
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtil.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String userEmail = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
        String newAccessToken = jwtUtil.generateAccessToken(userDetails, user.getId(), user.getRole().name());
        String newRefreshToken = jwtUtil.generateRefreshToken(userDetails, user.getId());

        UserResponse userResponse = userMapper.toResponse(user);

        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .user(userResponse)
            .build();
    }

    @Transactional
    public void logout(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setLastActive(LocalDateTime.now());
        userRepository.save(user);

        auditService.logUserLogout(user.getId());
    }

    public boolean isMailReady() {
        return emailService.getStatus().isReady();
    }

    @Transactional
    public void forgotPassword(String email, String clientIp) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        passwordResetRateLimiter.check(normalized, clientIp == null ? "unknown" : clientIp);

        userRepository.findByEmailIgnoreCase(normalized).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            passwordResetTokenRepository.findByUser_IdAndUsedAtIsNull(user.getId())
                .forEach(token -> token.setUsedAt(now));

            String rawToken = passwordResetTokenFactory.newRawToken();
            PasswordResetToken stored = PasswordResetToken.builder()
                .user(user)
                .tokenHash(passwordResetTokenFactory.hash(rawToken))
                .expiresAt(now.plusMinutes(15))
                .build();
            passwordResetTokenRepository.saveAndFlush(stored);

            String name = user.getName();
            String toEmail = user.getEmail();
            Runnable sendMail = () -> emailService.sendPasswordResetEmailNow(toEmail, name, rawToken);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendMail.run();
                    }
                });
            } else {
                sendMail.run();
            }
        });
    }

    @Transactional(readOnly = true)
    public TokenValidResponse validateResetToken(String token) {
        return TokenValidResponse.builder().valid(findUsableToken(token).isPresent()).build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = findUsableToken(request.getToken())
            .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or has expired"));

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.findByUser_IdAndUsedAtIsNull(user.getId())
            .forEach(token -> token.setUsedAt(LocalDateTime.now()));

        auditService.logAction(user.getId(), com.kanban.model.enums.AuditAction.UPDATE, "User", user.getId(),
            java.util.Map.of("event", "password_reset"));
    }

    private java.util.Optional<PasswordResetToken> findUsableToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return java.util.Optional.empty();
        }
        return passwordResetTokenRepository.findByTokenHash(passwordResetTokenFactory.hash(rawToken.trim()))
            .filter(token -> !token.isUsed() && !token.isExpired());
    }
}
