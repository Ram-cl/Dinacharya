package com.kanban.controller;

import com.kanban.model.dto.response.EmployeePerformanceOverviewResponse;
import com.kanban.model.dto.response.EmployeePerformanceResponse;
import com.kanban.model.dto.response.EmployeePerformanceTrendResponse;
import com.kanban.service.EmployeePerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/analytics/performance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Performance Analytics", description = "Employee performance analytics (admin only)")
public class PerformanceAnalyticsController {

    private final EmployeePerformanceService employeePerformanceService;

    @GetMapping("/employees")
    @Operation(summary = "Get employee performance overview for a period")
    public ResponseEntity<EmployeePerformanceOverviewResponse> getOverview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String department
    ) {
        return ResponseEntity.ok(employeePerformanceService.getOverview(from, to, department));
    }

    @GetMapping("/employees/{userId}")
    @Operation(summary = "Get performance details for a single employee")
    public ResponseEntity<EmployeePerformanceResponse> getEmployee(
        @PathVariable UUID userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(employeePerformanceService.getEmployee(userId, from, to));
    }

    @GetMapping("/employees/{userId}/trend")
    @Operation(summary = "Get performance trend for an employee")
    public ResponseEntity<EmployeePerformanceTrendResponse> getTrend(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "6") int months
    ) {
        return ResponseEntity.ok(employeePerformanceService.getTrend(userId, months));
    }

    @PostMapping("/compute")
    @Operation(summary = "Recompute performance snapshots for the current month")
    public ResponseEntity<Void> computeNow() {
        employeePerformanceService.computeCurrentMonthSnapshots();
        return ResponseEntity.noContent().build();
    }
}
