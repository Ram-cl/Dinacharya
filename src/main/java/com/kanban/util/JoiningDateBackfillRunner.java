package com.kanban.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Backfills {@code users.joining_date} for rows where it is still NULL.
 * Uses {@code DATE(created_at)} as the joining date — a safe proxy for existing
 * accounts that were created before this column was introduced.
 * New accounts get their joining_date set explicitly at registration time.
 *
 * Runs at @Order(1) — after AttendanceStatusColumnInitializer (0) and before
 * the department migration runner (2).
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class JoiningDateBackfillRunner implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            int updated = statement.executeUpdate(
                "UPDATE users SET joining_date = DATE(created_at) WHERE joining_date IS NULL"
            );
            if (updated > 0) {
                log.info("Backfilled joining_date for {} existing user(s)", updated);
            }
        } catch (Exception ex) {
            log.warn("Could not backfill joining_date: {}", ex.getMessage());
        }
    }
}
