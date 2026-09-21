package com.kanban.repository;

import com.kanban.model.entity.TimeEntry;
import com.kanban.model.entity.User;
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
public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    Optional<TimeEntry> findByUserAndEntryDate(User user, LocalDate entryDate);

    List<TimeEntry> findByUserAndEntryDateBetween(User user, LocalDate startDate, LocalDate endDate);

    Page<TimeEntry> findByEntryDate(LocalDate entryDate, Pageable pageable);

    Page<TimeEntry> findByUser_IdAndEntryDateBetween(UUID userId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query("SELECT t FROM TimeEntry t WHERE t.entryDate = :date ORDER BY t.user.name")
    List<TimeEntry> findAllByDate(@Param("date") LocalDate date);

    @Query("SELECT AVG(t.hoursWorked) FROM TimeEntry t WHERE t.user.id = :userId AND t.entryDate BETWEEN :startDate AND :endDate")
    Double calculateAverageHours(@Param("userId") UUID userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(t.breakDurationMinutes) FROM TimeEntry t WHERE t.user.id = :userId AND t.entryDate = :date")
    Integer calculateTotalBreaks(@Param("userId") UUID userId, @Param("date") LocalDate date);

    void deleteByUser_Id(UUID userId);
}
