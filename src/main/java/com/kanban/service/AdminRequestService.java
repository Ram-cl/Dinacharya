package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.exception.UnauthorizedException;
import com.kanban.mapper.AdminRequestMapper;
import com.kanban.model.dto.request.AdminRequestRequest;
import com.kanban.model.dto.response.AdminRequestResponse;
import com.kanban.model.entity.AdminRequest;
import com.kanban.model.entity.User;
import com.kanban.model.enums.RequestStatus;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.AdminRequestRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRequestService {

    private final AdminRequestRepository adminRequestRepository;
    private final UserRepository userRepository;
    private final AdminRequestMapper adminRequestMapper;

    @Transactional
    public AdminRequestResponse requestAdminAccess(UUID userId, AdminRequestRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Check if user already has admin role
        if (user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("User already has admin role");
        }

        // Check if there's already a pending request
        var existingRequest = adminRequestRepository.findByUserAndStatus(user, RequestStatus.PENDING);
        if (existingRequest.isPresent()) {
            throw new IllegalArgumentException("User already has a pending admin request");
        }

        // Create new admin request
        AdminRequest adminRequest = AdminRequest.builder()
                .user(user)
                .reason(request.getReason())
                .status(RequestStatus.PENDING)
                .build();

        adminRequest = adminRequestRepository.save(adminRequest);
        
        // Update user's request status
        user.setAdminRequestPending(true);
        user.setAdminRequestDate(LocalDateTime.now());
        user.setAdminRequestReason(request.getReason());
        userRepository.save(user);

        log.info("Admin access requested by user: {} ({})", user.getEmail(), userId);

        return adminRequestMapper.toResponse(adminRequest);
    }

    @Transactional(readOnly = true)
    public AdminRequestResponse getAdminRequest(UUID requestId, UUID adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can view admin requests");
        }

        AdminRequest adminRequest = adminRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin request not found with id: " + requestId));

        return adminRequestMapper.toResponse(adminRequest);
    }

    @Transactional(readOnly = true)
    public Page<AdminRequestResponse> getPendingRequests(UUID adminId, Pageable pageable) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can view admin requests");
        }

        Page<AdminRequest> requests = adminRequestRepository.findByStatus(RequestStatus.PENDING, pageable);
        return requests.map(adminRequestMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminRequestResponse> getAllRequests(UUID adminId, Pageable pageable) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can view admin requests");
        }

        Page<AdminRequest> requests = adminRequestRepository.findAll(pageable);
        return requests.map(adminRequestMapper::toResponse);
    }

    @Transactional
    public AdminRequestResponse approveAdminRequest(UUID requestId, UUID adminId, String notes) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can approve admin requests");
        }

        AdminRequest adminRequest = adminRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin request not found with id: " + requestId));

        if (adminRequest.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException("Admin request is already " + adminRequest.getStatus().name());
        }

        // Approve the request
        adminRequest.setStatus(RequestStatus.APPROVED);
        adminRequest.setAdminNotes(notes);
        adminRequest.setReviewedAt(LocalDateTime.now());
        adminRequest = adminRequestRepository.save(adminRequest);

        // Grant admin role to user
        User user = adminRequest.getUser();
        user.setRole(UserRole.ADMIN);
        user.setAdminRequestPending(false);
        userRepository.save(user);

        log.info("Admin request approved for user: {} by admin: {}", user.getEmail(), admin.getEmail());

        return adminRequestMapper.toResponse(adminRequest);
    }

    @Transactional
    public AdminRequestResponse rejectAdminRequest(UUID requestId, UUID adminId, String notes) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can reject admin requests");
        }

        AdminRequest adminRequest = adminRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin request not found with id: " + requestId));

        if (adminRequest.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException("Admin request is already " + adminRequest.getStatus().name());
        }

        // Reject the request
        adminRequest.setStatus(RequestStatus.REJECTED);
        adminRequest.setAdminNotes(notes);
        adminRequest.setReviewedAt(LocalDateTime.now());
        adminRequest = adminRequestRepository.save(adminRequest);

        // Update user's request status
        User user = adminRequest.getUser();
        user.setAdminRequestPending(false);
        userRepository.save(user);

        log.info("Admin request rejected for user: {} by admin: {}", user.getEmail(), admin.getEmail());

        return adminRequestMapper.toResponse(adminRequest);
    }

    @Transactional(readOnly = true)
    public AdminRequestResponse checkMyRequest(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        var adminRequest = adminRequestRepository.findByUserAndStatus(user, RequestStatus.PENDING);
        
        if (adminRequest.isEmpty()) {
            throw new ResourceNotFoundException("No pending admin request found for this user");
        }

        return adminRequestMapper.toResponse(adminRequest.get());
    }
}
