package com.kanban.config;

import com.kanban.model.entity.User;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(50)
public class DepartmentNameMigrationRunner implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running department name migration...");
        
        try {
            // Check if we can query users first
            long count = userRepository.count();
            log.info("Found {} users to check for migration", count);
            if (count == 0) {
                log.info("No users to migrate");
                return;
            }
            List<User> allUsers = userRepository.findAll();
            int cybersecurityCount = 0;
            int devopsCount = 0;

            for (User user : allUsers) {
                String name = user.getName();
                if (name == null) continue;

                String nameLower = name.toLowerCase().trim();
                
                if (nameLower.startsWith("cybersecurity ")) {
                    String newName = name.substring("cybersecurity ".length()).trim();
                    user.setName(newName);
                    user.setDepartment("Cybersecurity");
                    userRepository.save(user);
                    cybersecurityCount++;
                    log.info("Migrated: {} -> {} (Cybersecurity dept)", name, newName);
                } else if (nameLower.startsWith("devops ")) {
                    String newName = name.substring("devops ".length()).trim();
                    user.setName(newName);
                    user.setDepartment("DevOps");
                    userRepository.save(user);
                    devopsCount++;
                    log.info("Migrated: {} -> {} (DevOps dept)", name, newName);
                }
            }

            log.info("Department name migration complete: {} Cybersecurity, {} DevOps updated", 
                    cybersecurityCount, devopsCount);
        } catch (Exception e) {
            log.warn("Department name migration skipped - tables may not exist yet: {}", e.getMessage());
        }
    }
}
