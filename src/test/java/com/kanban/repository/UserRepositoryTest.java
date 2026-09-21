package com.kanban.repository;

import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .email("test@example.com")
            .password("hashedPassword")
            .name("Test User")
            .role(UserRole.USER)
            .department("Engineering")
            .skills(Set.of("Java", "Spring Boot"))
            .isActive(true)
            .build();
    }

    @Test
    void shouldSaveUser() {
        User saved = userRepository.save(testUser);
        
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getName()).isEqualTo("Test User");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void shouldFindUserByEmail() {
        userRepository.save(testUser);
        
        Optional<User> found = userRepository.findByEmail("test@example.com");
        
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test User");
    }

    @Test
    void shouldFindUsersByDepartment() {
        userRepository.save(testUser);
        
        var users = userRepository.findByDepartmentAndSkills("Engineering", null, org.springframework.data.domain.Pageable.unpaged());
        
        assertThat(users).isNotEmpty();
    }

    @Test
    void shouldCountActiveUsers() {
        userRepository.save(testUser);
        
        long count = userRepository.countByIsActiveTrue();
        
        assertThat(count).isGreaterThan(0);
    }
}
