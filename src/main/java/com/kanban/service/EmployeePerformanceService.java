package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.model.dto.response.EmployeePerformanceOverviewResponse;
import com.kanban.model.dto.response.EmployeePerformanceResponse;
import com.kanban.model.dto.response.EmployeePerformanceTrendResponse;
import com.kanban.model.entity.AttendanceRecord;
import com.kanban.model.entity.EmployeePerformanceSnapshot;
import com.kanban.model.entity.Task;
import com.kanban.model.entity.User;
import com.kanban.model.enums.TaskPriority;
import com.kanban.repository.AttendanceRecordRepository;
import com.kanban.repository.EmployeePerformanceSnapshotRepository;
import com.kanban.repository.TaskRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeePerformanceService {

    private static final double PRODUCTIVITY_WEIGHT = 0.4;
    private static final double COMPLETION_WEIGHT = 0.4;
    private static final double EFFICIENCY_WEIGHT = 0.2;

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeePerformanceSnapshotRepository snapshotRepository;

    @Transactional
    public void computeSnapshotsForPeriod(LocalDate periodStart, LocalDate periodEnd) {
        List<User> employees = userRepository.findActiveEmployees(null);
        Map<UUID, RawScores> rawScoresByUser = new HashMap<>();

        for (User employee : employees) {
            rawScoresByUser.put(employee.getId(), calculateRawScores(employee, periodStart, periodEnd));
        }

        double teamAverageProductivity = rawScoresByUser.values().stream()
            .mapToDouble(RawScores::productivityRaw)
            .average()
            .orElse(0.0);

        for (User employee : employees) {
            RawScores raw = rawScoresByUser.get(employee.getId());
            double productivity = normalizeProductivity(raw.productivityRaw(), teamAverageProductivity);
            double completion = raw.completionScore();
            double efficiency = raw.efficiencyScore();
            double performanceIndex = round(
                PRODUCTIVITY_WEIGHT * productivity
                    + COMPLETION_WEIGHT * completion
                    + EFFICIENCY_WEIGHT * efficiency
            );

            snapshotRepository.deleteByUserIdAndPeriodStartAndPeriodEnd(
                employee.getId(),
                periodStart,
                periodEnd
            );

            EmployeePerformanceSnapshot snapshot = EmployeePerformanceSnapshot.builder()
                .user(employee)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .productivityScore(productivity)
                .efficiencyScore(efficiency)
                .disciplineScore(completion)
                .performanceIndex(performanceIndex)
                .tasksCompleted(raw.tasksCompleted())
                .tasksAssigned(raw.tasksAssigned())
                .onTimeTasks(raw.onTimeTasks())
                .attendanceDays(raw.attendanceDays())
                .workingDays(raw.workingDays())
                .build();

            snapshot.setRollingIndex(calculateRollingIndex(employee.getId(), periodStart, performanceIndex));
            snapshotRepository.save(snapshot);
        }
    }

    @Transactional
    public void computeCurrentMonthSnapshots() {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.with(TemporalAdjusters.firstDayOfMonth());
        computeSnapshotsForPeriod(periodStart, today);
    }

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void scheduleMonthlyPerformanceComputation() {
        try {
            log.info("Starting scheduled monthly performance computation");
            computeCurrentMonthSnapshots();
            log.info("Monthly performance computation completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled monthly performance computation", e);
        }
    }

    @Transactional
    public EmployeePerformanceOverviewResponse getOverview(LocalDate from, LocalDate to, String department) {
        LocalDate periodStart = from != null ? from : LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate periodEnd = to != null ? to : LocalDate.now();

        List<EmployeePerformanceSnapshot> snapshots = snapshotRepository.findByPeriod(periodStart, periodEnd, department);
        if (snapshots.isEmpty()) {
            computeSnapshotsForPeriod(periodStart, periodEnd);
            snapshots = snapshotRepository.findByPeriod(periodStart, periodEnd, department);
        }

        return EmployeePerformanceOverviewResponse.builder()
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .employees(snapshots.stream().map(this::toResponse).toList())
            .build();
    }

    @Transactional
    public EmployeePerformanceResponse getEmployee(UUID userId, LocalDate from, LocalDate to) {
        LocalDate periodStart = from != null ? from : LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate periodEnd = to != null ? to : LocalDate.now();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return snapshotRepository.findByUserIdAndPeriodStartAndPeriodEnd(userId, periodStart, periodEnd)
            .map(this::toResponse)
            .orElseGet(() -> buildEmployeeResponse(user, periodStart, periodEnd));
    }

    private EmployeePerformanceResponse buildEmployeeResponse(User user, LocalDate periodStart, LocalDate periodEnd) {
        List<User> employees = userRepository.findActiveEmployees(null);
        Map<UUID, RawScores> rawScoresByUser = new HashMap<>();
        for (User employee : employees) {
            rawScoresByUser.put(employee.getId(), calculateRawScores(employee, periodStart, periodEnd));
        }

        double teamAverageProductivity = rawScoresByUser.values().stream()
            .mapToDouble(RawScores::productivityRaw)
            .average()
            .orElse(0.0);

        RawScores raw = rawScoresByUser.getOrDefault(
            user.getId(),
            calculateRawScores(user, periodStart, periodEnd)
        );

        double productivity = normalizeProductivity(raw.productivityRaw(), teamAverageProductivity);
        double completion = raw.completionScore();
        double efficiency = raw.efficiencyScore();
        double performanceIndex = round(
            PRODUCTIVITY_WEIGHT * productivity
                + COMPLETION_WEIGHT * completion
                + EFFICIENCY_WEIGHT * efficiency
        );

        return EmployeePerformanceResponse.builder()
            .userId(user.getId())
            .userName(user.getName())
            .department(user.getDepartment())
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .productivityScore(productivity)
            .efficiencyScore(efficiency)
            .disciplineScore(completion)
            .performanceIndex(performanceIndex)
            .rollingIndex(calculateRollingIndex(user.getId(), periodStart, performanceIndex))
            .tasksCompleted(raw.tasksCompleted())
            .tasksAssigned(raw.tasksAssigned())
            .onTimeTasks(raw.onTimeTasks())
            .attendanceDays(raw.attendanceDays())
            .workingDays(raw.workingDays())
            .build();
    }

    @Transactional(readOnly = true)
    public EmployeePerformanceTrendResponse getTrend(UUID userId, int months) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        LocalDate from = LocalDate.now().minusMonths(Math.max(months, 1) - 1L)
            .with(TemporalAdjusters.firstDayOfMonth());

        List<EmployeePerformanceSnapshot> snapshots = snapshotRepository.findTrendByUser(userId, from);
        return EmployeePerformanceTrendResponse.builder()
            .userId(user.getId())
            .userName(user.getName())
            .points(snapshots.stream()
                .map(snapshot -> EmployeePerformanceTrendResponse.TrendPoint.builder()
                    .periodStart(snapshot.getPeriodStart())
                    .periodEnd(snapshot.getPeriodEnd())
                    .productivityScore(snapshot.getProductivityScore())
                    .efficiencyScore(snapshot.getEfficiencyScore())
                    .disciplineScore(snapshot.getDisciplineScore())
                    .performanceIndex(snapshot.getPerformanceIndex())
                    .rollingIndex(snapshot.getRollingIndex())
                    .build())
                .toList())
            .build();
    }

    RawScores calculateRawScores(User employee, LocalDate periodStart, LocalDate periodEnd) {
        // Clamp period start to the employee's joining date so working-day count only
        // includes days from when they actually started — not before their onboarding.
        LocalDate joiningDate = employee.getJoiningDate() != null
            ? employee.getJoiningDate()
            : employee.getCreatedAt().toLocalDate();
        LocalDate effectiveStart = joiningDate.isAfter(periodStart) ? joiningDate : periodStart;

        LocalDateTime start = effectiveStart.atStartOfDay();
        LocalDateTime endExclusive = periodEnd.plusDays(1).atStartOfDay();

        List<Task> completedTasks = taskRepository.findCompletedTasksForUserInPeriod(
            employee.getId(),
            start,
            endExclusive
        );

        List<Task> assignedTasks = taskRepository.findAssignedTasksForCompletionInPeriod(
            employee.getId(),
            start,
            endExclusive
        );

        int tasksCompleted = completedTasks.size();
        int tasksAssigned = assignedTasks.size();

        double productivityRaw = completedTasks.stream()
            .mapToDouble(task -> priorityWeight(task.getPriority()))
            .sum();

        int tasksWithDeadline = 0;
        int onTimeTasks = 0;
        for (Task task : completedTasks) {
            if (task.getDeadline() == null) {
                continue;
            }
            tasksWithDeadline++;
            if (!task.getUpdatedAt().isAfter(task.getDeadline())) {
                onTimeTasks++;
            }
        }

        double efficiencyScore = efficiencyScore(tasksCompleted, tasksWithDeadline, onTimeTasks);
        double completionScore = completionRate(tasksCompleted, tasksAssigned);

        List<AttendanceRecord> attendance = attendanceRecordRepository.findByUserIdAndWorkDateBetween(
            employee.getId(),
            effectiveStart,
            periodEnd
        );
        int attendanceDays = (int) attendance.stream()
            .filter(record -> record.getEntryTime() != null)
            .count();

        return new RawScores(
            productivityRaw,
            efficiencyScore,
            completionScore,
            tasksCompleted,
            tasksAssigned,
            onTimeTasks,
            attendanceDays,
            workingDaysBetween(effectiveStart, periodEnd).size()
        );
    }

    static double priorityWeight(TaskPriority priority) {
        return switch (priority) {
            case LOW -> 1.0;
            case MEDIUM -> 2.0;
            case HIGH -> 3.0;
            case URGENT -> 4.0;
        };
    }

    static double normalizeProductivity(double raw, double teamAverage) {
        if (raw <= 0) {
            return 0.0;
        }
        if (teamAverage <= 0) {
            return 100.0;
        }
        return round(Math.min(100.0, (raw / teamAverage) * 100.0));
    }

    static double completionRate(int tasksCompleted, int tasksAssigned) {
        if (tasksAssigned <= 0) {
            return 0.0;
        }
        return round((tasksCompleted * 100.0) / tasksAssigned);
    }

    static double efficiencyScore(int tasksCompleted, int tasksWithDeadline, int onTimeTasks) {
        if (tasksCompleted <= 0) {
            return 0.0;
        }
        if (tasksWithDeadline <= 0) {
            return 100.0;
        }
        return round((onTimeTasks * 100.0) / tasksWithDeadline);
    }

    static List<LocalDate> workingDaysBetween(LocalDate start, LocalDate end) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            DayOfWeek dayOfWeek = cursor.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                days.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    private Double calculateRollingIndex(UUID userId, LocalDate currentPeriodStart, double currentIndex) {
        List<EmployeePerformanceSnapshot> previous = snapshotRepository
            .findRecentSnapshotsBefore(userId, currentPeriodStart)
            .stream()
            .sorted(Comparator.comparing(EmployeePerformanceSnapshot::getPeriodStart).reversed())
            .limit(2)
            .toList();

        List<Double> values = new ArrayList<>();
        values.add(currentIndex);
        previous.forEach(snapshot -> values.add(snapshot.getPerformanceIndex()));

        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(currentIndex));
    }

    private EmployeePerformanceResponse toResponse(EmployeePerformanceSnapshot snapshot) {
        User user = snapshot.getUser();
        return EmployeePerformanceResponse.builder()
            .userId(user.getId())
            .userName(user.getName())
            .department(user.getDepartment())
            .periodStart(snapshot.getPeriodStart())
            .periodEnd(snapshot.getPeriodEnd())
            .productivityScore(snapshot.getProductivityScore())
            .efficiencyScore(snapshot.getEfficiencyScore())
            .disciplineScore(snapshot.getDisciplineScore())
            .performanceIndex(snapshot.getPerformanceIndex())
            .rollingIndex(snapshot.getRollingIndex())
            .tasksCompleted(snapshot.getTasksCompleted())
            .tasksAssigned(snapshot.getTasksAssigned())
            .onTimeTasks(snapshot.getOnTimeTasks())
            .attendanceDays(snapshot.getAttendanceDays())
            .workingDays(snapshot.getWorkingDays())
            .build();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    record RawScores(
        double productivityRaw,
        double efficiencyScore,
        double completionScore,
        int tasksCompleted,
        int tasksAssigned,
        int onTimeTasks,
        int attendanceDays,
        int workingDays
    ) {}
}
