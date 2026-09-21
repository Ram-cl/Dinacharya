package com.kanban.controller;

import com.kanban.model.dto.request.AssignTaskRequest;
import com.kanban.model.dto.request.CreateTaskRequest;
import com.kanban.model.dto.request.UpdateTaskRequest;
import com.kanban.model.dto.request.UpdateTaskStatusRequest;
import com.kanban.model.dto.response.TaskResponse;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import com.kanban.security.CustomUserDetailsService;
import com.kanban.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tasks", description = "Task management APIs")
public class TaskController {

    private final TaskService taskService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID")
    @PreAuthorize("hasPermission(#id, 'task', 'read')")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable UUID id) {
        TaskResponse task = taskService.getTaskById(id);
        return ResponseEntity.ok(task);
    }

    @GetMapping
    @Operation(summary = "Get tasks with filters")
    public ResponseEntity<Page<TaskResponse>> getTasks(
        @RequestParam(required = false) UUID teamId,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) UUID assignedToId,
        Pageable pageable,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        Page<TaskResponse> tasks = taskService.getTasksByFilters(
            teamId, status, priority, assignedToId, user.getId(), pageable
        );
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Get overdue tasks")
    public ResponseEntity<Page<TaskResponse>> getOverdueTasks(Pageable pageable) {
        Page<TaskResponse> tasks = taskService.getOverdueTasks(pageable);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/my-tasks")
    @Operation(summary = "Get tasks assigned to current user")
    public ResponseEntity<Page<TaskResponse>> getMyTasks(
        Authentication authentication,
        Pageable pageable
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        Page<TaskResponse> tasks = taskService.getMyTasks(user.getId(), pageable);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping
    @Operation(summary = "Create a new task. If assignedToId is set, an assignment email is sent after the task is saved.")
    public ResponseEntity<TaskResponse> createTask(
        @Valid @RequestBody CreateTaskRequest request,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        TaskResponse task = taskService.createTask(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update task. Changing assignedToId sends an assignment email after commit.")
    public ResponseEntity<TaskResponse> updateTask(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTaskRequest request,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        TaskResponse task = taskService.updateTask(id, request, user.getId());
        return ResponseEntity.ok(task);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update task status")
    public ResponseEntity<TaskResponse> updateTaskStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTaskStatusRequest request,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        TaskResponse task = taskService.updateTaskStatus(id, request.getStatus(), user.getId());
        return ResponseEntity.ok(task);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign task to a user and send an email notification after commit")
    public ResponseEntity<TaskResponse> assignTask(
        @PathVariable UUID id,
        @Valid @RequestBody AssignTaskRequest request,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        TaskResponse task = taskService.assignTask(id, request.getAssigneeId(), user.getId());
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete task")
    public ResponseEntity<Void> deleteTask(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        taskService.deleteTask(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/all")
    @Operation(summary = "Delete all tasks (Admin only) - requires explicit confirmation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteAllTasks(
        @RequestParam(required = false) UUID teamId,
        @RequestParam(defaultValue = "false") boolean confirm,
        Authentication authentication
    ) {
        if (!confirm) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Confirmation required",
                "message", "Set confirm=true parameter to confirm deletion"
            ));
        }

        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        int deletedCount = taskService.deleteAllTasks(teamId, user.getId());
        
        return ResponseEntity.ok(Map.of(
            "message", "Tasks deleted successfully",
            "deletedCount", deletedCount
        ));
    }
}
