package com.kanban.repository;

import com.kanban.model.entity.User;
import com.kanban.model.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByNameIgnoreCase(String name);

    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<User> searchByNameContaining(@Param("name") String name);

    Page<User> findByRole(UserRole role, Pageable pageable);

    @Query("""
        SELECT u FROM User u 
        WHERE (:department IS NULL OR u.department = :department)
        AND (:skill IS NULL OR :skill MEMBER OF u.skills)
        AND u.isActive = true
        ORDER BY u.createdAt DESC
        """)
    Page<User> findByDepartmentAndSkills(
        @Param("department") String department,
        @Param("skill") String skill,
        Pageable pageable
    );

    @Query("""
        SELECT u FROM User u 
        WHERE u.role = :role AND u.isActive = true
        ORDER BY u.lastActive DESC
        """)
    Page<User> findActiveByRole(@Param("role") UserRole role, Pageable pageable);

    long countByIsActiveTrue();

    long countByRole(UserRole role);

    @Query("""
        SELECT DISTINCT u.department FROM User u
        WHERE u.department IS NOT NULL AND u.department <> ''
        ORDER BY u.department ASC
        """)
    List<String> findDistinctDepartments();

    @Modifying
    @Query("UPDATE User u SET u.department = NULL WHERE LOWER(u.department) = LOWER(:department)")
    int clearDepartment(@Param("department") String department);

    @Query("""
        SELECT DISTINCT u FROM User u
        WHERE u.isActive = true
        AND u.role = 'USER'
        AND (:department IS NULL OR u.department = :department)
        ORDER BY u.name ASC
        """)
    List<User> findActiveEmployees(@Param("department") String department);

    @Query("SELECT DISTINCT u FROM User u ORDER BY u.createdAt DESC")
    List<User> findAllDistinct();
}
