package com.kanban.service;

import com.kanban.mapper.TeamMapper;
import com.kanban.model.dto.request.CreateTeamRequest;
import com.kanban.model.dto.response.TeamResponse;
import com.kanban.model.dto.response.UserResponse;
import com.kanban.model.entity.Team;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.TeamRepository;
import com.kanban.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMapper teamMapper;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeamService teamService;

    private User lead;
    private CreateTeamRequest createRequest;
    private Team savedTeam;
    private TeamResponse teamResponse;

    @BeforeEach
    void setUp() {
        lead = User.builder()
            .id(UUID.randomUUID())
            .email("lead@example.com")
            .name("Lead")
            .role(UserRole.ADMIN)  // Use ADMIN for test team lead
            .isActive(true)
            .build();

        createRequest = CreateTeamRequest.builder()
            .name("Platform Team")
            .description("Team description")
            .build();

        savedTeam = Team.builder()
            .id(UUID.randomUUID())
            .name("Platform Team")
            .description("Team description")
            .lead(lead)
            .members(new HashSet<>())
            .build();
        savedTeam.getMembers().add(lead);

        teamResponse = TeamResponse.builder()
            .id(savedTeam.getId())
            .name("Platform Team")
            .description("Team description")
            .lead(UserResponse.builder()
                .id(lead.getId())
                .email(lead.getEmail())
                .name(lead.getName())
                .role(lead.getRole())
                .isActive(lead.getIsActive())
                .build())
            .build();
    }

    @Test
    void shouldRequireAdminRoleWhenCreatingTeam() {
        when(userService.getUserEntityById(lead.getId())).thenReturn(lead);
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        TeamResponse result = teamService.createTeam(createRequest, lead.getId());

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Platform Team");
        assertThat(lead.getRole()).isEqualTo(UserRole.ADMIN);
        verify(teamRepository).save(any(Team.class));
    }
}
