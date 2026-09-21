package com.kanban.model.dto.request;

import com.kanban.model.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTimeEntryRequest {
    
    private LocalTime entryTime;
    
    private LocalTime exitTime;
    
    private Double hoursWorked;
    
    private AttendanceStatus status;
    
    private Integer breakDurationMinutes;
    
    private String remark;
}
