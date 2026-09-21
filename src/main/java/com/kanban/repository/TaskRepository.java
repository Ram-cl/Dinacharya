package com.kanban.repository;

import com.kanban.model.entity.Task;
import com.kanban.model.entity.Team;
import com.kanban.model.entity.User;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Page<Task> findByTeam(Team team, Pageable pageable);

    Page<Task> findByTeamAndStatus(Team team, TaskStatus status, Pageable pageable);

    Page<Task> findByTeamAndPriority(Team team, TaskPriority priority, Pageable pageable);

    Page<Task> findByAssignedTo(User user, Pageable pageable);

    @Query("""
        SELECT t FROM Task t 
        WHERE t.team.id = :teamId
        AND (:status IS NULL OR t.status = :status)
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:assignedToId IS NULL OR t.assignedTo.id = :assignedToId)
        ORDER BY t.deadline ASC NULLS LAST, t.priority DESC, t.createdAt DESC
        """)
    Page<Task> findTasksByFilters(
        @Param("teamId") UUID teamId,
        @Param("status") TaskStatus status,
        @Param("priority") TaskPriority priority,
        @Param("assignedToId") UUID assignedToId,
        Pageable pageable
    );

    @Query("""
        SELECT t FROM Task t 
        WHERE (:status IS NULL OR t.status = :status)
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:assignedToId IS NULL OR t.assignedTo.id = :assignedToId)
        ORDER BY t.deadline ASC NULLS LAST, t.priority DESC, t.createdAt DESC
        """)
    Page<Task> findAllTasksByFilters(
        @Param("status") TaskStatus status,
        @Param("priority") TaskPriority priority,
        @Param("assignedToId") UUID assignedToId,
        Pageable pageable
    );

    @Query("""
        SELECT t FROM Task t 
        WHERE t.team.id IN :teamIds
        AND (:status IS NULL OR t.status = :status)
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:assignedToId IS NULL OR t.assignedTo.id = :assignedToId)
        ORDER BY t.deadline ASC NULLS LAST, t.priority DESC, t.createdAt DESC
        """)
    Page<Task> findTasksByTeamIds(
        @Param("teamIds") Set<UUID> teamIds,
        @Param("status") TaskStatus status,
        @Param("priority") TaskPriority priority,
        @Param("assignedToId") UUID assignedToId,
        Pageable pageable
    );

    @Query("""
        SELECT t FROM Task t 
        WHERE t.deadline IS NOT NULL 
        AND t.deadline < :now
        AND t.status != 'DONE'
        ORDER BY t.deadline ASC
        """)
    Page<Task> findOverdueTasks(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
        SELECT t FROM Task t 
        WHERE t.team.id IN :teamIds
        AND t.deadline IS NOT NULL 
        AND t.deadline < :now
        AND t.status != 'DONE'
        ORDER BY t.deadline ASC
        """)
    Page<Task> findOverdueTasksByTeamIds(@Param("teamIds") Set<UUID> teamIds, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
        SELECT t FROM Task t 
        WHERE t.team.id = :teamId
        AND t.deadline IS NOT NULL 
        AND t.deadline < :now
        AND t.status != 'DONE'
        ORDER BY t.deadline ASC
        """)
    Set<Task> findOverdueTasksByTeam(@Param("teamId") UUID teamId, @Param("now") LocalDateTime now);

    @Query("""
        SELECT COUNT(t) FROM Task t 
        WHERE t.team.id = :teamId AND t.status = :status
        """)
    long countByTeamAndStatus(@Param("teamId") UUID teamId, @Param("status") TaskStatus status);

    @Query("""
        SELECT t FROM Task t 
        WHERE t.createdBy.id = :userId AND t.status != 'DONE'
        ORDER BY t.deadline ASC NULLS LAST, t.priority DESC
        """)
    Page<Task> findAssignedTasksByCreator(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
        SELECT t FROM Task t 
        WHERE t.assignedTo.id = :userId AND t.status != 'DONE'
        ORDER BY t.deadline ASC NULLS LAST, t.priority DESC
        """)
    Page<Task> findMyTasks(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
        SELECT t FROM Task t
        WHERE t.assignedTo.id = :userId OR t.createdBy.id = :userId
        ORDER BY t.deadline ASC NULLS LAST, t.createdAt DESC
        """)
    Page<Task> findEmployeeWorkspaceTasks(@Param("userId") UUID userId, Pageable pageable);

    Optional<Task> findByIdAndTeamId(UUID taskId, UUID teamId);

    @Query("""
        SELECT t FROM Task t
        WHERE t.assignedTo.id = :userId
        AND t.status = 'DONE'
        AND (
            (t.completedAt IS NOT NULL AND t.completedAt >= :start AND t.completedAt < :endExclusive)
            OR (t.completedAt IS NULL AND t.updatedAt >= :start AND t.updatedAt < :endExclusive)
        )
        """)
    List<Task> findCompletedTasksForUserInPeriod(
        @Param("userId") UUID userId,
        @Param("start") LocalDateTime start,
        @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
        SELECT t FROM Task t
        WHERE t.assignedTo.id = :userId
        AND (
            (t.status <> 'DONE' AND t.createdAt < :endExclusive)
            OR (t.status = 'DONE' AND t.completedAt IS NOT NULL AND t.completedAt >= :start AND t.completedAt < :endExclusive)
            OR (t.status = 'DONE' AND t.completedAt IS NULL AND t.updatedAt >= :start AND t.updatedAt < :endExclusive)
        )
        """)
    List<Task> findAssignedTasksForCompletionInPeriod(
        @Param("userId") UUID userId,
        @Param("start") LocalDateTime start,
        @Param("endExclusive") LocalDateTime endExclusive
    );

    @Modifying
    @Query("UPDATE Task t SET t.assignedTo = null WHERE t.assignedTo.id = :userId")
    int clearAssignee(@Param("userId") UUID userId);

    void deleteByAssignedTo_Id(UUID userId);

    @Query("SELECT DISTINCT t FROM Task t WHERE t.assignedTo.department = :department")
    List<Task> findByAssignedTo_Department(@Param("department") String department);

    @Modifying
    @Query("UPDATE Task t SET t.createdBy = :replacement WHERE t.createdBy.id = :userId")
    int reassignCreator(@Param("userId") UUID userId, @Param("replacement") User replacement);

    @Query("SELECT t FROM Task t WHERE t.team.id = :teamId")
    List<Task> findByTeamId(@Param("teamId") UUID teamId);
}
