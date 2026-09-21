package com.kanban.model.dto.request;

import com.kanban.model.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
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
public class CreateTimeEntryRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Entry date is required")
    private LocalDate entryDate;
    
    private LocalTime entryTime;
    
    private LocalTime exitTime;
    
    private Double hoursWorked;
    
    private AttendanceStatus status;
    
    private Integer breakDurationMinutes;
    
    private String remark;
}
