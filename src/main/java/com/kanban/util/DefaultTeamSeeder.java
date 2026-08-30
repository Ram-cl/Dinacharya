package com.kanban.util;

import com.kanban.model.entity.Department;
import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import com.kanban.repository.DepartmentRepository;
import com.kanban.repository.UserRepository;
import com.kanban.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One team per department so imports and tasks can use department as the workspace.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class DefaultTeamSeeder implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final TeamService teamService;

    @Override
    public void run(String... args) {
        try {
            User lead = userRepository.findAllDistinct().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN)
                    .findFirst()
                    .orElse(null);
            if (lead == null) {
                log.warn("No admin user yet — skipped department teams");
                return;
            }

            for (Department department : departmentRepository.findAll()) {
                teamService.getOrCreateDepartmentTeam(department.getName(), lead);
            }
            log.info("Ensured a team for each department");
        } catch (Exception e) {
            log.error("Department team seeding failed; app will keep running: {}", e.getMessage(), e);
        }
    }
}
