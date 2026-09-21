package com.kanban.service;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.exception.UnauthorizedException;
import com.kanban.mapper.CommentMapper;
import com.kanban.model.dto.request.CreateCommentRequest;
import com.kanban.model.dto.response.CommentResponse;
import com.kanban.model.entity.Comment;
import com.kanban.model.entity.Task;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final UserService userService;
    private final TaskService taskService;
    private final AuditService auditService;

    public Set<CommentResponse> getCommentsByTask(UUID taskId) {
        Task task = taskService.getTaskEntityById(taskId);
        return commentRepository.findByTask(task).stream()
            .map(commentMapper::toResponse)
            .collect(Collectors.toSet());
    }

    public Page<CommentResponse> getCommentsByTaskPaginated(UUID taskId, Pageable pageable) {
        Task task = taskService.getTaskEntityById(taskId);
        return commentRepository.findByTaskOrderByCreatedAtDesc(task, pageable)
            .map(commentMapper::toResponse);
    }

    public Page<CommentResponse> getFlaggedComments(Pageable pageable) {
        return commentRepository.findByFlaggedTrue(pageable)
            .map(commentMapper::toResponse);
    }

    @Transactional
    public CommentResponse createComment(UUID taskId, CreateCommentRequest request, UUID authorId) {
        User author = userService.getUserEntityById(authorId);
        Task task = taskService.getTaskEntityById(taskId);

        // Verify author is a team member
        if (!task.getTeam().getMembers().contains(author) && author.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("You must be a team member to comment on tasks");
        }

        Comment comment = Comment.builder()
            .content(request.getContent())
            .author(author)
            .task(task)
            .flagged(false)
            .build();

        comment = commentRepository.save(comment);
        auditService.logCommentAdded(authorId, comment.getId(), taskId);

        return commentMapper.toResponse(comment);
    }

    @Transactional
    public void deleteComment(UUID id, UUID currentUserId) {
        Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + id));

        User currentUser = userService.getUserEntityById(currentUserId);

        boolean isAuthor = comment.getAuthor().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new UnauthorizedException("You don't have permission to delete this comment");
        }

        commentRepository.delete(comment);
    }

    @Transactional
    public CommentResponse flagComment(UUID id, UUID adminId) {
        Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + id));

        User admin = userService.getUserEntityById(adminId);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can flag comments");
        }

        comment.setFlagged(true);
        comment = commentRepository.save(comment);

        auditService.logCommentFlagged(adminId, id);

        return commentMapper.toResponse(comment);
    }

    @Transactional
    public CommentResponse resolveComment(UUID id, UUID adminId) {
        Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + id));

        User admin = userService.getUserEntityById(adminId);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admin can resolve flagged comments");
        }

        comment.setFlagged(false);
        comment = commentRepository.save(comment);

        return commentMapper.toResponse(comment);
    }

    public long countFlaggedComments() {
        return commentRepository.countFlaggedComments();
    }
}
