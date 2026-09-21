package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.mapper.UserMapper;
import com.kanban.model.dto.response.UserResponse;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserResponse testUserResponse;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        
        testUser = User.builder()
            .id(testUserId)
            .email("test@example.com")
            .password("hashedPassword")
            .name("Test User")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        testUserResponse = UserResponse.builder()
            .id(testUserId)
            .email("test@example.com")
            .name("Test User")
            .role(UserRole.USER)
            .isActive(true)
            .build();
    }

    @Test
    void shouldGetUserById() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        UserResponse result = userService.getUserById(testUserId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testUserId);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        
        verify(userRepository).findById(testUserId);
        verify(userMapper).toResponse(testUser);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User not found");
        
        verify(userRepository).findById(any(UUID.class));
        verify(userMapper, never()).toResponse(any());
    }

    @Test
    void shouldDeactivateUser() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.deactivateUser(testUserId);

        assertThat(testUser.getIsActive()).isFalse();
        verify(userRepository).save(testUser);
    }
}
