package com.kanban.service;

import com.kanban.model.dto.request.CreateTimeEntryRequest;
import com.kanban.model.dto.request.UpdateTimeEntryRequest;
import com.kanban.model.dto.response.TimeEntryResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimeEntryService {
    TimeEntryResponse createTimeEntry(CreateTimeEntryRequest request, UUID userId);
    TimeEntryResponse updateTimeEntry(UUID entryId, UpdateTimeEntryRequest request, UUID userId);
    TimeEntryResponse getTimeEntry(UUID entryId);
    void deleteTimeEntry(UUID entryId);
    Page<TimeEntryResponse> getTimeEntriesByDate(LocalDate date, int page, int size);
    List<TimeEntryResponse> getTodayTimeEntries();
    Page<TimeEntryResponse> getUserTimeEntries(UUID userId, LocalDate startDate, LocalDate endDate, int page, int size);
}
