package com.kanban.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data migration runner that converts old role values to new simplified role system.
 * Runs FIRST on application startup (Order = Integer.MIN_VALUE) before other components.
 * This must execute before AseTeamSeeder to prevent enum deserialization errors.
 */
@Component
@Slf4j
@Order(Integer.MIN_VALUE)  // Run this FIRST, before all other CommandLineRunners
public class RoleDataMigration implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("========== STARTING RBAC ROLE DATA MIGRATION ==========");
        
        try {
            // Check if the users table exists first
            try {
                entityManager.createNativeQuery("SELECT 1 FROM users LIMIT 1").getSingleResult();
            } catch (Exception e) {
                log.info("Users table does not exist yet - skipping role migration (will run on next startup after tables are created)");
                return;
            }
            
            // Step 1: First, expand the ENUM column to accept all old and new values temporarily
            // This allows us to update without truncation errors
            try {
                log.info("Updating role column definition to accept all values...");
                entityManager.createNativeQuery(
                    "ALTER TABLE users MODIFY COLUMN role ENUM('USER', 'ADMIN', 'MEMBER', 'TEAM_LEAD', 'MODERATOR')"
                ).executeUpdate();
                log.info("✓ Column definition updated to support old and new values");
            } catch (Exception e) {
                // Column might already be in correct format or have different definition
                log.debug("Note: Could not modify column definition (may already be updated): {}", e.getMessage());
            }

            // Step 2: Convert MEMBER to USER
            int memberCount = entityManager.createNativeQuery(
                "UPDATE users SET role = 'USER' WHERE role = 'MEMBER'"
            ).executeUpdate();
            if (memberCount > 0) {
                log.info("✓ Migrated {} MEMBER users to USER role", memberCount);
            }

            // Step 3: Convert TEAM_LEAD to ADMIN
            int teamLeadCount = entityManager.createNativeQuery(
                "UPDATE users SET role = 'ADMIN' WHERE role = 'TEAM_LEAD'"
            ).executeUpdate();
            if (teamLeadCount > 0) {
                log.info("✓ Migrated {} TEAM_LEAD users to ADMIN role", teamLeadCount);
            }

            // Step 4: Convert MODERATOR to ADMIN
            int moderatorCount = entityManager.createNativeQuery(
                "UPDATE users SET role = 'ADMIN' WHERE role = 'MODERATOR'"
            ).executeUpdate();
            if (moderatorCount > 0) {
                log.info("✓ Migrated {} MODERATOR users to ADMIN role", moderatorCount);
            }

            // Step 5: Backup check - don't delete users, just warn about invalid roles
            long invalidCount = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM users WHERE role NOT IN ('USER', 'ADMIN') AND role IS NOT NULL"
            ).getSingleResult()).longValue();
            if (invalidCount > 0) {
                log.warn("⚠ Found {} users with non-standard roles (preserving data, will convert on next run)", invalidCount);
                // Set these to USER role by default instead of deleting
                int convertedCount = entityManager.createNativeQuery(
                    "UPDATE users SET role = 'USER' WHERE role NOT IN ('USER', 'ADMIN') AND role IS NOT NULL"
                ).executeUpdate();
                log.info("✓ Converted {} users with invalid roles to USER (data preserved)", convertedCount);
            }

            // Step 6: Finally, restrict the ENUM column to only new valid values
            try {
                log.info("Restricting role column to new values only...");
                entityManager.createNativeQuery(
                    "ALTER TABLE users MODIFY COLUMN role ENUM('USER', 'ADMIN') NOT NULL"
                ).executeUpdate();
                log.info("✓ Column definition restricted to USER, ADMIN values");
            } catch (Exception e) {
                // Hibernate's ddl-auto will handle this on next deployment
                log.debug("Note: Could not restrict column definition: {}", e.getMessage());
            }

            entityManager.flush();
            log.info("========== RBAC ROLE DATA MIGRATION COMPLETED SUCCESSFULLY ==========");
        } catch (Exception e) {
            log.error("ERROR during RBAC role data migration", e);
            throw e;
        }
    }
}
