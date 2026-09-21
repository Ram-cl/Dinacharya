package com.kanban.controller;

import com.kanban.model.dto.response.TaskCompletionAnalyticsResponse;
import com.kanban.service.TaskCompletionAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/analytics/tasks")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Task Completion Analytics", description = "Task completion metrics and trends (admin only)")
public class TaskCompletionAnalyticsController {

    private final TaskCompletionAnalyticsService analyticsService;

    @GetMapping
    @Operation(summary = "Get task completion analytics", 
               description = "Returns completion rates, trends, and breakdowns by status, priority, and assignee")
    public ResponseEntity<TaskCompletionAnalyticsResponse> getAnalytics(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String department
    ) {
        return ResponseEntity.ok(analyticsService.getAnalytics(from, to, department));
    }
}
