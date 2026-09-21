package com.kanban.mapper;

import com.kanban.model.dto.response.AdminRequestResponse;
import com.kanban.model.entity.AdminRequest;
import org.springframework.stereotype.Component;

@Component
public class AdminRequestMapper {

    public AdminRequestResponse toResponse(AdminRequest request) {
        if (request == null) {
            return null;
        }

        return AdminRequestResponse.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .userEmail(request.getUser().getEmail())
                .userName(request.getUser().getName())
                .reason(request.getReason())
                .status(request.getStatus())
                .adminNotes(request.getAdminNotes())
                .requestedAt(request.getRequestedAt())
                .reviewedAt(request.getReviewedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
