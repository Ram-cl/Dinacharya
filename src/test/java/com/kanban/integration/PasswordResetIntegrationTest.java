package com.kanban.integration;

import com.kanban.model.dto.request.LoginRequest;
import com.kanban.model.dto.request.ResetPasswordRequest;
import com.kanban.model.entity.PasswordResetToken;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.PasswordResetTokenRepository;
import com.kanban.repository.UserRepository;
import com.kanban.security.PasswordResetTokenFactory;
import com.kanban.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordResetTokenFactory tokenFactory;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User member;

    @BeforeEach
    void setUp() {
        member = userRepository.save(User.builder()
            .email("worker@example.com")
            .password(passwordEncoder.encode("OldPass123"))
            .name("Worker")
            .role(UserRole.USER)
            .isActive(true)
            .build());
    }

    @Test
    void unknownEmailDoesNotCreateToken() {
        authService.forgotPassword("missing@example.com", "127.0.0.1");
        assertThat(passwordResetTokenRepository.count()).isZero();
    }

    @Test
    void knownEmailCreatesToken() {
        authService.forgotPassword("WORKER@example.com", "127.0.0.1");
        assertThat(passwordResetTokenRepository.count()).isEqualTo(1);
        assertThat(authService.validateResetToken("bogus-token").isValid()).isFalse();
    }

    @Test
    void resetRejectsExpiredAndReusedTokens() {
        String expiredRaw = tokenFactory.newRawToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
            .user(member)
            .tokenHash(tokenFactory.hash(expiredRaw))
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build());

        assertThat(authService.validateResetToken(expiredRaw).isValid()).isFalse();
        assertThatThrownBy(() -> authService.resetPassword(ResetPasswordRequest.builder()
            .token(expiredRaw)
            .newPassword("NewPass123")
            .build()))
            .isInstanceOf(IllegalArgumentException.class);

        String fresh = tokenFactory.newRawToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
            .user(member)
            .tokenHash(tokenFactory.hash(fresh))
            .expiresAt(LocalDateTime.now().plusMinutes(15))
            .build());

        authService.resetPassword(ResetPasswordRequest.builder()
            .token(fresh)
            .newPassword("NewPass123")
            .build());

        assertThatThrownBy(() -> authService.resetPassword(ResetPasswordRequest.builder()
            .token(fresh)
            .newPassword("AnotherPass1")
            .build()))
            .isInstanceOf(IllegalArgumentException.class);

        authService.login(LoginRequest.builder()
            .email("worker@example.com")
            .password("NewPass123")
            .build());
    }
}
