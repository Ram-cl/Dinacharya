package com.kanban.service;

import com.kanban.model.dto.request.TaskImportData;
import com.kanban.model.dto.response.TaskImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FileImportService {
    TaskImportResponse importTasksFromExcel(MultipartFile file, UUID teamId, UUID userId) throws IOException;
    TaskImportResponse importTasksFromWord(MultipartFile file, UUID teamId, UUID userId) throws IOException;

    /**
     * Import from a daily attendance / tasksheet workbook (multi-sheet, one sheet per employee).
     * Uses header detection to map columns like Date, Attendance, Login, Logout, Hours, Task, Status.
     * If importDate is provided, only rows matching that date are imported.
     */
    TaskImportResponse importAttendanceSheet(MultipartFile file, UUID teamId, UUID userId) throws IOException;
    TaskImportResponse importAttendanceSheet(MultipartFile file, UUID teamId, UUID userId, LocalDate importDate) throws IOException;

    List<TaskImportData> parseExcelFile(MultipartFile file) throws IOException;
    List<TaskImportData> parseWordFile(MultipartFile file) throws IOException;
    List<TaskImportData> parseAttendanceExcel(MultipartFile file) throws IOException;
}
