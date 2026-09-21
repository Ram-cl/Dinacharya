package com.kanban.mapper;

import com.kanban.model.dto.response.TimeEntryResponse;
import com.kanban.model.entity.TimeEntry;
import com.kanban.repository.TimeEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class TimeEntryMapper {

    private final UserMapper userMapper;
    private final TimeEntryRepository timeEntryRepository;

    public TimeEntryResponse toResponse(TimeEntry timeEntry) {
        if (timeEntry == null) {
            return null;
        }

        // Calculate weekly average
        LocalDate weekStart = timeEntry.getEntryDate().minusDays(6);
        Double weeklyAvg = timeEntryRepository.calculateAverageHours(
            timeEntry.getUser().getId(), 
            weekStart, 
            timeEntry.getEntryDate()
        );

        return TimeEntryResponse.builder()
                .id(timeEntry.getId())
                .user(userMapper.toResponse(timeEntry.getUser()))
                .entryDate(timeEntry.getEntryDate())
                .entryTime(timeEntry.getEntryTime())
                .exitTime(timeEntry.getExitTime())
                .hoursWorked(timeEntry.getHoursWorked())
                .status(timeEntry.getStatus())
                .breakDurationMinutes(timeEntry.getBreakDurationMinutes())
                .remark(timeEntry.getRemark())
                .weeklyAverage(weeklyAvg != null ? Math.round(weeklyAvg * 100.0) / 100.0 : null)
                .build();
    }
}
