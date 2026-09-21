package com.kanban.controller;

import com.kanban.model.dto.request.CreateAttendanceRequest;
import com.kanban.model.dto.request.CreateDepartmentRequest;
import com.kanban.model.dto.request.CreateMemberRequest;
import com.kanban.model.dto.request.UpdateAttendanceRequest;
import com.kanban.model.dto.response.AttendanceRecordResponse;
import com.kanban.model.dto.response.CommentResponse;
import com.kanban.model.dto.response.DepartmentResponse;
import com.kanban.model.dto.response.UserResponse;
import com.kanban.model.enums.AttendanceStatus;
import com.kanban.security.CustomUserDetailsService;
import com.kanban.service.AttendanceService;
import com.kanban.service.CommentService;
import com.kanban.service.DepartmentService;
import com.kanban.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/moderator")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Moderator", description = "Admin APIs (admin only)")
public class ModeratorController {

    private final CommentService commentService;
    private final AttendanceService attendanceService;
    private final UserService userService;
    private final DepartmentService departmentService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping("/flagged-comments")
    @Operation(summary = "Get all flagged comments")
    public ResponseEntity<Page<CommentResponse>> getFlaggedComments(Pageable pageable) {
        Page<CommentResponse> comments = commentService.getFlaggedComments(pageable);
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/comments/{id}/flag")
    @Operation(summary = "Flag a comment for moderation")
    public ResponseEntity<CommentResponse> flagComment(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        CommentResponse comment = commentService.flagComment(id, user.getId());
        return ResponseEntity.ok(comment);
    }

    @PostMapping("/comments/{id}/resolve")
    @Operation(summary = "Resolve a flagged comment")
    public ResponseEntity<CommentResponse> resolveComment(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        CommentResponse comment = commentService.resolveComment(id, user.getId());
        return ResponseEntity.ok(comment);
    }

    @GetMapping("/flagged-comments/count")
    @Operation(summary = "Get count of flagged comments")
    public ResponseEntity<Long> getFlaggedCommentsCount() {
        long count = commentService.countFlaggedComments();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/attendance")
    @Operation(summary = "Get team attendance records")
    public ResponseEntity<Page<AttendanceRecordResponse>> getAttendanceRecords(
        @RequestParam(required = false) LocalDate date,
        @RequestParam(required = false) String department,
        @RequestParam(required = false) AttendanceStatus status,
        @RequestParam(required = false) String search,
        Pageable pageable
    ) {
        Page<AttendanceRecordResponse> records = attendanceService.getAttendanceRecords(
            date, department, status, search, pageable
        );
        return ResponseEntity.ok(records);
    }

    @PostMapping("/attendance")
    @Operation(summary = "Create attendance record for a member")
    public ResponseEntity<AttendanceRecordResponse> createAttendance(
        @Valid @RequestBody CreateAttendanceRequest request
    ) {
        AttendanceRecordResponse record = attendanceService.createAttendance(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(record);
    }

    @PutMapping("/attendance/{id}")
    @Operation(summary = "Update attendance record")
    public ResponseEntity<AttendanceRecordResponse> updateAttendance(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateAttendanceRequest request
    ) {
        AttendanceRecordResponse record = attendanceService.updateAttendance(id, request);
        return ResponseEntity.ok(record);
    }

    @DeleteMapping("/attendance/{id}")
    @Operation(summary = "Delete attendance record")
    public ResponseEntity<Void> deleteAttendance(@PathVariable UUID id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/departments")
    @Operation(summary = "List all departments")
    public ResponseEntity<List<String>> getDepartments() {
        return ResponseEntity.ok(departmentService.getDepartmentNames());
    }

    @PostMapping("/departments")
    @Operation(summary = "Create a department")
    public ResponseEntity<DepartmentResponse> createDepartment(
        @Valid @RequestBody CreateDepartmentRequest request
    ) {
        String name = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DepartmentResponse.builder().name(name).build());
    }

    @DeleteMapping("/departments")
    @Operation(summary = "Delete a department")
    public ResponseEntity<Void> deleteDepartment(@RequestParam String name) {
        departmentService.deleteDepartment(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members")
    @Operation(summary = "Create a new team member")
    public ResponseEntity<UserResponse> createMember(
        @Valid @RequestBody CreateMemberRequest request
    ) {
        UserResponse member = userService.createMember(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }
}
