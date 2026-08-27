package com.kanban.util;

import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Creates a default admin user when enabled via configuration.
 * Set ADMIN_EMAIL and ADMIN_PASSWORD in your local .env (never commit real values).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(100) // Run after other initializers
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.enabled:false}")
    private boolean adminEnabled;

    @Value("${app.admin.email:admin@taskhub.com}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!adminEnabled) {
            log.info("Default admin bootstrap disabled (set ADMIN_ENABLED=true to enable)");
            return;
        }

        if (!StringUtils.hasText(adminPassword)) {
            log.warn("ADMIN_ENABLED is true but ADMIN_PASSWORD is empty — skipping default admin creation");
            return;
        }

        try {
            if (userRepository.findByEmailIgnoreCase(adminEmail).isEmpty()) {
                log.info("Creating default admin user for {}", adminEmail);

                User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .name("Administrator")
                    .role(UserRole.ADMIN)
                    .isActive(true)
                    .lastActive(LocalDateTime.now())
                    .build();

                userRepository.save(admin);
                log.info("Default admin user created for {}", adminEmail);
            } else {
                log.info("Admin user already exists: {}", adminEmail);
            }
        } catch (Exception e) {
            log.error("Failed to create admin user: {}. Database tables may not be ready.", e.getMessage());
        }
    }
}
