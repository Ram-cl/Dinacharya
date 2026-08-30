package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.exception.UnauthorizedException;
import com.kanban.mapper.TeamMapper;
import com.kanban.model.dto.request.CreateTeamRequest;
import com.kanban.model.dto.request.UpdateTeamRequest;
import com.kanban.model.dto.response.TeamResponse;
import com.kanban.model.dto.response.UserResponse;
import com.kanban.model.entity.Team;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.TeamRepository;
import com.kanban.repository.UserRepository;
import com.kanban.util.DepartmentNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMapper teamMapper;
    private final UserService userService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TeamResponse getTeamById(UUID id) {
        Team team = teamRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));
        return teamMapper.toResponse(team);
    }

    @Transactional(readOnly = true)
    public Page<TeamResponse> getAllTeams(Pageable pageable) {
        return teamRepository.findAll(pageable).map(teamMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TeamResponse> getTeamsByMember(UUID userId, Pageable pageable) {
        User user = userService.getUserEntityById(userId);
        return teamRepository.findTeamsByLeadOrMember(user.getId(), user, pageable)
            .map(teamMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Set<UUID> getAccessibleTeamIds(UUID userId) {
        User user = userService.getUserEntityById(userId);
        return teamRepository.findTeamsByLeadOrMember(user.getId(), user, Pageable.unpaged())
            .stream()
            .map(Team::getId)
            .collect(Collectors.toSet());
    }

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, UUID leadId) {
        try {
            User lead = userService.getUserEntityById(leadId);

            // Only ADMIN can create teams
            if (lead.getRole() != UserRole.ADMIN) {
                throw new UnauthorizedException("Only admin users can create teams");
            }

            Team team = Team.builder()
                .name(request.getName())
                .description(request.getDescription())
                .lead(lead)
                .members(new HashSet<>())
                .tasks(new HashSet<>())
                .build();
            
            team.getMembers().add(lead);
            team = teamRepository.save(team);
            
            UserResponse leadResponse = UserResponse.builder()
                .id(team.getLead().getId())
                .name(team.getLead().getName())
                .email(team.getLead().getEmail())
                .role(team.getLead().getRole())
                .build();
            
            Set<UserResponse> membersResponse = team.getMembers().stream()
                .map(member -> UserResponse.builder()
                    .id(member.getId())
                    .name(member.getName())
                    .email(member.getEmail())
                    .role(member.getRole())
                    .build())
                .collect(java.util.stream.Collectors.toSet());
            
            return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .lead(leadResponse)
                .members(membersResponse)
                .taskCount(0)
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create team: " + e.getMessage(), e);
        }
    }

    @Transactional
    public TeamResponse updateTeam(UUID id, UpdateTeamRequest request, UUID currentUserId) {
        Team team = teamRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));

        User currentUser = userService.getUserEntityById(currentUserId);
        
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can update team");
        }

        teamMapper.updateTeamFromRequest(request, team);
        team = teamRepository.save(team);

        return teamMapper.toResponse(team);
    }

    @Transactional
    public TeamResponse addMember(UUID teamId, UUID userId, UUID currentUserId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        User currentUser = userService.getUserEntityById(currentUserId);
        
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can add members");
        }

        User newMember = userService.getUserEntityById(userId);
        team.getMembers().add(newMember);
        team = teamRepository.save(team);

        return teamMapper.toResponse(team);
    }

    @Transactional
    public TeamResponse removeMember(UUID teamId, UUID userId, UUID currentUserId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        User currentUser = userService.getUserEntityById(currentUserId);
        
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can remove members");
        }

        if (team.getLead().getId().equals(userId)) {
            throw new IllegalArgumentException("Cannot remove team lead from team");
        }

        User memberToRemove = userService.getUserEntityById(userId);
        team.getMembers().remove(memberToRemove);
        team = teamRepository.save(team);

        return teamMapper.toResponse(team);
    }

    @Transactional
    public void deleteTeam(UUID id, UUID currentUserId) {
        Team team = teamRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));

        User currentUser = userService.getUserEntityById(currentUserId);
        
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can delete team");
        }

        teamRepository.delete(team);
    }

    @Transactional(readOnly = true)
    public Team getTeamEntityById(UUID id) {
        return teamRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));
    }

    @Transactional
    public Team getOrCreatePersonalTeam(User user) {
        Page<Team> existing = teamRepository.findTeamsByLeadOrMember(
            user.getId(), user, PageRequest.of(0, 1)
        );
        if (existing.hasContent()) {
            return existing.getContent().get(0);
        }

        Team team = Team.builder()
            .name("Daily work")
            .description("Personal daily task log")
            .lead(user)
            .members(new HashSet<>())
            .tasks(new HashSet<>())
            .build();
        team.getMembers().add(user);
        return teamRepository.save(team);
    }

    /**
     * Each department is a team of the same name (ASE, DevOps, UI, …).
     */
    @Transactional
    public Team getOrCreateDepartmentTeam(String departmentName, User lead) {
        String name = DepartmentNames.canonical(departmentName);
        if (name == null || name.isBlank()) {
            name = "Engineering";
        }
        final String teamName = name;
        User managedLead = lead;
        if (lead != null && lead.getId() != null) {
            managedLead = userRepository.findById(lead.getId()).orElse(lead);
        }
        final User teamLead = managedLead;
        return teamRepository.findByNameIgnoreCase(teamName).orElseGet(() -> {
            Team team = Team.builder()
                    .name(teamName)
                    .description("Department: " + teamName)
                    .lead(teamLead)
                    .members(new HashSet<>())
                    .tasks(new HashSet<>())
                    .build();
            if (teamLead != null) {
                team.getMembers().add(teamLead);
            }
            return teamRepository.save(team);
        });
    }

    public boolean isMember(Team team, User user) {
        if (team == null || user == null || user.getId() == null) {
            return false;
        }
        if (team.getLead() != null && user.getId().equals(team.getLead().getId())) {
            return true;
        }
        return team.getMembers() != null && team.getMembers().stream()
            .anyMatch(member -> user.getId().equals(member.getId()));
    }

    @Transactional
    public void ensureMember(Team team, User user) {
        if (isMember(team, user)) {
            return;
        }
        if (team.getMembers() == null) {
            team.setMembers(new HashSet<>());
        }
        team.getMembers().add(user);
        teamRepository.save(team);
    }
}
