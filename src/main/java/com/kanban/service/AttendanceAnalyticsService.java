package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.exception.UnauthorizedException;
import com.kanban.model.dto.response.EmployeeAttendanceDashboardResponse;
import com.kanban.model.entity.AttendanceRecord;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.AttendanceRecordRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceAnalyticsService {

    private static final LocalTime LATE_CUTOFF = LocalTime.of(9, 30);
    private static final DateTimeFormatter DAY_LABEL_FORMAT =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL_FORMAT =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final UserRepository userRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Transactional(readOnly = true)
    public EmployeeAttendanceDashboardResponse getDashboard(
        UUID userId,
        UUID currentUserId,
        UserRole currentUserRole,
        LocalDate from,
        LocalDate to
    ) {
        if (!canView(userId, currentUserId, currentUserRole)) {
            throw new UnauthorizedException("You can only view your own attendance dashboard");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        LocalDate periodStart = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate periodEnd = to != null ? to : LocalDate.now();
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }

        // Clamp period start to the employee's joining date so working-day denominator
        // only counts days from when they were actually onboarded.
        LocalDate joiningDate = user.getJoiningDate() != null
            ? user.getJoiningDate()
            : user.getCreatedAt().toLocalDate();
        if (joiningDate.isAfter(periodStart)) {
            periodStart = joiningDate;
        }

        List<AttendanceRecord> records = attendanceRecordRepository.findByUserIdAndWorkDateBetween(
            userId,
            periodStart,
            periodEnd
        );
        Map<LocalDate, AttendanceRecord> recordsByDate = records.stream()
            .collect(Collectors.toMap(AttendanceRecord::getWorkDate, record -> record, (a, b) -> a));

        List<LocalDate> workingDays = workingDaysBetween(periodStart, periodEnd);
        Map<YearMonth, List<DayView>> daysByMonth = new LinkedHashMap<>();

        int presentCount = 0;
        int absentCount = 0;
        int lateCount = 0;

        for (LocalDate day : workingDays) {
            DayView dayView = classifyDay(day, recordsByDate.get(day));
            if ("PRESENT".equals(dayView.status())) {
                presentCount++;
            } else if ("LATE".equals(dayView.status())) {
                presentCount++;
                lateCount++;
            } else {
                absentCount++;
            }

            YearMonth month = YearMonth.from(day);
            daysByMonth.computeIfAbsent(month, ignored -> new ArrayList<>()).add(dayView);
        }

        int totalSessions = workingDays.size();
        double overallPercent = totalSessions == 0
            ? 0.0
            : round((presentCount * 100.0) / totalSessions);

        List<EmployeeAttendanceDashboardResponse.MonthSummary> months = daysByMonth.entrySet()
            .stream()
            .sorted(Map.Entry.<YearMonth, List<DayView>>comparingByKey().reversed())
            .map(entry -> toMonthSummary(entry.getKey(), entry.getValue()))
            .toList();

        return EmployeeAttendanceDashboardResponse.builder()
            .userId(user.getId())
            .userName(user.getName())
            .department(user.getDepartment())
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .overallPercent(overallPercent)
            .totalSessions(totalSessions)
            .presentCount(presentCount)
            .absentCount(absentCount)
            .lateCount(lateCount)
            .months(months)
            .build();
    }

    private EmployeeAttendanceDashboardResponse.MonthSummary toMonthSummary(
        YearMonth month,
        List<DayView> days
    ) {
        List<DayView> sortedDays = days.stream()
            .sorted(Comparator.comparing(DayView::workDate).reversed())
            .toList();

        int present = 0;
        int absent = 0;
        int late = 0;
        for (DayView day : sortedDays) {
            switch (day.status()) {
                case "PRESENT" -> present++;
                case "LATE" -> {
                    present++;
                    late++;
                }
                default -> absent++;
            }
        }

        int total = sortedDays.size();
        double percent = total == 0 ? 0.0 : round((present * 100.0) / total);

        return EmployeeAttendanceDashboardResponse.MonthSummary.builder()
            .monthKey(month.toString())
            .monthLabel(month.atDay(1).format(MONTH_LABEL_FORMAT))
            .attendancePercent(percent)
            .presentCount(present)
            .absentCount(absent)
            .lateCount(late)
            .totalSessions(total)
            .days(sortedDays.stream().map(this::toDayEntry).toList())
            .build();
    }

    private EmployeeAttendanceDashboardResponse.DayEntry toDayEntry(DayView day) {
        return EmployeeAttendanceDashboardResponse.DayEntry.builder()
            .workDate(day.workDate())
            .dayLabel(day.workDate().format(DAY_LABEL_FORMAT))
            .status(day.status())
            .note(day.note())
            .build();
    }

    private DayView classifyDay(LocalDate day, AttendanceRecord record) {
        if (record == null || record.getEntryTime() == null) {
            return new DayView(day, "ABSENT", "Absent");
        }

        LocalTime entryTime = record.getEntryTime().toLocalTime();
        if (entryTime.isAfter(LATE_CUTOFF)) {
            return new DayView(day, "LATE", "Late check-in");
        }

        return new DayView(day, "PRESENT", "Present");
    }

    private static boolean canView(UUID targetUserId, UUID currentUserId, UserRole currentUserRole) {
        if (currentUserRole == UserRole.ADMIN) {
            return true;
        }
        return targetUserId.equals(currentUserId);
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

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record DayView(LocalDate workDate, String status, String note) {}
}
