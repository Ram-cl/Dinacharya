package com.kanban.service;

import com.kanban.exception.OptimisticLockException;
import com.kanban.exception.ResourceNotFoundException;
import com.kanban.exception.UnauthorizedException;
import com.kanban.mapper.TaskMapper;
import com.kanban.model.dto.request.CreateTaskRequest;
import com.kanban.model.dto.request.UpdateTaskRequest;
import com.kanban.model.dto.response.TaskResponse;
import com.kanban.model.entity.Task;
import com.kanban.model.entity.Team;
import com.kanban.model.entity.User;
import com.kanban.model.enums.AuditAction;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final UserService userService;
    private final TeamService teamService;
    private final AuditService auditService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(UUID id) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        return taskMapper.toResponse(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> getTasksByFilters(
        UUID teamId,
        TaskStatus status,
        TaskPriority priority,
        UUID assignedToId,
        UUID currentUserId,
        Pageable pageable
    ) {
        User currentUser = userService.getUserEntityById(currentUserId);

        if (teamId != null) {
            return taskRepository.findTasksByFilters(teamId, status, priority, assignedToId, pageable)
                .map(taskMapper::toResponse);
        }

        if (currentUser.getRole() == UserRole.ADMIN) {
            return taskRepository.findAllTasksByFilters(status, priority, assignedToId, pageable)
                .map(taskMapper::toResponse);
        }

        Set<UUID> teamIds = teamService.getAccessibleTeamIds(currentUserId);
        if (teamIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return taskRepository.findTasksByTeamIds(teamIds, status, priority, assignedToId, pageable)
            .map(taskMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> getOverdueTasks(Pageable pageable) {
        return taskRepository.findOverdueTasks(LocalDateTime.now(), pageable)
            .map(taskMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> getMyTasks(UUID userId, Pageable pageable) {
        return taskRepository.findEmployeeWorkspaceTasks(userId, pageable)
            .map(taskMapper::toResponse);
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, UUID createdById) {
        User createdBy = userService.getUserEntityById(createdById);
        boolean isAdmin = createdBy.getRole() == UserRole.ADMIN;
        Team team;

        if (request.getTeamId() != null) {
            team = teamService.getTeamEntityById(request.getTeamId());
            if (!teamService.isMember(team, createdBy) && !isAdmin) {
                teamService.ensureMember(team, createdBy);
            }
        } else {
            team = teamService.getOrCreatePersonalTeam(createdBy);
        }

        Task task = taskMapper.fromCreateRequest(request);
        task.setCreatedBy(createdBy);
        task.setTeam(team);
        task.setStatus(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO);

        if (request.getAssignedToId() != null) {
            User assignee = userService.getUserEntityById(request.getAssignedToId());
            if (!teamService.isMember(team, assignee) && !isAdmin && !assignee.getId().equals(createdById)) {
                throw new UnauthorizedException("You can only assign daily tasks to yourself");
            }
            teamService.ensureMember(team, assignee);
            task.setAssignedTo(assignee);
        } else if (!isAdmin) {
            teamService.ensureMember(team, createdBy);
            task.setAssignedTo(createdBy);
        }

        task = taskRepository.save(task);
        auditService.logTaskCreated(createdById, task.getId(), task.getTitle());

        notifyAssigneeAfterCommit(task);

        return taskMapper.toResponse(task);
    }

    /**
     * Bulk task creation optimized for file imports. Unlike calling
     * {@link #createTask} in a loop, this:
     *   - resolves the creator once (not per row),
     *   - caches each team and assignee entity (repeat employees hit the DB once),
     *   - adds new team members in-memory and lets managed-entity dirty checking
     *     flush team_members a single time at commit,
     *   - saves all tasks with {@code saveAll} so Hibernate JDBC batching applies,
     *   - writes a single summary audit entry instead of one per row,
     *   - skips per-row assignee emails (an import shouldn't spam notifications).
     * Every request must carry a teamId. The whole batch commits atomically.
     */
    @Transactional
    public List<TaskResponse> createTasksBulk(List<CreateTaskRequest> requests, UUID createdById) {
        List<TaskResponse> responses = new ArrayList<>();
        if (requests == null || requests.isEmpty()) {
            return responses;
        }

        User createdBy = userService.getUserEntityById(createdById);
        boolean isAdmin = createdBy.getRole() == UserRole.ADMIN;

        Map<UUID, Team> teamCache = new HashMap<>();
        Map<UUID, User> userCache = new HashMap<>();
        userCache.put(createdBy.getId(), createdBy);
        // Per-team snapshot of member ids, so each new member is added only once.
        Map<UUID, Set<UUID>> teamMemberIds = new HashMap<>();

        List<Task> toSave = new ArrayList<>();

        for (CreateTaskRequest request : requests) {
            if (request.getTeamId() == null) {
                throw new IllegalArgumentException("Bulk task creation requires a teamId on every row");
            }

            Team team = teamCache.computeIfAbsent(request.getTeamId(), teamService::getTeamEntityById);
            Set<UUID> memberIds = teamMemberIds.computeIfAbsent(team.getId(), k -> {
                Set<UUID> ids = new HashSet<>();
                if (team.getLead() != null) {
                    ids.add(team.getLead().getId());
                }
                if (team.getMembers() != null) {
                    team.getMembers().forEach(m -> ids.add(m.getId()));
                }
                return ids;
            });

            Task task = taskMapper.fromCreateRequest(request);
            task.setCreatedBy(createdBy);
            task.setTeam(team);
            task.setStatus(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO);

            User assignee = null;
            if (request.getAssignedToId() != null) {
                assignee = userCache.computeIfAbsent(request.getAssignedToId(), userService::getUserEntityById);
                if (!memberIds.contains(assignee.getId()) && !isAdmin && !assignee.getId().equals(createdById)) {
                    throw new UnauthorizedException("You can only assign daily tasks to yourself");
                }
            } else if (!isAdmin) {
                assignee = createdBy;
            }

            if (assignee != null) {
                task.setAssignedTo(assignee);
                if (!memberIds.contains(assignee.getId())) {
                    // Team owns the team_members join table; adding here flushes at commit.
                    team.getMembers().add(assignee);
                    memberIds.add(assignee.getId());
                }
            }

            toSave.add(task);
        }

        List<Task> saved = taskRepository.saveAll(toSave);

        // Single audit entry for the whole import instead of one insert per row.
        auditService.logAction(createdById, AuditAction.CREATE, "Task", createdById,
            Map.of("event", "bulk_import", "count", saved.size()));

        for (Task task : saved) {
            responses.add(taskMapper.toResponse(task));
        }
        return responses;
    }

    @Transactional
    public TaskResponse updateTask(UUID id, UpdateTaskRequest request, UUID currentUserId) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        validateTaskEditPermission(task, currentUserId);

        UUID previousAssigneeId = task.getAssignedTo() != null ? task.getAssignedTo().getId() : null;

        Map<String, Object> changes = new HashMap<>();

        if (request.getTitle() != null) {
            changes.put("title", Map.of("old", task.getTitle(), "new", request.getTitle()));
        }
        if (request.getPriority() != null) {
            changes.put("priority", Map.of("old", task.getPriority(), "new", request.getPriority()));
        }
        if (request.getDeadline() != null) {
            changes.put("deadline", Map.of("old", task.getDeadline(), "new", request.getDeadline()));
        }

        taskMapper.updateTaskFromRequest(request, task);

        if (request.getAssignedToId() != null) {
            User assignee = userService.getUserEntityById(request.getAssignedToId());
            teamService.ensureMember(task.getTeam(), assignee);
            task.setAssignedTo(assignee);
            changes.put("assignedTo", assignee.getId().toString());
        }

        try {
            task = taskRepository.save(task);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockException("Task was modified by another user. Please refresh and try again.");
        }

        auditService.logTaskUpdated(currentUserId, task.getId(), changes);

        if (request.getAssignedToId() != null && !request.getAssignedToId().equals(previousAssigneeId)) {
            notifyAssigneeAfterCommit(task);
        }

        return taskMapper.toResponse(task);
    }

    @Transactional
    public TaskResponse updateTaskStatus(UUID id, TaskStatus newStatus, UUID currentUserId) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        User currentUser = userService.getUserEntityById(currentUserId);
        boolean isCreator = task.getCreatedBy().getId().equals(currentUserId);
        boolean isAssignee = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        if (!isCreator && !isAssignee && !isAdmin) {
            throw new UnauthorizedException("You don't have permission to update this task status");
        }

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);

        try {
            task = taskRepository.save(task);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockException("Task was modified by another user. Please refresh and try again.");
        }

        auditService.logTaskStatusChanged(currentUserId, task.getId(), oldStatus.name(), newStatus.name());

        return taskMapper.toResponse(task);
    }

    @Transactional
    public TaskResponse assignTask(UUID id, UUID assigneeId, UUID currentUserId) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        validateTaskEditPermission(task, currentUserId);

        User assignee = userService.getUserEntityById(assigneeId);
        teamService.ensureMember(task.getTeam(), assignee);
        task.setAssignedTo(assignee);

        try {
            task = taskRepository.save(task);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockException("Task was modified by another user. Please refresh and try again.");
        }

        auditService.logTaskAssigned(currentUserId, task.getId(), assigneeId);
        notifyAssigneeAfterCommit(task);

        return taskMapper.toResponse(task);
    }

    @Transactional
    public void deleteTask(UUID id, UUID currentUserId) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        validateTaskEditPermission(task, currentUserId);

        String taskTitle = task.getTitle();
        UUID teamId = task.getTeam().getId();

        if (task.getComments() != null) {
            task.getComments().clear();
        }
        if (task.getAttachments() != null) {
            task.getAttachments().clear();
        }
        if (task.getLabels() != null) {
            task.getLabels().clear();
        }

        taskRepository.delete(task);
        auditService.logTaskDeleted(currentUserId, id, taskTitle);
    }

    @Transactional
    public int deleteAllTasks(UUID teamId, UUID currentUserId) {
        User currentUser = userService.getUserEntityById(currentUserId);

        // Only ADMIN role can delete all tasks
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admins can delete all tasks");
        }

        int deletedCount = 0;
        if (teamId != null) {
            // Delete all tasks in a specific team
            java.util.List<Task> tasks = taskRepository.findByTeamId(teamId);
            deletedCount = tasks.size();
            for (Task task : tasks) {
                clearTaskRelations(task);
            }
            taskRepository.deleteAll(tasks);
            auditService.logAction(currentUserId, com.kanban.model.enums.AuditAction.DELETE, "Task", teamId, 
                java.util.Map.of("event", "delete_all_in_team", "count", deletedCount));
        } else {
            // Delete ALL tasks in the system
            java.util.List<Task> allTasks = taskRepository.findAll();
            deletedCount = allTasks.size();
            java.util.Set<UUID> affectedTeams = new java.util.HashSet<>();
            for (Task task : allTasks) {
                clearTaskRelations(task);
                affectedTeams.add(task.getTeam().getId());
            }
            taskRepository.deleteAll(allTasks);
            auditService.logAction(currentUserId, com.kanban.model.enums.AuditAction.DELETE, "Task", currentUserId, 
                java.util.Map.of("event", "delete_all_system", "count", deletedCount));
        }
        return deletedCount;
    }

    private void clearTaskRelations(Task task) {
        if (task.getComments() != null) {
            task.getComments().clear();
        }
        if (task.getAttachments() != null) {
            task.getAttachments().clear();
        }
        if (task.getLabels() != null) {
            task.getLabels().clear();
        }
    }

    private void validateTaskEditPermission(Task task, UUID currentUserId) {
        User currentUser = userService.getUserEntityById(currentUserId);

        boolean isCreator = task.getCreatedBy().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;

        if (!isCreator && !isAdmin) {
            throw new UnauthorizedException("You don't have permission to edit this task");
        }
    }

    private void notifyAssigneeAfterCommit(Task task) {
        if (task.getAssignedTo() == null || task.getAssignedTo().getEmail() == null) {
            return;
        }
        if (task.getCreatedBy() != null && task.getAssignedTo().getId().equals(task.getCreatedBy().getId())) {
            return;
        }

        String toEmail = task.getAssignedTo().getEmail();
        String employeeName = task.getAssignedTo().getName();
        String title = task.getTitle();
        String priority = task.getPriority() != null ? task.getPriority().name() : TaskPriority.MEDIUM.name();
        LocalDateTime dueDate = task.getDeadline();
        String description = task.getDescription();
        UUID taskId = task.getId();

        Runnable sendMail = () -> emailService.sendTaskAssignmentEmail(
            toEmail, employeeName, title, priority, dueDate, description, taskId
        );

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendMail.run();
                }
            });
            return;
        }

        sendMail.run();
    }

    @Transactional(readOnly = true)
    public Task getTaskEntityById(UUID id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
    }
}
