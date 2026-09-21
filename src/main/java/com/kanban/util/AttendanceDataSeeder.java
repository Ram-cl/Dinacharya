package com.kanban.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Attendance data seeder - DISABLED
 * Previously auto-created attendance records on startup.
 * Now disabled to allow manual attendance entry only.
 */
@Component
@Order(21)
@Slf4j
public class AttendanceDataSeeder implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.info("AttendanceDataSeeder is disabled - no automatic attendance records will be created");
    }
}
