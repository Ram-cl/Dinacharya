package com.kanban.config;

import com.kanban.model.entity.Department;
import com.kanban.model.entity.User;
import com.kanban.repository.DepartmentRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Two jobs on every startup:
 *
 * 1. Normalise department names on User rows — strips accidental dept prefix from names
 *    (e.g. "CyberSecurity Alice" -> name="Alice", dept="Cybersecurity") and corrects
 *    casing variants (CyberSecurity / Devops / UIUX -> canonical form).
 *
 * 2. Normalise the departments table — renames any stale variant rows to the canonical
 *    name and removes duplicates so the dropdown never shows both "DevOps" and "Devops".
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DepartmentNameMigrationRunner implements ApplicationRunner {

    /** Canonical department names — single source of truth. */
    private static final List<String> CANONICAL_DEPARTMENTS = List.of(
        "ASE",
        "Business Development",
        "Cybersecurity",
        "Dev",
        "DevOps",
        "Engineering",
        "UI"
    );

    /**
     * Maps every known variant (lower-cased) to its canonical form.
     * Used both for prefix-stripping and for department-value correction.
     */
    private static final Map<String, String> NORMALISE = Map.ofEntries(
        Map.entry("ase",                  "ASE"),
        Map.entry("business development", "Business Development"),
        Map.entry("cybersecurity",        "Cybersecurity"),
        Map.entry("devops",               "DevOps"),
        Map.entry("dev",                  "Dev"),
        Map.entry("engineering",          "Engineering"),
        Map.entry("ui",                   "UI"),
        Map.entry("uiux",                 "UI"),
        Map.entry("ui/ux",                "UI"),
        Map.entry("ui ux",                "UI")
    );

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        normaliseDepartmentsTable();
        normaliseUserNames();
    }

    // -------------------------------------------------------------------------
    // 1. departments table
    // -------------------------------------------------------------------------
    private void normaliseDepartmentsTable() {
        // Ensure every canonical department exists.
        for (String canonical : CANONICAL_DEPARTMENTS) {
            if (!departmentRepository.existsByNameIgnoreCase(canonical)) {
                departmentRepository.save(Department.builder().name(canonical).build());
                log.info("Seeded missing department: {}", canonical);
            }
        }

        // Rename any non-canonical rows to the canonical name; delete duplicates.
        List<Department> all = departmentRepository.findAllByOrderByNameAsc();
        for (Department dept : all) {
            String canonical = canonical(dept.getName());
            if (canonical == null) continue; // unknown dept — leave it alone
            if (canonical.equals(dept.getName())) continue; // already correct

            // Check if the canonical row already exists (different object).
            departmentRepository.findByNameIgnoreCase(canonical).ifPresentOrElse(
                existing -> {
                    if (!existing.getId().equals(dept.getId())) {
                        log.info("Removing duplicate department '{}' (canonical '{}' already exists)",
                            dept.getName(), canonical);
                        departmentRepository.delete(dept);
                    }
                },
                () -> {
                    log.info("Renaming department '{}' -> '{}'", dept.getName(), canonical);
                    dept.setName(canonical);
                    departmentRepository.save(dept);
                }
            );
        }
    }

    // -------------------------------------------------------------------------
    // 2. users.name / users.department
    // -------------------------------------------------------------------------
    private void normaliseUserNames() {
        List<User> allUsers = userRepository.findAllDistinct();
        int fixed = 0;

        for (User user : allUsers) {
            boolean changed = false;

            // Strip accidental department prefix from name.
            String name = user.getName();
            if (name != null && !name.isBlank()) {
                for (String key : NORMALISE.keySet()) {
                    String lc = name.toLowerCase().trim();
                    if (lc.startsWith(key + " ") || lc.startsWith(key + "_")) {
                        String stripped = name.substring(key.length() + 1).trim();
                        if (!stripped.isBlank()) {
                            String detectedDept = NORMALISE.get(key);
                            log.info("Fixing name: '{}' -> '{}' (dept={})", name, stripped, detectedDept);
                            user.setName(stripped);
                            if (user.getDepartment() == null || user.getDepartment().isBlank()) {
                                user.setDepartment(detectedDept);
                            }
                            changed = true;
                            break;
                        }
                    }
                }
            }

            // Normalise department casing on the user row.
            String dept = user.getDepartment();
            if (dept != null && !dept.isBlank()) {
                String canonical = canonical(dept);
                if (canonical != null && !canonical.equals(dept)) {
                    log.info("Normalising user dept: '{}' -> '{}' (user: {})", dept, canonical, user.getName());
                    user.setDepartment(canonical);
                    changed = true;
                }
            }

            if (changed) {
                userRepository.save(user);
                fixed++;
            }
        }

        if (fixed > 0) {
            log.info("Department name migration: fixed {} user row(s)", fixed);
        }
    }

    /** Returns the canonical form for a department name, or null if unknown. */
    private static String canonical(String raw) {
        if (raw == null) return null;
        return NORMALISE.get(raw.toLowerCase().trim());
    }
}
