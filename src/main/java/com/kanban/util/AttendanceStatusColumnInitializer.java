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

@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class AttendanceStatusColumnInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            widenStatus(statement, "attendance_records", false);
            widenStatus(statement, "time_entries", true);
            statement.executeUpdate(
                "UPDATE attendance_records SET status = 'ONLINE' WHERE status IN ('PRESENT', 'ON_BREAK', 'WORK_FROM_HOME', 'HALF_DAY')"
            );
            statement.executeUpdate(
                "UPDATE attendance_records SET status = 'OFFLINE' WHERE status IN ('ABSENT', 'LEAVE', 'AWAY')"
            );
        } catch (Exception ex) {
            log.warn("Could not widen attendance status columns: {}", ex.getMessage());
        }
    }

    private void widenStatus(Statement statement, String table, boolean nullable) {
        String nullability = nullable ? "NULL" : "NOT NULL";
        try {
            statement.execute("ALTER TABLE " + table + " MODIFY COLUMN status VARCHAR(50) " + nullability);
            log.info("Ensured {}.status is VARCHAR(50)", table);
        } catch (Exception ex) {
            log.debug("Could not alter {}.status: {}", table, ex.getMessage());
        }
    }
}
