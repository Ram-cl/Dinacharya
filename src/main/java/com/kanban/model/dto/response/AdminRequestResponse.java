package com.kanban.model.dto.response;

import com.kanban.model.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminRequestResponse {
    
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userName;
    private String reason;
    private RequestStatus status;
    private String adminNotes;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime updatedAt;
}
