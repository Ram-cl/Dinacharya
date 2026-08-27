package com.kanban.util;

import com.kanban.model.entity.Department;
import com.kanban.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepartmentSeeder implements ApplicationRunner {

    public static final List<String> DEFAULT_DEPARTMENTS = List.of(
        "ASE",
        "Business Development",
        "CyberSecurity",
        "Dev",
        "Devops",
        "Engineering",
        "UI"
    );

    private final DepartmentRepository departmentRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String name : DEFAULT_DEPARTMENTS) {
                if (!departmentRepository.existsByNameIgnoreCase(name)) {
                    departmentRepository.save(Department.builder().name(name).build());
                    log.info("Seeded department: {}", name);
                }
            }
        } catch (Exception e) {
            log.warn("Department seeder skipped - tables may not exist yet: {}", e.getMessage());
        }
    }
}
