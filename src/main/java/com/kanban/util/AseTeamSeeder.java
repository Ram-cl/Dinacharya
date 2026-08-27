package com.kanban.util;

import com.kanban.model.entity.Department;
import com.kanban.model.entity.User;
import com.kanban.model.enums.EmployeeStatus;
import com.kanban.model.enums.EmploymentType;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.DepartmentRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class AseTeamSeeder implements ApplicationRunner {

    public static final String DEPARTMENT = "ASE";
    public static final String ROLE = "ASE";
    public static final String DEFAULT_PASSWORD = "Welcome@1234";

    public static final List<String> ASE_MEMBERS = List.of(
        "Akkipalli Sri Usha",
        "CH Nikhileshwar Reddy",
        "Ajay Kumar Ramavath",
        "Chintala siva Subramanyam",
        "Nanneboina Hemanth kumar",
        "Kota Prasanthi",
        "Ananya Kamboja",
        "Dondapati Jyothsna Amisha",
        "Boojala Sai Vignesh Reddy",
        "Pattima kalyani"
    );

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting ASE Team Seeder...");
        
        try {
            // Check if tables exist by attempting a simple count
            try {
                userRepository.count();
            } catch (Exception e) {
                log.info("ASE Team Seeder skipped - tables may not exist yet");
                return;
            }
            
            // Step 1: Clean up test data first
            cleanupTestData();
            
            // Step 2: Create ASE department if needed
            if (!departmentRepository.existsByNameIgnoreCase(DEPARTMENT)) {
                departmentRepository.save(Department.builder().name(DEPARTMENT).build());
                log.info("Seeded department: {}", DEPARTMENT);
            }

            // Step 3: Create ASE team members
            for (String name : ASE_MEMBERS) {
                try {
                    if (userRepository.findByNameIgnoreCase(name).isPresent()) {
                        continue;
                    }
                } catch (Exception e) {
                    // Handle enum deserialization errors during role migration
                    log.debug("Skipping user lookup for '{}' due to role migration: {}", name, e.getMessage());
                }
                
                String email = emailFor(name);
                try {
                    if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
                        continue;
                    }
                } catch (Exception e) {
                    // Handle enum deserialization errors during role migration
                    log.debug("Skipping email lookup for '{}' due to role migration: {}", email, e.getMessage());
                }

                User user = User.builder()
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                    .department(DEPARTMENT)
                    .professionalRole(ROLE)
                    .role(UserRole.USER)
                    .employeeStatus(EmployeeStatus.ACTIVE)
                    .employmentType(EmploymentType.FULL_TIME)
                    .isActive(true)
                    .build();
                userRepository.save(user);
                log.info("Seeded ASE member '{}' ({})", name, email);
            }
        } catch (Exception e) {
            log.warn("ASE Team Seeder error (tables may not exist yet): {}", e.getMessage());
        }
    }

    private void cleanupTestData() {
        try {
            log.info("Cleaning up test data...");
            
            // Test employee names to remove
            String[] testNames = {
                "Test Employee",
                "Alice Johnson",
                "Bob Smith",
                "Carol Davis",
                "David Wilson"
            };
            
            // Find all test users
            List<User> testUsers = userRepository.findAll().stream()
                .filter(u -> {
                    for (String name : testNames) {
                        if (u.getName().equalsIgnoreCase(name)) {
                            return true;
                        }
                    }
                    // Also filter by email pattern
                    if (u.getEmail() != null && u.getEmail().contains("@test.com")) {
                        return true;
                    }
                    // Filter placeholder emails (except imported.local)
                    if (u.getEmail() != null && u.getEmail().contains("employee") 
                        && !u.getEmail().contains("@imported.local")) {
                        return true;
                    }
                    return false;
                })
                .toList();
            
            if (!testUsers.isEmpty()) {
                log.info("Found {} test users to delete", testUsers.size());
                
                // Delete the users (cascade should handle related data)
                userRepository.deleteAll(testUsers);
                log.info("Deleted {} test users and their associated data", testUsers.size());
            }
        } catch (Exception e) {
            log.warn("Error during test data cleanup: {}", e.getMessage());
        }
    }

    private static String emailFor(String name) {
        String slug = name.toLowerCase()
            .replaceAll("[^a-z0-9]+", ".")
            .replaceAll("^\\.|\\.$", "");
        return slug + "@imported.local";
    }
}
