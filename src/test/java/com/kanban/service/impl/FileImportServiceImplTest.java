package com.kanban.service.impl;

import com.kanban.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileImportServiceImplTest {

    @Test
    void classifyHeader_keepsCompletionStatusColumns() throws Exception {
        FileImportServiceImpl service = new FileImportServiceImpl(
            Mockito.mock(com.kanban.service.TaskService.class),
            Mockito.mock(com.kanban.service.TeamService.class),
            Mockito.mock(com.kanban.repository.TeamRepository.class),
            Mockito.mock(com.kanban.repository.UserRepository.class),
            Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            Mockito.mock(com.kanban.repository.TimeEntryRepository.class),
            Mockito.mock(com.kanban.repository.AttendanceRecordRepository.class)
        );

        Method method = FileImportServiceImpl.class.getDeclaredMethod("classifyHeader", String.class);
        method.setAccessible(true);

        assertEquals("STATUS", method.invoke(service, "Completion Status"));
        assertEquals("STATUS", method.invoke(service, "Task Completion"));
        assertEquals("STATUS", method.invoke(service, "Completed"));
    }

    @Test
    void parseStatus_mapsCompletedStatusValues() throws Exception {
        FileImportServiceImpl service = new FileImportServiceImpl(
            Mockito.mock(com.kanban.service.TaskService.class),
            Mockito.mock(com.kanban.service.TeamService.class),
            Mockito.mock(com.kanban.repository.TeamRepository.class),
            Mockito.mock(com.kanban.repository.UserRepository.class),
            Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            Mockito.mock(com.kanban.repository.TimeEntryRepository.class),
            Mockito.mock(com.kanban.repository.AttendanceRecordRepository.class)
        );

        Method method = FileImportServiceImpl.class.getDeclaredMethod("parseStatus", String.class);
        method.setAccessible(true);

        assertEquals(TaskStatus.DONE, method.invoke(service, "Completed"));
        assertEquals(TaskStatus.IN_PROGRESS, method.invoke(service, "In Progress"));
        assertEquals(TaskStatus.TODO, method.invoke(service, "Pending"));
    }
}
