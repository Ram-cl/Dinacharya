package com.kanban.model.dto.response;

import com.kanban.model.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeEntryResponse {
    private UUID id;
    private UserResponse user;
    private LocalDate entryDate;
    private LocalTime entryTime;
    private LocalTime exitTime;
    private Double hoursWorked;
    private AttendanceStatus status;
    private Integer breakDurationMinutes;
    private String remark;
    private Double weeklyAverage;
}
