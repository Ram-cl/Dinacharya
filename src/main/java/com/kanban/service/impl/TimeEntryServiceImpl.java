package com.kanban.service.impl;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.exception.UnauthorizedException;
import com.kanban.mapper.TimeEntryMapper;
import com.kanban.model.dto.request.CreateTimeEntryRequest;
import com.kanban.model.dto.request.UpdateTimeEntryRequest;
import com.kanban.model.dto.response.TimeEntryResponse;
import com.kanban.model.entity.TimeEntry;
import com.kanban.model.entity.User;
import com.kanban.repository.TimeEntryRepository;
import com.kanban.repository.UserRepository;
import com.kanban.service.TimeEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeEntryServiceImpl implements TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final TimeEntryMapper timeEntryMapper;

    @Override
    @Transactional
    public TimeEntryResponse createTimeEntry(CreateTimeEntryRequest request, UUID userId) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if entry already exists for this user and date
        var existingEntry = timeEntryRepository.findByUserAndEntryDate(user, request.getEntryDate());
        
        if (existingEntry.isPresent()) {
            // Update existing entry instead of throwing error
            TimeEntry timeEntry = existingEntry.get();
            if (request.getEntryTime() != null) {
                timeEntry.setEntryTime(request.getEntryTime());
            }
            if (request.getExitTime() != null) {
                timeEntry.setExitTime(request.getExitTime());
            }
            if (request.getHoursWorked() != null) {
                timeEntry.setHoursWorked(request.getHoursWorked());
            }
            if (request.getStatus() != null) {
                timeEntry.setStatus(request.getStatus());
            }
            if (request.getBreakDurationMinutes() != null) {
                timeEntry.setBreakDurationMinutes(request.getBreakDurationMinutes());
            }
            if (request.getRemark() != null) {
                timeEntry.setRemark(request.getRemark());
            }
            
            TimeEntry updated = timeEntryRepository.save(timeEntry);
            log.info("Updated existing time entry for user {} on date {}", user.getName(), request.getEntryDate());
            return timeEntryMapper.toResponse(updated);
        }

        TimeEntry timeEntry = TimeEntry.builder()
                .user(user)
                .entryDate(request.getEntryDate())
                .entryTime(request.getEntryTime())
                .exitTime(request.getExitTime())
                .hoursWorked(request.getHoursWorked())
                .status(request.getStatus())
                .breakDurationMinutes(request.getBreakDurationMinutes())
                .remark(request.getRemark())
                .build();

        TimeEntry saved = timeEntryRepository.save(timeEntry);
        log.info("Created time entry for user {} on date {}", user.getName(), request.getEntryDate());

        return timeEntryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TimeEntryResponse updateTimeEntry(UUID entryId, UpdateTimeEntryRequest request, UUID userId) {
        TimeEntry timeEntry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Time entry not found"));

        if (request.getEntryTime() != null) {
            timeEntry.setEntryTime(request.getEntryTime());
        }
        if (request.getExitTime() != null) {
            timeEntry.setExitTime(request.getExitTime());
        }
        if (request.getHoursWorked() != null) {
            timeEntry.setHoursWorked(request.getHoursWorked());
        }
        if (request.getStatus() != null) {
            timeEntry.setStatus(request.getStatus());
        }
        if (request.getBreakDurationMinutes() != null) {
            timeEntry.setBreakDurationMinutes(request.getBreakDurationMinutes());
        }
        if (request.getRemark() != null) {
            timeEntry.setRemark(request.getRemark());
        }

        TimeEntry updated = timeEntryRepository.save(timeEntry);
        log.info("Updated time entry {}", entryId);

        return timeEntryMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public TimeEntryResponse getTimeEntry(UUID entryId) {
        TimeEntry timeEntry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Time entry not found"));
        return timeEntryMapper.toResponse(timeEntry);
    }

    @Override
    @Transactional
    public void deleteTimeEntry(UUID entryId) {
        if (!timeEntryRepository.existsById(entryId)) {
            throw new ResourceNotFoundException("Time entry not found");
        }
        timeEntryRepository.deleteById(entryId);
        log.info("Deleted time entry {}", entryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TimeEntryResponse> getTimeEntriesByDate(LocalDate date, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("user.name"));
        Page<TimeEntry> entries = timeEntryRepository.findByEntryDate(date, pageable);
        return entries.map(timeEntryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeEntryResponse> getTodayTimeEntries() {
        List<TimeEntry> entries = timeEntryRepository.findAllByDate(LocalDate.now());
        return entries.stream()
                .map(timeEntryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TimeEntryResponse> getUserTimeEntries(UUID userId, LocalDate startDate, LocalDate endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("entryDate").descending());
        Page<TimeEntry> entries = timeEntryRepository.findByUser_IdAndEntryDateBetween(userId, startDate, endDate, pageable);
        return entries.map(timeEntryMapper::toResponse);
    }
}
