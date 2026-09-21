package com.kanban.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * One-time data reconciliation for the {@code tasks.completed_at} column that backs the
 * employee performance breakdown.
 *
 * <p>The column is added automatically by Hibernate (ddl-auto: update), but existing rows
 * start with a NULL value. Performance analytics count a task as "completed in a period"
 * based on {@code completed_at}, so without this backfill previously finished tasks would be
 * invisible to the scores until they were edited again.</p>
 *
 * <p>It also clears performance snapshots for the current (in-progress) month so the overview
 * and detail pages recompute them with the corrected completion logic. Finalized snapshots
 * from previous months are left untouched so the rolling index keeps its history.</p>
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedAtInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            int backfilled = statement.executeUpdate(
                "UPDATE tasks SET completed_at = updated_at WHERE status = 'DONE' AND completed_at IS NULL"
            );
            int cleared = statement.executeUpdate(
                "UPDATE tasks SET completed_at = NULL WHERE status <> 'DONE' AND completed_at IS NOT NULL"
            );
            if (backfilled > 0 || cleared > 0) {
                log.info("Reconciled tasks.completed_at (backfilled={}, cleared={})", backfilled, cleared);
            }

            int staleSnapshots = statement.executeUpdate(
                "DELETE FROM employee_performance_snapshots WHERE period_start >= DATE_FORMAT(CURDATE(), '%Y-%m-01')"
            );
            if (staleSnapshots > 0) {
                log.info("Cleared {} current-period performance snapshot(s) for recomputation", staleSnapshots);
            }
        } catch (Exception ex) {
            log.warn("Could not reconcile tasks.completed_at: {}", ex.getMessage());
        }
    }
}
