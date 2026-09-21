package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.model.dto.request.AttendanceBreakRequest;
import com.kanban.model.dto.request.CreateAttendanceRequest;
import com.kanban.model.dto.request.UpdateAttendanceRequest;
import com.kanban.model.dto.response.AttendanceBreakResponse;
import com.kanban.model.dto.response.AttendanceRecordResponse;
import com.kanban.model.entity.AttendanceBreak;
import com.kanban.model.entity.AttendanceRecord;
import com.kanban.model.entity.User;
import com.kanban.model.enums.AttendanceStatus;
import com.kanban.repository.AttendanceRecordRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public Page<AttendanceRecordResponse> getAttendanceRecords(
        LocalDate workDate,
        String department,
        AttendanceStatus status,
        String search,
        Pageable pageable
    ) {
        LocalDate date = workDate != null ? workDate : LocalDate.now();
        String searchTerm = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();
        String departmentTerm = (department == null || department.isBlank()) ? null : department.trim();

        List<User> employees = userRepository.findActiveEmployees(departmentTerm);
        Map<UUID, AttendanceRecord> recordsByUser = attendanceRecordRepository.findByWorkDate(date).stream()
            .collect(Collectors.toMap(record -> record.getUser().getId(), record -> record, (a, b) -> a));

        List<AttendanceRecordResponse> roster = employees.stream()
            .filter(user -> matchesSearch(user, searchTerm))
            .map(user -> {
                AttendanceRecord record = recordsByUser.get(user.getId());
                return record != null ? toResponse(record) : placeholderFor(user, date);
            })
            .filter(response -> status == null || response.getStatus() == status)
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), roster.size());
        List<AttendanceRecordResponse> pageContent = start >= roster.size() ? List.of() : roster.subList(start, end);
        return new PageImpl<>(pageContent, pageable, roster.size());
    }

    private boolean matchesSearch(User user, String searchTerm) {
        if (searchTerm == null) {
            return true;
        }
        String haystack = String.join(" ",
            nullToEmpty(user.getName()),
            nullToEmpty(user.getEmail()),
            nullToEmpty(user.getDepartment()),
            nullToEmpty(user.getProfessionalRole())
        ).toLowerCase();
        return haystack.contains(searchTerm);
    }

    private AttendanceRecordResponse placeholderFor(User user, LocalDate workDate) {
        int weeklyAvg = calculateWeeklyAvgMinutes(user.getId(), workDate);
        return AttendanceRecordResponse.builder()
            .userId(user.getId())
            .memberName(user.getName())
            .memberEmail(user.getEmail())
            .department(user.getDepartment())
            .profilePicture(user.getProfilePicture())
            .workDate(workDate)
            .status(AttendanceStatus.ABSENT)
            .hoursToday("00:00")
            .hoursTodayMinutes(0)
            .weeklyAvgHours(formatWeeklyHours(weeklyAvg))
            .weeklyAvgMinutes(weeklyAvg)
            .breaks(List.of())
            .build();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Transactional
    public AttendanceRecordResponse createAttendance(CreateAttendanceRequest request) {
        User user = userService.getUserEntityById(request.getUserId());
        LocalDate workDate = request.getWorkDate() != null ? request.getWorkDate() : LocalDate.now();

        attendanceRecordRepository.findByUserIdAndWorkDate(user.getId(), workDate)
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Attendance record already exists for this member on " + workDate);
            });

        AttendanceRecord record = AttendanceRecord.builder()
            .user(user)
            .workDate(workDate)
            .entryTime(request.getEntryTime())
            .exitTime(request.getExitTime())
            .status(resolveStatus(request.getStatus(), request.getEntryTime(), request.getExitTime(), request.getBreaks()))
            .breaks(new ArrayList<>())
            .build();

        if (request.getBreaks() != null) {
            for (AttendanceBreakRequest breakRequest : request.getBreaks()) {
                if (breakRequest.getStartTime() != null) {
                    AttendanceBreak attendanceBreak = AttendanceBreak.builder()
                        .attendanceRecord(record)
                        .startTime(breakRequest.getStartTime())
                        .endTime(breakRequest.getEndTime())
                        .build();
                    record.getBreaks().add(attendanceBreak);
                }
            }
        }

        record = attendanceRecordRepository.save(record);
        return toResponse(record);
    }

    @Transactional
    public AttendanceRecordResponse updateAttendance(UUID id, UpdateAttendanceRequest request) {
        AttendanceRecord record = getRecordEntity(id);

        if (request.getEntryTime() != null) {
            record.setEntryTime(request.getEntryTime());
        }
        if (request.getExitTime() != null) {
            record.setExitTime(request.getExitTime());
        }
        if (request.getStatus() != null) {
            record.setStatus(toStoredStatus(request.getStatus(), record.getEntryTime()));
        } else {
            record.setStatus(resolveStatus(null, record.getEntryTime(), record.getExitTime(), null, record));
        }

        if (request.getBreaks() != null) {
            record.getBreaks().clear();
            for (AttendanceBreakRequest breakRequest : request.getBreaks()) {
                if (breakRequest.getStartTime() != null) {
                    AttendanceBreak attendanceBreak = AttendanceBreak.builder()
                        .attendanceRecord(record)
                        .startTime(breakRequest.getStartTime())
                        .endTime(breakRequest.getEndTime())
                        .build();
                    record.getBreaks().add(attendanceBreak);
                }
            }
            record.setStatus(toStoredStatus(record.getStatus(), record.getEntryTime()));
        }

        record = attendanceRecordRepository.save(record);
        return toResponse(record);
    }

    @Transactional
    public void deleteAttendance(UUID id) {
        AttendanceRecord record = getRecordEntity(id);
        attendanceRecordRepository.delete(record);
    }

    private AttendanceRecord getRecordEntity(UUID id) {
        return attendanceRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with id: " + id));
    }

    private AttendanceStatus resolveStatus(
        AttendanceStatus requested,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        List<AttendanceBreakRequest> breaks
    ) {
        if (requested != null) {
            return toStoredStatus(requested, entryTime);
        }
        if (entryTime != null) {
            return AttendanceStatus.ONLINE;
        }
        return AttendanceStatus.OFFLINE;
    }

    private AttendanceStatus resolveStatus(
        AttendanceStatus requested,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        List<AttendanceBreakRequest> breaks,
        AttendanceRecord record
    ) {
        if (requested != null) {
            return toStoredStatus(requested, entryTime);
        }
        if (entryTime != null) {
            return AttendanceStatus.ONLINE;
        }
        return AttendanceStatus.OFFLINE;
    }

    private AttendanceStatus toStoredStatus(AttendanceStatus status, LocalDateTime entryTime) {
        return toTeamStatus(status, entryTime) == AttendanceStatus.PRESENT
            ? AttendanceStatus.ONLINE
            : AttendanceStatus.OFFLINE;
    }

    private AttendanceStatus toTeamStatus(AttendanceStatus status, LocalDateTime entryTime) {
        if (status == AttendanceStatus.PRESENT
            || status == AttendanceStatus.ONLINE
            || status == AttendanceStatus.ON_BREAK
            || status == AttendanceStatus.WORK_FROM_HOME
            || status == AttendanceStatus.HALF_DAY) {
            return AttendanceStatus.PRESENT;
        }
        if (status == AttendanceStatus.ABSENT
            || status == AttendanceStatus.LEAVE
            || status == AttendanceStatus.OFFLINE
            || status == AttendanceStatus.AWAY) {
            return AttendanceStatus.ABSENT;
        }
        return entryTime != null ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord record) {
        User user = record.getUser();
        int hoursTodayMinutes = calculateWorkedMinutes(record);
        int weeklyAvgMinutes = calculateWeeklyAvgMinutes(user.getId(), record.getWorkDate());

        List<AttendanceBreakResponse> breakResponses = record.getBreaks().stream()
            .map(this::toBreakResponse)
            .toList();

        return AttendanceRecordResponse.builder()
            .id(record.getId())
            .userId(user.getId())
            .memberName(user.getName())
            .memberEmail(user.getEmail())
            .department(user.getDepartment())
            .profilePicture(user.getProfilePicture())
            .workDate(record.getWorkDate())
            .entryTime(record.getEntryTime())
            .exitTime(record.getExitTime())
            .status(toTeamStatus(record.getStatus(), record.getEntryTime()))
            .hoursToday(formatDuration(hoursTodayMinutes))
            .hoursTodayMinutes(hoursTodayMinutes)
            .weeklyAvgHours(formatWeeklyHours(weeklyAvgMinutes))
            .weeklyAvgMinutes(weeklyAvgMinutes)
            .breaks(breakResponses)
            .createdAt(record.getCreatedAt())
            .updatedAt(record.getUpdatedAt())
            .build();
    }

    private AttendanceBreakResponse toBreakResponse(AttendanceBreak attendanceBreak) {
        int minutes = 0;
        if (attendanceBreak.getStartTime() != null && attendanceBreak.getEndTime() != null) {
            minutes = (int) Duration.between(attendanceBreak.getStartTime(), attendanceBreak.getEndTime()).toMinutes();
        }
        return AttendanceBreakResponse.builder()
            .id(attendanceBreak.getId())
            .startTime(attendanceBreak.getStartTime())
            .endTime(attendanceBreak.getEndTime())
            .duration(formatDuration(minutes))
            .build();
    }

    private int calculateWorkedMinutes(AttendanceRecord record) {
        if (record.getEntryTime() == null) {
            return 0;
        }

        LocalDateTime end = record.getExitTime() != null ? record.getExitTime() : LocalDateTime.now();
        if (end.isBefore(record.getEntryTime())) {
            return 0;
        }

        int totalMinutes = (int) Duration.between(record.getEntryTime(), end).toMinutes();

        for (AttendanceBreak attendanceBreak : record.getBreaks()) {
            if (attendanceBreak.getStartTime() != null && attendanceBreak.getEndTime() != null) {
                totalMinutes -= (int) Duration.between(
                    attendanceBreak.getStartTime(),
                    attendanceBreak.getEndTime()
                ).toMinutes();
            }
        }

        return Math.max(0, totalMinutes);
    }

    private int calculateWeeklyAvgMinutes(UUID userId, LocalDate workDate) {
        LocalDate startOfWeek = workDate.minusDays(6);
        List<AttendanceRecord> weekRecords = attendanceRecordRepository
            .findByUserIdAndWorkDateBetween(userId, startOfWeek, workDate);

        if (weekRecords.isEmpty()) {
            return 0;
        }

        int totalMinutes = weekRecords.stream()
            .mapToInt(this::calculateWorkedMinutes)
            .sum();

        return totalMinutes / weekRecords.size();
    }

    private String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "00:00";
        }
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    private String formatWeeklyHours(int avgMinutes) {
        if (avgMinutes <= 0) {
            return "0h";
        }
        int hours = avgMinutes / 60;
        return hours + "h";
    }
}
