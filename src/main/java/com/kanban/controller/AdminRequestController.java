package com.kanban.controller;

import com.kanban.model.dto.request.AdminRequestRequest;
import com.kanban.model.dto.response.AdminRequestResponse;
import com.kanban.service.AdminRequestService;
import com.kanban.security.CustomUserDetailsService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin-requests")
@RequiredArgsConstructor
@Tag(name = "Admin Requests", description = "APIs for requesting and managing admin access")
public class AdminRequestController {

    private final AdminRequestService adminRequestService;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping
    @Operation(summary = "Request admin access")
    public ResponseEntity<AdminRequestResponse> requestAdminAccess(
            @Valid @RequestBody AdminRequestRequest request,
            Authentication authentication) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        AdminRequestResponse response = adminRequestService.requestAdminAccess(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-request")
    @Operation(summary = "Check my admin request status")
    public ResponseEntity<AdminRequestResponse> checkMyRequest(Authentication authentication) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        AdminRequestResponse response = adminRequestService.checkMyRequest(user.getId());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending")
    @Operation(summary = "Get all pending admin requests (Admin only)")
    public ResponseEntity<Page<AdminRequestResponse>> getPendingRequests(
            Authentication authentication,
            Pageable pageable) {
        var admin = userDetailsService.loadUserEntityByEmail(authentication.getName());
        Page<AdminRequestResponse> requests = adminRequestService.getPendingRequests(admin.getId(), pageable);
        return ResponseEntity.ok(requests);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Get all admin requests (Admin only)")
    public ResponseEntity<Page<AdminRequestResponse>> getAllRequests(
            Authentication authentication,
            Pageable pageable) {
        var admin = userDetailsService.loadUserEntityByEmail(authentication.getName());
        Page<AdminRequestResponse> requests = adminRequestService.getAllRequests(admin.getId(), pageable);
        return ResponseEntity.ok(requests);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{requestId}")
    @Operation(summary = "Get admin request by ID (Admin only)")
    public ResponseEntity<AdminRequestResponse> getAdminRequest(
            @PathVariable UUID requestId,
            Authentication authentication) {
        var admin = userDetailsService.loadUserEntityByEmail(authentication.getName());
        AdminRequestResponse response = adminRequestService.getAdminRequest(requestId, admin.getId());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{requestId}/approve")
    @Operation(summary = "Approve admin request (Admin only)")
    public ResponseEntity<AdminRequestResponse> approveRequest(
            @PathVariable UUID requestId,
            @RequestParam(required = false) String notes,
            Authentication authentication) {
        var admin = userDetailsService.loadUserEntityByEmail(authentication.getName());
        AdminRequestResponse response = adminRequestService.approveAdminRequest(requestId, admin.getId(), notes);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject admin request (Admin only)")
    public ResponseEntity<AdminRequestResponse> rejectRequest(
            @PathVariable UUID requestId,
            @RequestParam(required = false) String notes,
            Authentication authentication) {
        var admin = userDetailsService.loadUserEntityByEmail(authentication.getName());
        AdminRequestResponse response = adminRequestService.rejectAdminRequest(requestId, admin.getId(), notes);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{requestId}/revoke")
    @Operation(summary = "Revoke admin access from user (Admin only)")
    public ResponseEntity<Map<String, String>> revokeAdminAccess(
            @PathVariable UUID requestId,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        var admin = userDetailsService.loadUserEntityByEmail(authentication.getName());
        adminRequestService.rejectAdminRequest(requestId, admin.getId(), reason);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Admin access has been revoked");
        return ResponseEntity.ok(response);
    }
}
