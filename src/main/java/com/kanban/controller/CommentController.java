package com.kanban.controller;

import com.kanban.model.dto.request.CreateCommentRequest;
import com.kanban.model.dto.response.CommentResponse;
import com.kanban.security.CustomUserDetailsService;
import com.kanban.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/comments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Comments", description = "Comment management APIs")
public class CommentController {

    private final CommentService commentService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping
    @Operation(summary = "Get comments for a task")
    public ResponseEntity<Set<CommentResponse>> getCommentsByTask(
        @PathVariable UUID taskId,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        Set<CommentResponse> comments = commentService.getCommentsByTask(taskId, user.getId());
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/paginated")
    @Operation(summary = "Get comments for a task (paginated)")
    public ResponseEntity<Page<CommentResponse>> getCommentsByTaskPaginated(
        @PathVariable UUID taskId,
        Authentication authentication,
        Pageable pageable
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        Page<CommentResponse> comments = commentService.getCommentsByTaskPaginated(taskId, user.getId(), pageable);
        return ResponseEntity.ok(comments);
    }

    @PostMapping
    @Operation(summary = "Add a comment to a task")
    public ResponseEntity<CommentResponse> createComment(
        @PathVariable UUID taskId,
        @Valid @RequestBody CreateCommentRequest request,
        Authentication authentication
    ) {
        var user = userDetailsService.loadUserEntityByEmail(authentication.getName());
        CommentResponse comment = commentService.createComment(taskId, request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }
}
