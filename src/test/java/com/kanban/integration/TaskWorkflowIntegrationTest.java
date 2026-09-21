package com.kanban.integration;

import com.kanban.model.dto.request.CreateTaskRequest;
import com.kanban.model.dto.request.UpdateTaskStatusRequest;
import com.kanban.model.dto.response.TaskResponse;
import com.kanban.model.entity.Team;
import com.kanban.model.entity.User;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.TeamRepository;
import com.kanban.repository.UserRepository;
import com.kanban.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskWorkflowIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    private User creator;
    private User assignee;
    private Team team;

    @BeforeEach
    void setUp() {
        // Create test users
        creator = User.builder()
            .email("creator@example.com")
            .password("password")
            .name("Creator")
            .role(UserRole.ADMIN)  // Use ADMIN for test task creator
            .isActive(true)
            .build();
        creator = userRepository.save(creator);

        assignee = User.builder()
            .email("assignee@example.com")
            .password("password")
            .name("Assignee")
            .role(UserRole.USER)
            .isActive(true)
            .build();
        assignee = userRepository.save(assignee);

        // Create test team
        team = Team.builder()
            .name("Test Team")
            .description("A test team")
            .lead(creator)
            .members(new HashSet<>(Set.of(creator, assignee)))
            .build();
        team = teamRepository.save(team);
    }

    @Test
    void shouldCompleteFullTaskWorkflow() {
        // 1. Create task
        CreateTaskRequest createRequest = CreateTaskRequest.builder()
            .title("Test Task")
            .description("This is a test task")
            .priority(TaskPriority.HIGH)
            .deadline(LocalDateTime.now().plusDays(7))
            .teamId(team.getId())
            .build();

        TaskResponse createdTask = taskService.createTask(createRequest, creator.getId());

        assertThat(createdTask).isNotNull();
        assertThat(createdTask.getTitle()).isEqualTo("Test Task");
        assertThat(createdTask.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(createdTask.getPriority()).isEqualTo(TaskPriority.HIGH);

        // 2. Assign task
        TaskResponse assignedTask = taskService.assignTask(
            createdTask.getId(), 
            assignee.getId(), 
            creator.getId()
        );

        assertThat(assignedTask.getAssignedTo()).isNotNull();
        assertThat(assignedTask.getAssignedTo().getId()).isEqualTo(assignee.getId());

        // 3. Update task status to IN_PROGRESS (creator/lead can edit)
        TaskResponse inProgressTask = taskService.updateTaskStatus(
            createdTask.getId(),
            TaskStatus.IN_PROGRESS,
            creator.getId()
        );

        assertThat(inProgressTask.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        // 4. Update task status to DONE
        TaskResponse completedTask = taskService.updateTaskStatus(
            createdTask.getId(),
            TaskStatus.DONE,
            creator.getId()
        );

        assertThat(completedTask.getStatus()).isEqualTo(TaskStatus.DONE);

        // Verify final state
        TaskResponse finalTask = taskService.getTaskById(createdTask.getId());
        assertThat(finalTask.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(finalTask.getAssignedTo().getId()).isEqualTo(assignee.getId());
    }
}
