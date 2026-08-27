package com.kanban.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Department name migration runner - DISABLED for initial deployment.
 * This was causing startup failures due to user_skills table not existing yet.
 * Can be re-enabled after tables are created successfully.
 */
@Component
@Slf4j
@Order(50)
public class DepartmentNameMigrationRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.info("Department name migration runner - SKIPPED (disabled for initial deployment)");
        // Migration disabled - tables need to exist first
    }
}
