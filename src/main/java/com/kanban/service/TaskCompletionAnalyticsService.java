package com.kanban.service;

import com.kanban.model.dto.response.TaskCompletionAnalyticsResponse;
import com.kanban.model.dto.response.TaskCompletionAnalyticsResponse.*;
import com.kanban.model.entity.Task;
import com.kanban.model.entity.User;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import com.kanban.repository.TaskRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskCompletionAnalyticsService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TaskCompletionAnalyticsResponse getAnalytics(LocalDate from, LocalDate to, String department) {
        LocalDate periodStart = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate periodEnd = to != null ? to : LocalDate.now();

        List<Task> allTasks = (department != null && !department.isBlank())
            ? taskRepository.findByAssignedTo_Department(department)
            : taskRepository.findAll();

        // Use all tasks for summary metrics (no creation-date window)
        List<Task> periodTasks = allTasks;

        // Calculate summary metrics
        int totalTasks = periodTasks.size();
        int completedTasks = (int) periodTasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.DONE)
            .count();
        int inProgressTasks = (int) periodTasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
            .count();
        int todoTasks = (int) periodTasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.TODO)
            .count();
        int inReviewTasks = (int) periodTasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.IN_REVIEW)
            .count();

        double completionRate = totalTasks > 0 ? round((completedTasks * 100.0) / totalTasks) : 0;

        // Overdue tasks
        LocalDateTime now = LocalDateTime.now();
        int overdueTasks = (int) periodTasks.stream()
            .filter(t -> t.getDeadline() != null)
            .filter(t -> t.getStatus() != TaskStatus.DONE)
            .filter(t -> t.getDeadline().isBefore(now))
            .count();

        // On-time rate (completed tasks that were completed before/on deadline)
        List<Task> completedWithDeadline = periodTasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.DONE)
            .filter(t -> t.getDeadline() != null)
            .toList();
        int onTimeCompleted = (int) completedWithDeadline.stream()
            .filter(t -> !t.getUpdatedAt().isAfter(t.getDeadline()))
            .count();
        double onTimeRate = completedWithDeadline.size() > 0
            ? round((onTimeCompleted * 100.0) / completedWithDeadline.size())
            : 100.0;

        // Average completion time (for completed tasks)
        Double avgCompletionTimeHours = calculateAvgCompletionTime(periodTasks);

        // Breakdowns
        List<StatusBreakdown> byStatus = calculateStatusBreakdown(periodTasks, totalTasks);
        List<PriorityBreakdown> byPriority = calculatePriorityBreakdown(periodTasks);
        List<AssigneeBreakdown> byAssignee = calculateAssigneeBreakdown(periodTasks, now, department);
        List<DailyTrend> dailyTrend = calculateDailyTrend(allTasks, periodStart, periodEnd);
        List<WeeklyTrend> weeklyTrend = calculateWeeklyTrend(allTasks, periodStart, periodEnd);

        return TaskCompletionAnalyticsResponse.builder()
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .totalTasks(totalTasks)
            .completedTasks(completedTasks)
            .inProgressTasks(inProgressTasks)
            .todoTasks(todoTasks)
            .inReviewTasks(inReviewTasks)
            .completionRate(completionRate)
            .onTimeRate(onTimeRate)
            .overdueTasks(overdueTasks)
            .avgCompletionTimeHours(avgCompletionTimeHours)
            .avgTimeInProgressHours(null)
            .byStatus(byStatus)
            .byPriority(byPriority)
            .byAssignee(byAssignee)
            .dailyTrend(dailyTrend)
            .weeklyTrend(weeklyTrend)
            .build();
    }

    private Double calculateAvgCompletionTime(List<Task> tasks) {
        List<Long> completionHours = tasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.DONE)
            .filter(t -> t.getCreatedAt() != null && t.getUpdatedAt() != null)
            .map(t -> ChronoUnit.HOURS.between(t.getCreatedAt(), t.getUpdatedAt()))
            .filter(h -> h >= 0)
            .toList();

        if (completionHours.isEmpty()) {
            return null;
        }

        double avg = completionHours.stream().mapToLong(Long::longValue).average().orElse(0);
        return round(avg);
    }

    private List<StatusBreakdown> calculateStatusBreakdown(List<Task> tasks, int total) {
        Map<TaskStatus, Long> countByStatus = tasks.stream()
            .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));

        return Arrays.stream(TaskStatus.values())
            .map(status -> {
                int count = countByStatus.getOrDefault(status, 0L).intValue();
                double pct = total > 0 ? round((count * 100.0) / total) : 0;
                return StatusBreakdown.builder()
                    .status(status.name())
                    .count(count)
                    .percentage(pct)
                    .build();
            })
            .toList();
    }

    private List<PriorityBreakdown> calculatePriorityBreakdown(List<Task> tasks) {
        Map<TaskPriority, List<Task>> byPriority = tasks.stream()
            .collect(Collectors.groupingBy(Task::getPriority));

        return Arrays.stream(TaskPriority.values())
            .map(priority -> {
                List<Task> priorityTasks = byPriority.getOrDefault(priority, List.of());
                int total = priorityTasks.size();
                int completed = (int) priorityTasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.DONE)
                    .count();
                double rate = total > 0 ? round((completed * 100.0) / total) : 0;
                return PriorityBreakdown.builder()
                    .priority(priority.name())
                    .total(total)
                    .completed(completed)
                    .completionRate(rate)
                    .build();
            })
            .toList();
    }

    private List<AssigneeBreakdown> calculateAssigneeBreakdown(List<Task> tasks, LocalDateTime now, String department) {
        // Build a task map keyed by assignee id.
        Map<UUID, List<Task>> byAssignee = tasks.stream()
            .filter(t -> t.getAssignedTo() != null)
            .collect(Collectors.groupingBy(t -> t.getAssignedTo().getId()));

        // Load ALL active employees (filtered by department if set) so employees with
        // zero tasks still appear in the breakdown rather than being silently omitted.
        List<User> employees = userRepository.findActiveEmployees(
            (department != null && !department.isBlank()) ? department : null
        );

        return employees.stream()
            .map(user -> {
                List<Task> userTasks = byAssignee.getOrDefault(user.getId(), List.of());
                int assigned = userTasks.size();
                int completed = (int) userTasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.DONE)
                    .count();
                int overdue = (int) userTasks.stream()
                    .filter(t -> t.getDeadline() != null)
                    .filter(t -> t.getStatus() != TaskStatus.DONE)
                    .filter(t -> t.getDeadline().isBefore(now))
                    .count();
                double rate = assigned > 0 ? round((completed * 100.0) / assigned) : 0;

                return AssigneeBreakdown.builder()
                    .userId(user.getId().toString())
                    .userName(user.getName())
                    .assigned(assigned)
                    .completed(completed)
                    .completionRate(rate)
                    .overdue(overdue)
                    .build();
            })
            .sorted(Comparator.comparingInt(AssigneeBreakdown::getCompleted).reversed())
            .toList();
    }

    private List<DailyTrend> calculateDailyTrend(List<Task> allTasks, LocalDate from, LocalDate to) {
        List<DailyTrend> trend = new ArrayList<>();
        LocalDate cursor = from;

        while (!cursor.isAfter(to)) {
            LocalDate date = cursor;
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            int created = (int) allTasks.stream()
                .filter(t -> !t.getCreatedAt().isBefore(dayStart) && t.getCreatedAt().isBefore(dayEnd))
                .count();

            int completed = (int) allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .filter(t -> t.getUpdatedAt() != null)
                .filter(t -> !t.getUpdatedAt().isBefore(dayStart) && t.getUpdatedAt().isBefore(dayEnd))
                .count();

            trend.add(DailyTrend.builder()
                .date(date)
                .created(created)
                .completed(completed)
                .netChange(created - completed)
                .build());

            cursor = cursor.plusDays(1);
        }

        return trend;
    }

    private List<WeeklyTrend> calculateWeeklyTrend(List<Task> allTasks, LocalDate from, LocalDate to) {
        List<WeeklyTrend> trend = new ArrayList<>();

        LocalDate weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekNum = 1;

        while (!weekStart.isAfter(to)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(to)) {
                weekEnd = to;
            }

            LocalDateTime start = weekStart.atStartOfDay();
            LocalDateTime end = weekEnd.plusDays(1).atStartOfDay();

            int created = (int) allTasks.stream()
                .filter(t -> !t.getCreatedAt().isBefore(start) && t.getCreatedAt().isBefore(end))
                .count();

            int completed = (int) allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .filter(t -> t.getUpdatedAt() != null)
                .filter(t -> !t.getUpdatedAt().isBefore(start) && t.getUpdatedAt().isBefore(end))
                .count();

            double rate = created > 0 ? round((completed * 100.0) / created) : 0;

            trend.add(WeeklyTrend.builder()
                .weekStart(weekStart)
                .weekLabel("W" + weekNum)
                .created(created)
                .completed(completed)
                .completionRate(rate)
                .build());

            weekStart = weekStart.plusWeeks(1);
            weekNum++;
        }

        return trend;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
