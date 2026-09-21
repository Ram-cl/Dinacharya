package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.exception.UnauthorizedException;
import com.kanban.mapper.AttachmentMapper;
import com.kanban.model.dto.response.AttachmentResponse;
import com.kanban.model.entity.Attachment;
import com.kanban.model.entity.Task;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper attachmentMapper;
    private final UserService userService;
    private final TaskService taskService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public Set<AttachmentResponse> getAttachmentsByTask(UUID taskId) {
        Task task = taskService.getTaskEntityById(taskId);
        return attachmentRepository.findByTask(task).stream()
            .map(attachmentMapper::toResponse)
            .collect(Collectors.toSet());
    }

    @Transactional
    public AttachmentResponse uploadAttachment(UUID taskId, MultipartFile file, UUID uploadedById) throws IOException {
        User uploadedBy = userService.getUserEntityById(uploadedById);
        Task task = taskService.getTaskEntityById(taskId);

        // Verify uploader is a team member
        if (!task.getTeam().getMembers().contains(uploadedBy) && uploadedBy.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("You must be a team member to upload attachments");
        }

        String fileUrl = fileStorageService.uploadFile(file);

        Attachment attachment = Attachment.builder()
            .fileUrl(fileUrl)
            .fileName(file.getOriginalFilename())
            .fileType(file.getContentType())
            .task(task)
            .uploadedBy(uploadedBy)
            .build();

        attachment = attachmentRepository.save(attachment);
        auditService.logAttachmentAdded(uploadedById, attachment.getId(), taskId, file.getOriginalFilename());

        return attachmentMapper.toResponse(attachment);
    }

    @Transactional
    public void deleteAttachment(UUID id, UUID currentUserId) {
        Attachment attachment = attachmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with id: " + id));

        User currentUser = userService.getUserEntityById(currentUserId);

        boolean isUploader = attachment.getUploadedBy().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;

        if (!isUploader && !isAdmin) {
            throw new UnauthorizedException("You don't have permission to delete this attachment");
        }

        String fileName = attachment.getFileName();
        String fileUrl = attachment.getFileUrl();
        UUID taskId = attachment.getTask().getId();
        UUID teamId = attachment.getTask().getTeam().getId();

        attachmentRepository.delete(attachment);
        fileStorageService.deleteFile(fileUrl);

        auditService.logAttachmentDeleted(currentUserId, id, fileName);
    }
}
