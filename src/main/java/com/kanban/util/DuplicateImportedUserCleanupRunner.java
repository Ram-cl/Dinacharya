package com.kanban.util;

import com.kanban.model.entity.User;
import com.kanban.repository.AttachmentRepository;
import com.kanban.repository.AttendanceRecordRepository;
import com.kanban.repository.CommentRepository;
import com.kanban.repository.EmployeePerformanceSnapshotRepository;
import com.kanban.repository.PasswordResetTokenRepository;
import com.kanban.repository.TaskRepository;
import com.kanban.repository.TeamRepository;
import com.kanban.repository.TimeEntryRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes duplicate user accounts created by repeated Excel imports.
 *
 * The importer generates emails like:
 *   cybersecurity.karthik@imported.local   (first import)
 *   cybersecurity.karthik1@imported.local  (second import — duplicate)
 *   cybersecurity.karthik2@imported.local  (third import — duplicate)
 *
 * This runner keeps the account whose email has NO numeric suffix (the original),
 * and deletes all numbered variants, handling every FK table.
 *
 * Runs at @Order(3) — after DepartmentNameMigrationRunner (@Order 2).
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class DuplicateImportedUserCleanupRunner implements ApplicationRunner {

    // Matches emails like "slug1@imported.local", "slug2@imported.local", etc.
    private static final Pattern SUFFIXED_EMAIL =
        Pattern.compile("^(.+?)(\\d+)@imported\\.local$");

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeePerformanceSnapshotRepository snapshotRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TeamRepository teamRepository;
    private final TimeEntryRepository timeEntryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> importedUsers = userRepository.findAllDistinct().stream()
            .filter(u -> u.getEmail() != null && u.getEmail().endsWith("@imported.local"))
            .toList();

        // Group by lower-cased name.
        Map<String, List<User>> byName = new LinkedHashMap<>();
        for (User u : importedUsers) {
            String key = u.getName() == null ? "" : u.getName().trim().toLowerCase();
            byName.computeIfAbsent(key, k -> new ArrayList<>()).add(u);
        }

        int deleted = 0;
        for (Map.Entry<String, List<User>> entry : byName.entrySet()) {
            List<User> group = entry.getValue();
            if (group.size() <= 1) continue;

            // Sort: accounts whose email has NO numeric suffix come first (they are the
            // originals). Among suffixed accounts, sort by numeric suffix ascending.
            group.sort(Comparator.comparingInt(DuplicateImportedUserCleanupRunner::emailSuffix));

            User keep = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                User dup = group.get(i);
                log.info("Removing duplicate imported user: '{}' ({}) — keeping ({})",
                    dup.getName(), dup.getEmail(), keep.getEmail());
                purgeUser(dup);
                deleted++;
            }
        }

        if (deleted > 0) {
            log.info("Duplicate imported user cleanup: removed {} account(s)", deleted);
        }
    }

    /**
     * Returns the numeric suffix of an @imported.local email, or 0 if there is none.
     * "slug@imported.local"  -> 0  (original — sort first)
     * "slug1@imported.local" -> 1
     * "slug2@imported.local" -> 2
     */
    private static int emailSuffix(User u) {
        if (u.getEmail() == null) return Integer.MAX_VALUE;
        Matcher m = SUFFIXED_EMAIL.matcher(u.getEmail());
        return m.matches() ? Integer.parseInt(m.group(2)) : 0;
    }

    /** Deletes a user and all their dependent rows across every FK table. */
    private void purgeUser(User user) {
        var id = user.getId();
        timeEntryRepository.deleteByUser_Id(id);
        taskRepository.deleteByAssignedTo_Id(id);
        commentRepository.deleteByAuthor_Id(id);
        attachmentRepository.deleteByUploadedBy_Id(id);
        attendanceRecordRepository.deleteByUser_Id(id);
        snapshotRepository.deleteByUser_Id(id);
        passwordResetTokenRepository.deleteByUser_Id(id);
        teamRepository.removeFromAllTeams(id);
        userRepository.delete(user);
    }
}
