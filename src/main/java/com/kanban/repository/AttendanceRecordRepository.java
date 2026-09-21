package com.kanban.repository;

import com.kanban.model.entity.AttendanceRecord;
import com.kanban.model.enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    List<AttendanceRecord> findByWorkDate(LocalDate workDate);

    Optional<AttendanceRecord> findByUserIdAndWorkDate(UUID userId, LocalDate workDate);

    @Query("""
        SELECT ar FROM AttendanceRecord ar
        JOIN ar.user u
        WHERE ar.workDate = :workDate
        AND (:department IS NULL OR u.department = :department)
        AND (:status IS NULL OR ar.status = :status)
        AND (
            :search IS NULL
            OR LOWER(u.name) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(u.email) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(COALESCE(u.department, '')) LIKE CONCAT('%', LOWER(:search), '%')
        )
        ORDER BY u.name ASC
        """)
    Page<AttendanceRecord> findByFilters(
        @Param("workDate") LocalDate workDate,
        @Param("department") String department,
        @Param("status") AttendanceStatus status,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("""
        SELECT ar FROM AttendanceRecord ar
        WHERE ar.user.id = :userId
        AND ar.workDate BETWEEN :startDate AND :endDate
        ORDER BY ar.workDate ASC
        """)
    List<AttendanceRecord> findByUserIdAndWorkDateBetween(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    void deleteByUser_Id(UUID userId);
}
