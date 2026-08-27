package com.kanban.service.impl;

import com.kanban.exception.ResourceNotFoundException;
import com.kanban.model.dto.request.CreateTaskRequest;
import com.kanban.model.dto.request.TaskImportData;
import com.kanban.model.dto.response.TaskImportResponse;
import com.kanban.model.dto.response.TaskResponse;
import com.kanban.model.entity.AttendanceRecord;
import com.kanban.model.entity.Team;
import com.kanban.model.entity.User;
import com.kanban.model.entity.TimeEntry;
import com.kanban.model.enums.TaskPriority;
import com.kanban.model.enums.TaskStatus;
import com.kanban.model.enums.UserRole;
import com.kanban.model.enums.AttendanceStatus;
import com.kanban.repository.AttendanceRecordRepository;
import com.kanban.repository.TeamRepository;
import com.kanban.repository.UserRepository;
import com.kanban.repository.TimeEntryRepository;
import com.kanban.service.FileImportService;
import com.kanban.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileImportServiceImpl implements FileImportService {

    private final TaskService taskService;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TimeEntryRepository timeEntryRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Override
    @Transactional
    public TaskImportResponse importTasksFromExcel(MultipartFile file, UUID teamId, UUID userId) throws IOException {
        log.info("Starting Excel import for team: {}", teamId);

        // Verify team exists
        teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        // Read the file bytes once so we can attempt two parsing strategies.
        byte[] bytes = file.getBytes();

        // Auto-detect the multi-sheet attendance/timesheet layout first.
        List<TaskImportData> attendanceTasks = parseAttendanceExcel(bytes);
        if (!attendanceTasks.isEmpty()) {
            log.info("Detected attendance tasksheet layout ({} task rows) — importing with employee auto-create",
                    attendanceTasks.size());
            return processImportedTasks(attendanceTasks, teamId, userId, true);
        }

        // Fall back to the flat template (Title, Description, Status, Priority, Due Date, Assignee Email, Team).
        List<TaskImportData> parsedTasks = parseExcelBytes(bytes);
        return processImportedTasks(parsedTasks, teamId, userId);
    }

    @Override
    @Transactional
    public TaskImportResponse importTasksFromWord(MultipartFile file, UUID teamId, UUID userId) throws IOException {
        log.info("Starting Word import for team: {}", teamId);
        
        // Verify team exists
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        List<TaskImportData> parsedTasks = parseWordFile(file);
        return processImportedTasks(parsedTasks, teamId, userId);
    }

    @Override
    @Transactional
    public TaskImportResponse importAttendanceSheet(MultipartFile file, UUID teamId, UUID userId) throws IOException {
        log.info("Starting attendance tasksheet import for team: {}", teamId);

        teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        List<TaskImportData> parsedTasks = parseAttendanceExcel(file);
        return processImportedTasks(parsedTasks, teamId, userId, true);
    }

    @Override
    public List<TaskImportData> parseExcelFile(MultipartFile file) throws IOException {
        return parseExcelBytes(file.getBytes());
    }

    private List<TaskImportData> parseExcelBytes(byte[] bytes) throws IOException {
        List<TaskImportData> tasks = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            // Skip header row (row 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                TaskImportData task = parseExcelRow(row, i + 1);
                if (task != null) {
                    tasks.add(task);
                }
            }
        }

        log.info("Parsed {} tasks from Excel file", tasks.size());
        return tasks;
    }

    @Override
    public List<TaskImportData> parseWordFile(MultipartFile file) throws IOException {
        List<TaskImportData> tasks = new ArrayList<>();
        
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            // Parse tables in the document
            List<XWPFTable> tables = document.getTables();
            
            for (XWPFTable table : tables) {
                List<XWPFTableRow> rows = table.getRows();
                
                // Skip header row (row 0)
                for (int i = 1; i < rows.size(); i++) {
                    XWPFTableRow row = rows.get(i);
                    TaskImportData task = parseWordRow(row, i + 1);
                    if (task != null) {
                        tasks.add(task);
                    }
                }
            }

            // If no tables found, try parsing from paragraphs (simple format)
            if (tasks.isEmpty()) {
                tasks = parseWordParagraphs(document);
            }
        }

        log.info("Parsed {} tasks from Word file", tasks.size());
        return tasks;
    }

    private TaskImportData parseExcelRow(Row row, int rowNumber) {
        try {
            String title = getCellValueAsString(row.getCell(0));
            
            // Skip if title is empty
            if (title == null || title.trim().isEmpty()) {
                return null;
            }

            return TaskImportData.builder()
                    .title(title)
                    .description(getCellValueAsString(row.getCell(1)))
                    .status(parseStatus(getCellValueAsString(row.getCell(2))))
                    .priority(parsePriority(getCellValueAsString(row.getCell(3))))
                    .dueDate(getCellValueAsDate(row.getCell(4)))
                    .assigneeEmail(getCellValueAsString(row.getCell(5)))
                    .teamName(getCellValueAsString(row.getCell(6)))
                    .rowNumber(rowNumber)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing Excel row {}: {}", rowNumber, e.getMessage());
            return null;
        }
    }

    private TaskImportData parseWordRow(XWPFTableRow row, int rowNumber) {
        try {
            if (row.getTableCells().size() < 5) {
                return null;
            }

            String title = row.getCell(0).getText().trim();
            
            // Skip if title is empty
            if (title.isEmpty()) {
                return null;
            }

            return TaskImportData.builder()
                    .title(title)
                    .description(row.getCell(1).getText().trim())
                    .status(parseStatus(row.getCell(2).getText().trim()))
                    .priority(parsePriority(row.getCell(3).getText().trim()))
                    .dueDate(parseDate(row.getCell(4).getText().trim()))
                    .assigneeEmail(row.getTableCells().size() > 5 ? row.getCell(5).getText().trim() : null)
                    .teamName(row.getTableCells().size() > 6 ? row.getCell(6).getText().trim() : null)
                    .rowNumber(rowNumber)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing Word row {}: {}", rowNumber, e.getMessage());
            return null;
        }
    }

    private List<TaskImportData> parseWordParagraphs(XWPFDocument document) {
        List<TaskImportData> tasks = new ArrayList<>();
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        
        int rowNumber = 1;
        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraph.getText().trim();
            
            // Skip empty lines or headers
            if (text.isEmpty() || text.toLowerCase().startsWith("title")) {
                continue;
            }

            // Simple format: "Task Title - Description - Status - Priority - Due Date"
            String[] parts = text.split("-");
            if (parts.length >= 3) {
                TaskImportData task = TaskImportData.builder()
                        .title(parts[0].trim())
                        .description(parts.length > 1 ? parts[1].trim() : "")
                        .status(parts.length > 2 ? parseStatus(parts[2].trim()) : TaskStatus.TODO)
                        .priority(parts.length > 3 ? parsePriority(parts[3].trim()) : TaskPriority.MEDIUM)
                        .dueDate(parts.length > 4 ? parseDate(parts[4].trim()) : null)
                        .rowNumber(rowNumber++)
                        .build();
                
                tasks.add(task);
            }
        }
        
        return tasks;
    }

    private TaskImportResponse processImportedTasks(List<TaskImportData> parsedTasks, UUID teamId, UUID userId) {
        return processImportedTasks(parsedTasks, teamId, userId, false);
    }

    private TaskImportResponse processImportedTasks(List<TaskImportData> parsedTasks, UUID teamId, UUID userId,
                                                    boolean autoCreateEmployees) {
        List<String> errors = new ArrayList<>();
        int failureCount = 0;

        // Resolve each assignee once and reuse. Attendance sheets repeat the same
        // employee across many rows, so this removes most per-row DB lookups.
        // Values may be null (cached "not found") to avoid re-querying misses.
        java.util.Map<String, User> usersByEmail = new java.util.HashMap<>();
        java.util.Map<String, User> usersByName = new java.util.HashMap<>();

        List<CreateTaskRequest> requests = new ArrayList<>();

        for (TaskImportData taskData : parsedTasks) {
            // Attendance-only rows (no real task title) are not imported as tasks.
            boolean isAttendanceOnly = taskData.getTitle() != null && taskData.getTitle().startsWith("Attendance:");
            if (isAttendanceOnly) {
                log.debug("Skipping attendance-only row: {}", taskData.getRowNumber());
                continue;
            }

            // Validate required fields for task creation
            if (taskData.getTitle() == null || taskData.getTitle().trim().isEmpty()) {
                errors.add(String.format("Row %d: Title is required", taskData.getRowNumber()));
                failureCount++;
                continue;
            }

            // Find assignee: prefer email, then fall back to employee name (attendance sheets)
            UUID assigneeId = null;
            String email = taskData.getAssigneeEmail();
            if (email != null && !email.isEmpty()) {
                String key = email.toLowerCase();
                User assignee;
                if (usersByEmail.containsKey(key)) {
                    assignee = usersByEmail.get(key);
                } else {
                    assignee = userRepository.findByEmailIgnoreCase(key).orElse(null);
                    usersByEmail.put(key, assignee); // cache result (including null misses)
                }
                if (assignee != null) {
                    assigneeId = assignee.getId();
                } else {
                    log.warn("Row {}: Assignee with email {} not found", taskData.getRowNumber(), email);
                }
            }
            if (assigneeId == null && taskData.getEmployeeName() != null
                    && !taskData.getEmployeeName().isBlank()) {
                String nameKey = taskData.getEmployeeName().trim().toLowerCase();
                User assignee;
                if (usersByName.containsKey(nameKey)) {
                    assignee = usersByName.get(nameKey);
                } else {
                    assignee = resolveUserByName(taskData.getEmployeeName());
                    if (assignee == null && autoCreateEmployees) {
                        assignee = createEmployee(taskData.getEmployeeName(), taskData.getDepartment());
                    }
                    usersByName.put(nameKey, assignee); // cache result (including null misses)
                }
                if (assignee != null) {
                    assigneeId = assignee.getId();
                } else {
                    log.warn("Row {}: Employee '{}' not matched to any user",
                            taskData.getRowNumber(), taskData.getEmployeeName());
                }
            }

            requests.add(CreateTaskRequest.builder()
                    .title(taskData.getTitle())
                    .description(taskData.getDescription())
                    .remark(taskData.getRemark())
                    .status(taskData.getStatus() != null ? taskData.getStatus() : TaskStatus.TODO)
                    .priority(taskData.getPriority() != null ? taskData.getPriority() : TaskPriority.MEDIUM)
                    .deadline(taskData.getDueDate() != null ? taskData.getDueDate().atStartOfDay() : null)
                    .assignedToId(assigneeId)
                    .teamId(teamId)
                    .build());
        }

        // Single batched save instead of one round trip per row.
        List<TaskResponse> importedTasks = taskService.createTasksBulk(requests, userId);
        int successCount = importedTasks.size();

        String message = String.format("Import completed: %d succeeded, %d failed out of %d total rows",
                successCount, failureCount, parsedTasks.size());

        return TaskImportResponse.builder()
                .totalRows(parsedTasks.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .errors(errors)
                .importedTasks(importedTasks)
                .message(message)
                .build();
    }

    // ==========================================================================
    // Attendance / daily tasksheet parsing (multi-sheet, header-driven)
    // ==========================================================================

    @Override
    public List<TaskImportData> parseAttendanceExcel(MultipartFile file) throws IOException {
        return parseAttendanceExcel(file.getBytes());
    }

    private List<TaskImportData> parseAttendanceExcel(byte[] bytes) throws IOException {
        List<TaskImportData> tasks = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            int sheetCount = workbook.getNumberOfSheets();

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                if (sheet == null) {
                    continue;
                }
                // Skip summary/config sheets (e.g. "ASE Master", "Setup", "Summary") — they aren't per-employee task logs.
                String sn = sheet.getSheetName() == null ? "" : sheet.getSheetName().toLowerCase();
                if (sn.contains("master") || sn.contains("setup") || sn.contains("summary") || sn.contains("dashboard")) {
                    log.info("Skipping non-employee sheet '{}'", sheet.getSheetName());
                    continue;
                }
                try {
                    parseAttendanceSheet(sheet, formatter, tasks);
                } catch (Exception e) {
                    log.warn("Skipping sheet '{}' due to parse error: {}", sheet.getSheetName(), e.getMessage());
                }
            }
        }

        log.info("Parsed {} task rows from attendance workbook", tasks.size());
        return tasks;
    }

    private void parseAttendanceSheet(Sheet sheet, DataFormatter formatter, List<TaskImportData> tasks) {
        int headerRowIdx = findHeaderRow(sheet, formatter);
        if (headerRowIdx < 0) {
            log.info("No recognizable header row in sheet '{}' — skipping", sheet.getSheetName());
            return;
        }

        Row headerRow = sheet.getRow(headerRowIdx);
        java.util.Map<String, Integer> cols = new java.util.HashMap<>();
        for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
            String header = formatter.formatCellValue(headerRow.getCell(c));
            String key = classifyHeader(header);
            if (key != null && !cols.containsKey(key)) {
                cols.put(key, c);
            }
        }

        String sheetEmployee = detectSheetEmployeeName(sheet, formatter, headerRowIdx, cols);
        String[] deptAndName = splitDepartmentAndName(sheetEmployee);
        String sheetDepartment = deptAndName[0];
        String sheetName = deptAndName[1];

        // Check if this is a mixed sheet (has TASK column) or attendance-only
        boolean hasTitleColumn = cols.containsKey("TASK");

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) {
                continue;
            }

            LocalDate date = null;
            if (cols.containsKey("DATE")) {
                date = getCellValueAsDate(row.getCell(cols.get("DATE")));
            }
            if (date == null) {
                continue;
            }

            String attendance = cellText(row, cols.get("ATTENDANCE"), formatter);
            if (attendance == null || attendance.isBlank()) {
                attendance = cellText(row, cols.get("REMARK"), formatter);
            }
            String login = cellText(row, cols.get("LOGIN"), formatter);
            String logout = cellText(row, cols.get("LOGOUT"), formatter);
            String hours = cellText(row, cols.get("HOURS"), formatter);

            String rowEmployee = cellText(row, cols.get("NAME"), formatter);
            String employeeName = (rowEmployee != null && !rowEmployee.isBlank()) ? rowEmployee : sheetName;
            String department = sheetDepartment;
            if (cols.containsKey("DEPARTMENT")) {
                String rowDept = cellText(row, cols.get("DEPARTMENT"), formatter);
                if (rowDept != null && !rowDept.isBlank()) {
                    department = rowDept;
                }
            }

            boolean hasTimes = (login != null && !login.isBlank()) || (logout != null && !logout.isBlank());
            boolean hasAttendanceMark = attendance != null && !attendance.isBlank();
            if (!hasTimes && !hasAttendanceMark) {
                continue;
            }

            String taskTitle = null;
            if (hasTitleColumn) {
                taskTitle = cellText(row, cols.get("TASK"), formatter);
            }

            String title = (taskTitle != null && !taskTitle.isBlank())
                ? taskTitle
                : ("Attendance: " + (hasAttendanceMark ? attendance : "Present"));

            TaskImportData data = TaskImportData.builder()
                    .title(title)
                    .dueDate(date)
                    .employeeName(employeeName)
                    .department(department)
                    .remark(attendance)
                    .attendance(attendance)
                    .description(composeWorkDetails(login, logout, hours))
                    .sheetName(sheet.getSheetName())
                    .rowNumber(r + 1)
                    .loginTime(login)
                    .logoutTime(logout)
                    .hoursWorked(hours)
                    .build();

            tasks.add(data);
        }
    }

    /** Splits a tab/label like "ASE Pattima kalyani" into ["ASE", "Pattima kalyani"]. */
    private String[] splitDepartmentAndName(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{null, null};
        }
        String s = raw.trim();
        int sp = s.indexOf(' ');
        if (sp > 0) {
            String first = s.substring(0, sp);
            String rest = s.substring(sp + 1).trim();
            // Treat a short, all-uppercase leading token (e.g. "ASE") as the department code.
            if (!rest.isEmpty() && first.length() <= 5 && first.equals(first.toUpperCase())) {
                return new String[]{first, rest};
            }
        }
        return new String[]{null, s};
    }

    private String composeWorkDetails(String login, String logout, String hours) {
        List<String> parts = new ArrayList<>();
        if (login != null && !login.isBlank()) parts.add("Login: " + login.trim());
        if (logout != null && !logout.isBlank()) parts.add("Logout: " + logout.trim());
        if (hours != null && !hours.isBlank()) parts.add("Hours: " + hours.trim());
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    /** Creates a minimal employee profile from a tasksheet row (name + department). */
    private User createEmployee(String rawName, String department) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            return null;
        }
        String email = buildEmployeeEmail(name);

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(name)
                .role(UserRole.USER)
                .department((department != null && !department.isBlank()) ? department.trim() : null)
                .isActive(true)
                .lastActive(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);
        log.info("Auto-created employee '{}' ({}) from tasksheet import", name, email);
        return saved;
    }

    /** Builds a unique, deterministic email from an employee name, e.g. "Akkipalli Sri Usha" -> akkipalli.sri.usha@imported.local */
    private String buildEmployeeEmail(String name) {
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.|\\.$", "");
        if (slug.isEmpty()) {
            slug = "employee";
        }
        String base = slug + "@imported.local";
        String candidate = base;
        int suffix = 1;
        while (userRepository.findByEmailIgnoreCase(candidate).isPresent()) {
            candidate = slug + suffix + "@imported.local";
            suffix++;
        }
        return candidate;
    }

    /** Scans the first rows of a sheet to find the row that looks like a header. */
    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int scanLimit = Math.min(sheet.getLastRowNum(), 15);
        int bestRow = -1;
        int bestScore = 0;

        for (int r = sheet.getFirstRowNum(); r <= scanLimit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                String key = classifyHeader(formatter.formatCellValue(row.getCell(c)));
                if (key != null) {
                    keys.add(key);
                }
            }
            boolean attendanceHeader = keys.contains("DATE")
                && (keys.contains("LOGIN") || keys.contains("LOGOUT") || keys.contains("ATTENDANCE") || keys.contains("NAME"));
            boolean taskHeader = keys.contains("TASK") && keys.size() >= 2;
            if ((attendanceHeader || taskHeader) && keys.size() > bestScore) {
                bestScore = keys.size();
                bestRow = r;
            }
        }
        return bestRow;
    }

    /** Maps a header cell's text to a canonical column key, or null if unrecognized. */
    private String classifyHeader(String raw) {
        if (raw == null) {
            return null;
        }
        String h = raw.trim().toLowerCase();
        if (h.isEmpty()) {
            return null;
        }
        if (h.contains("action")) return null; // ignore ACTIONS column
        if (h.contains("scorecard") || h.contains("completion") || h.contains("completed")) return null; // ignore Performance Scorecard block
        if (h.contains("employee") || h.contains("associate") || h.equals("name") || h.contains("emp name") || h.contains("staff")) return "NAME";
        if (h.contains("depart") || h.equals("dept")) return "DEPARTMENT";
        // "Task Description" must be the task title, so check "task" before "description"
        if (h.contains("task") || h.contains("activity") || h.contains("assignment") || h.contains("work done")) return "TASK";
        if (h.contains("description") || h.contains("details")) return "DESCRIPTION";
        if (h.contains("date")) return "DATE";
        if (h.contains("priorit")) return "PRIORITY";
        if (h.contains("status")) return "STATUS";
        if (h.contains("attend") || h.equals("present") || h.contains("presence")) return "ATTENDANCE";
        if (h.contains("remark") || h.contains("note") || h.contains("comment")) return "REMARK";
        if (h.contains("login") || h.contains("in time") || h.contains("check in") || h.contains("check-in")) return "LOGIN";
        if (h.contains("logout") || h.contains("out time") || h.contains("check out") || h.contains("check-out")) return "LOGOUT";
        if (h.contains("hour") || h.contains("duration") || h.contains("worked")) return "HOURS";
        return null;
    }

    /** Tries to find the employee name from a NAME column value above the header, a labelled cell, or the sheet tab. */
    private String detectSheetEmployeeName(Sheet sheet, DataFormatter formatter, int headerRowIdx,
                                           java.util.Map<String, Integer> cols) {
        // Look for a "Name: X" style label in the rows above the header
        for (int r = sheet.getFirstRowNum(); r < headerRowIdx; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                String text = formatter.formatCellValue(row.getCell(c));
                if (text == null) {
                    continue;
                }
                String t = text.trim();
                if (t.toLowerCase().startsWith("name") && t.contains(":")) {
                    String candidate = t.substring(t.indexOf(':') + 1).trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
                if (t.toUpperCase().startsWith("ASE ") && t.length() > 4) {
                    return t;
                }
            }
        }
        // Fall back to the sheet tab name if it is not generic
        String sheetName = sheet.getSheetName();
        if (sheetName != null && !sheetName.matches("(?i)sheet\\d+")) {
            return sheetName.trim();
        }
        return null;
    }

    private String cellText(Row row, Integer colIdx, DataFormatter formatter) {
        if (colIdx == null || row == null) {
            return null;
        }
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            return null;
        }
        String v = formatter.formatCellValue(cell);
        return v == null ? null : v.trim();
    }

    private User resolveUserByName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim().replaceFirst("(?i)^name\\s*[:\\-]\\s*", "").trim();
        if (name.isEmpty()) {
            return null;
        }
        String cleaned = name.replaceFirst("(?i)^ASE\\s+", "").trim();

        User user = userRepository.findByNameIgnoreCase(name).orElse(null);
        if (user == null && !cleaned.equalsIgnoreCase(name)) {
            user = userRepository.findByNameIgnoreCase(cleaned).orElse(null);
        }
        if (user == null) {
            String query = cleaned.isEmpty() ? name : cleaned;
            List<User> matches = userRepository.searchByNameContaining(query);
            if (matches.size() == 1) {
                user = matches.get(0);
            }
        }
        return user;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return null;
            default:
                return null;
        }
    }

    private LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            } else if (cell.getCellType() == CellType.STRING) {
                return parseDate(cell.getStringCellValue());
            }
        } catch (Exception e) {
            log.error("Error parsing date from cell: {}", e.getMessage());
        }

        return null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            // Try common date formats
            String[] formats = {
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "dd-MM-yyyy",
                "MM-dd-yyyy"
            };

            for (String format : formats) {
                try {
                    return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern(format));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.error("Error parsing date '{}': {}", dateStr, e.getMessage());
        }

        return null;
    }

    private TaskStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return TaskStatus.TODO;
        }

        try {
            return TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to match partial strings
            String normalized = status.trim().toLowerCase();
            if (normalized.contains("todo") || normalized.contains("to do") || normalized.contains("pending")) {
                return TaskStatus.TODO;
            } else if (normalized.contains("progress") || normalized.contains("working")) {
                return TaskStatus.IN_PROGRESS;
            } else if (normalized.contains("review")) {
                return TaskStatus.IN_REVIEW;
            } else if (normalized.contains("done") || normalized.contains("complete")) {
                return TaskStatus.DONE;
            }
            return TaskStatus.TODO;
        }
    }

    private TaskPriority parsePriority(String priority) {
        if (priority == null || priority.trim().isEmpty()) {
            return TaskPriority.MEDIUM;
        }

        try {
            return TaskPriority.valueOf(priority.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to match partial strings
            String normalized = priority.trim().toLowerCase();
            if (normalized.contains("low")) {
                return TaskPriority.LOW;
            } else if (normalized.contains("high") || normalized.contains("critical")) {
                return TaskPriority.HIGH;
            } else if (normalized.contains("urgent")) {
                return TaskPriority.URGENT;
            }
            return TaskPriority.MEDIUM;
        }
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Creates a TimeEntry record from task import data (for attendance/timesheet imports).
     * Parses login/logout times and calculates hours worked.
     */
    private void createTimeEntryFromTaskData(TaskImportData taskData, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalTime entryTime = null;
        LocalTime exitTime = null;
        Double hoursWorked = null;

        // Parse entry time (login)
        if (taskData.getLoginTime() != null && !taskData.getLoginTime().isBlank()) {
            entryTime = parseTimeString(taskData.getLoginTime());
        }

        // Parse exit time (logout)
        if (taskData.getLogoutTime() != null && !taskData.getLogoutTime().isBlank()) {
            exitTime = parseTimeString(taskData.getLogoutTime());
        }

        // Calculate hours worked
        if (entryTime != null && exitTime != null) {
            hoursWorked = calculateHoursWorked(entryTime, exitTime);
        } else if (taskData.getHoursWorked() != null && !taskData.getHoursWorked().isBlank()) {
            hoursWorked = parseHoursWorked(taskData.getHoursWorked());
        }

        // Determine attendance status
        AttendanceStatus status = parseAttendanceStatus(taskData.getAttendance());

        // Check if entry already exists - update if it does, create if not
        var existingEntry = timeEntryRepository.findByUserAndEntryDate(user, taskData.getDueDate());
        
        if (existingEntry.isPresent()) {
            // Update existing entry
            TimeEntry timeEntry = existingEntry.get();
            timeEntry.setEntryTime(entryTime);
            timeEntry.setExitTime(exitTime);
            timeEntry.setHoursWorked(hoursWorked);
            timeEntry.setStatus(status);
            timeEntry.setRemark(taskData.getRemark() != null ? taskData.getRemark() : taskData.getAttendance());
            
            timeEntryRepository.save(timeEntry);
            log.info("Updated time entry for user {} on date {}", user.getName(), taskData.getDueDate());
        } else {
            // Create new entry
            TimeEntry timeEntry = TimeEntry.builder()
                    .user(user)
                    .entryDate(taskData.getDueDate())
                    .entryTime(entryTime)
                    .exitTime(exitTime)
                    .hoursWorked(hoursWorked)
                    .status(status)
                    .remark(taskData.getRemark() != null ? taskData.getRemark() : taskData.getAttendance())
                    .build();

            timeEntryRepository.save(timeEntry);
            log.info("Created time entry for user {} on date {}", user.getName(), taskData.getDueDate());
        }
    }

    /**
     * Writes the same imported row into attendance_records, which powers the
     * moderator attendance dashboard (distinct from time_entries).
     */
    private void upsertAttendanceRecord(TaskImportData taskData, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalTime entryTime = parseTimeString(taskData.getLoginTime());
        LocalTime exitTime = parseTimeString(taskData.getLogoutTime());
        LocalDate workDate = taskData.getDueDate();
        AttendanceStatus dashboardStatus = toDashboardAttendanceStatus(taskData, entryTime, exitTime);

        LocalDateTime entryDateTime = entryTime != null ? LocalDateTime.of(workDate, entryTime) : null;
        LocalDateTime exitDateTime = exitTime != null ? LocalDateTime.of(workDate, exitTime) : null;

        AttendanceRecord record = attendanceRecordRepository
            .findByUserIdAndWorkDate(userId, workDate)
            .orElse(null);

        if (record == null) {
            record = AttendanceRecord.builder()
                .user(user)
                .workDate(workDate)
                .entryTime(entryDateTime)
                .exitTime(exitDateTime)
                .status(dashboardStatus)
                .build();
        } else {
            if (entryDateTime != null) {
                record.setEntryTime(entryDateTime);
            }
            if (exitDateTime != null) {
                record.setExitTime(exitDateTime);
            }
            record.setStatus(dashboardStatus);
        }

        attendanceRecordRepository.save(record);
        log.info("Upserted attendance record for {} on {}", user.getName(), workDate);
    }

    private AttendanceStatus toDashboardAttendanceStatus(
        TaskImportData taskData,
        LocalTime entryTime,
        LocalTime exitTime
    ) {
        AttendanceStatus parsed = parseAttendanceStatus(taskData.getAttendance());
        if (parsed == AttendanceStatus.ABSENT || parsed == AttendanceStatus.LEAVE) {
            return AttendanceStatus.OFFLINE;
        }
        if (entryTime != null
            || parsed == AttendanceStatus.PRESENT
            || parsed == AttendanceStatus.WORK_FROM_HOME
            || parsed == AttendanceStatus.ONLINE
            || parsed == AttendanceStatus.ON_BREAK
            || parsed == AttendanceStatus.HALF_DAY) {
            return AttendanceStatus.ONLINE;
        }
        return AttendanceStatus.OFFLINE;
    }

    /**
     * Parses a time string like "10:30 AM" or "22:30" into LocalTime.
     */
    private LocalTime parseTimeString(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }

        try {
            String normalized = timeStr.trim().toUpperCase();

            // Try standard formats
            String[] formats = {
                "HH:mm:ss",
                "HH:mm",
                "hh:mm:ss a",
                "hh:mm a"
            };

            for (String format : formats) {
                try {
                    return LocalTime.parse(normalized, java.time.format.DateTimeFormatter.ofPattern(format));
                } catch (Exception ignored) {
                }
            }

            log.warn("Could not parse time string: {}", timeStr);
            return null;
        } catch (Exception e) {
            log.error("Error parsing time '{}': {}", timeStr, e.getMessage());
            return null;
        }
    }

    /**
     * Parses hours worked string like "7:30", "7.5", or "7 hours 30 minutes".
     */
    private Double parseHoursWorked(String hoursStr) {
        if (hoursStr == null || hoursStr.isBlank()) {
            return null;
        }

        try {
            String normalized = hoursStr.trim().toLowerCase();

            // Handle "7:30" format (7 hours 30 minutes = 7.5 hours)
            if (normalized.contains(":")) {
                String[] parts = normalized.split(":");
                if (parts.length == 2) {
                    int hours = Integer.parseInt(parts[0].trim());
                    int minutes = Integer.parseInt(parts[1].trim());
                    return hours + (minutes / 60.0);
                }
            }

            // Handle "7.5" format
            if (normalized.contains(".")) {
                return Double.parseDouble(normalized);
            }

            // Handle "7 hours 30 minutes" format
            if (normalized.contains("hour") || normalized.contains("minute")) {
                int hours = 0;
                int minutes = 0;

                if (normalized.contains("hour")) {
                    String[] parts = normalized.split("hour");
                    hours = Integer.parseInt(parts[0].trim());
                }

                if (normalized.contains("minute")) {
                    String[] parts = normalized.split("minute");
                    String minPart = parts[0].trim();
                    if (minPart.contains(" ")) {
                        minPart = minPart.substring(minPart.lastIndexOf(" ") + 1);
                    }
                    minutes = Integer.parseInt(minPart);
                }

                return hours + (minutes / 60.0);
            }

            // Try direct integer/float parse
            return Double.parseDouble(normalized);
        } catch (Exception e) {
            log.warn("Could not parse hours worked '{}': {}", hoursStr, e.getMessage());
            return null;
        }
    }

    /**
     * Calculates hours worked between entry and exit times.
     */
    private Double calculateHoursWorked(LocalTime entryTime, LocalTime exitTime) {
        if (entryTime == null || exitTime == null) {
            return null;
        }

        try {
            long minutes = java.time.temporal.ChronoUnit.MINUTES.between(entryTime, exitTime);
            if (minutes < 0) {
                // Handle case where exit time is next day (e.g., 23:00 to 01:00)
                minutes = minutes + (24 * 60);
            }
            return Math.round((minutes / 60.0) * 100.0) / 100.0;
        } catch (Exception e) {
            log.error("Error calculating hours: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses attendance status from string like "Present", "Absent", etc.
     */
    private AttendanceStatus parseAttendanceStatus(String status) {
        if (status == null || status.isBlank()) {
            return AttendanceStatus.PRESENT;
        }

        try {
            return AttendanceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String normalized = status.trim().toLowerCase();
            if (normalized.contains("present")) {
                return AttendanceStatus.PRESENT;
            } else if (normalized.contains("absent")) {
                return AttendanceStatus.ABSENT;
            } else if (normalized.contains("half")) {
                return AttendanceStatus.HALF_DAY;
            } else if (normalized.contains("leave")) {
                return AttendanceStatus.LEAVE;
            } else if (normalized.contains("work from home") || normalized.contains("wfh")) {
                return AttendanceStatus.WORK_FROM_HOME;
            } else if (normalized.contains("online")) {
                return AttendanceStatus.ONLINE;
            } else if (normalized.contains("offline")) {
                return AttendanceStatus.OFFLINE;
            } else if (normalized.contains("away")) {
                return AttendanceStatus.AWAY;
            }
            return AttendanceStatus.PRESENT;
        }
    }
}
