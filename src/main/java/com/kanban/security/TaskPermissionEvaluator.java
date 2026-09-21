package com.kanban.security;

import com.kanban.model.entity.Task;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.TaskRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TaskPermissionEvaluator implements PermissionEvaluator {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null || !(permission instanceof String)) {
            return false;
        }

        String targetType = targetDomainObject.getClass().getSimpleName().toLowerCase();
        return hasPrivilege(authentication, targetType, permission.toString(), targetDomainObject);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null || !(permission instanceof String)) {
            return false;
        }

        if ("task".equalsIgnoreCase(targetType)) {
            Task task = taskRepository.findById((UUID) targetId).orElse(null);
            if (task == null) {
                return false;
            }
            return hasPrivilege(authentication, targetType.toLowerCase(), permission.toString(), task);
        }

        return false;
    }

    private boolean hasPrivilege(Authentication authentication, String targetType, String permission, Object targetObject) {
        String userEmail = authentication.getName();
        User user = userRepository.findByEmail(userEmail).orElse(null);

        if (user == null) {
            return false;
        }

        // Admin has full access
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }

        if ("task".equals(targetType) && targetObject instanceof Task task) {
            // Task creator can read/edit/delete
            if (task.getCreatedBy().getId().equals(user.getId())) {
                return true;
            }

            // For "read" permission, any team member can view
            if ("read".equals(permission) && task.getTeam().getMembers().contains(user)) {
                return true;
            }
        }

        return false;
    }
}
