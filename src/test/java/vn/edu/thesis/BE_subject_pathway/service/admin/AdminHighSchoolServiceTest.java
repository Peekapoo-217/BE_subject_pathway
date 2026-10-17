package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminHighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AdminHighSchoolRepository;

@ExtendWith(MockitoExtension.class)
class AdminHighSchoolServiceTest {
    @Mock private AdminHighSchoolRepository repository;
    @Mock private ProvinceCatalogService provinces;

    private AdminHighSchoolService service;

    @BeforeEach
    void setUp() {
        service = new AdminHighSchoolService(repository, provinces,
                new CursorCodec("01234567890123456789012345678901"));
    }

    @Test
    void createUsesSuggestedInitialsAndPersistsAuditAndGraphOutboxThroughRepository() {
        when(provinces.requireProvinceName("66")).thenReturn("Tỉnh Đắk Lắk");
        when(repository.proposedIdentityExists("Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk", null)).thenReturn(false);
        when(repository.codeInUse("TĐT")).thenReturn(false);
        AdminHighSchoolDto created = school("TĐT", "Trường THPT Tôn Đức Thắng");
        when(repository.findActive("TĐT")).thenReturn(Optional.of(created));

        AdminHighSchoolDto result = service.create(null, " Trường   THPT Tôn Đức Thắng ", "66", "actor-id");

        assertEquals("TĐT", result.code());
        verify(repository).lockIdentity("Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk");
        verify(repository).lockCode("TĐT");
        verify(repository).create("TĐT", "Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk", "actor-id");
    }

    @Test
    void createRejectsCodeUsedByADeletedOrHistoricalSchool() {
        when(provinces.requireProvinceName("66")).thenReturn("Tỉnh Đắk Lắk");
        when(repository.proposedIdentityExists("Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk", null)).thenReturn(false);
        when(repository.codeInUse("TĐT")).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("TĐT", "Trường THPT Tôn Đức Thắng", "66", "actor-id"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(repository, never()).create("TĐT", "Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk", "actor-id");
    }

    @Test
    void updateAllowsCodeChangeAndDelegatesHistoryAndGraphOutboxRecording() {
        when(repository.findActive("OLD")).thenReturn(Optional.of(school("OLD", "Trường THPT Cũ")));
        when(provinces.requireProvinceName("66")).thenReturn("Tỉnh Đắk Lắk");
        when(repository.proposedIdentityExists("Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk", "OLD")).thenReturn(false);
        when(repository.codeInUse("TĐT")).thenReturn(false);
        when(repository.update("OLD", "TĐT", "Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk", "actor-id"))
                .thenReturn(1);
        when(repository.findActive("TĐT")).thenReturn(Optional.of(school("TĐT", "Trường THPT Tôn Đức Thắng")));

        AdminHighSchoolDto result = service.update("OLD", "TĐT", "Trường THPT Tôn Đức Thắng", "66", "actor-id");

        assertEquals("TĐT", result.code());
        verify(repository).update("OLD", "TĐT", "Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk", "actor-id");
    }

    @Test
    void listUsesSignedKeysetCursorAndCapsPageSize() {
        CursorCodec codec = new CursorCodec("01234567890123456789012345678901");
        when(repository.listActive("A01", 101)).thenReturn(List.of(school("A02", "Trường THPT B")));
        AdminHighSchoolService cursorService = new AdminHighSchoolService(repository, provinces, codec);

        var page = cursorService.list(500, codec.encode("A01"));

        assertEquals(1, page.items().size());
        verify(repository).listActive("A01", 101);
    }

    @Test
    void softDeleteReturnsNotFoundWhenSchoolIsAlreadyDeleted() {
        when(repository.softDelete("TĐT", "actor-id")).thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.softDelete("TĐT", "actor-id"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void restoresDeletedSchoolAndQueuesGraphProjection() {
        when(repository.lockForRestore("TDT")).thenReturn(Map.of("deletedAt", Instant.EPOCH, "deletedBy", "actor-id"));
        when(repository.restore("TDT", Instant.EPOCH, "actor-id")).thenReturn(1);
        when(repository.enqueueRestoreGraph("TDT")).thenReturn("restore-job");

        var result = service.restore("TDT", "super-admin");

        assertEquals("TDT", result.schoolCode());
        assertEquals("PENDING", result.graphSyncStatus());
        assertEquals("restore-job", result.graphSyncJobId());
        verify(repository).auditRestored("super-admin", "TDT", "restore-job");
    }

    @Test
    void repeatedSchoolRestoreReplaysExistingGraphJobWithoutEnqueueing() {
        Map<String, Object> activeSchool = new java.util.HashMap<>();
        activeSchool.put("deletedAt", null);
        when(repository.lockForRestore("TDT")).thenReturn(activeSchool);
        when(repository.restoreReplay("TDT")).thenReturn(Map.of(
                "graphSyncStatus", "RETRY", "graphSyncJobId", "existing-job"));

        var result = service.restore("TDT", "super-admin");

        assertEquals("RETRY", result.graphSyncStatus());
        assertEquals("existing-job", result.graphSyncJobId());
        verify(repository, never()).restore(anyString(), any(), any());
        verify(repository, never()).enqueueRestoreGraph(anyString());
    }

    private static AdminHighSchoolDto school(String code, String name) {
        return new AdminHighSchoolDto(code, name, "Tỉnh Đắk Lắk", Instant.EPOCH, Instant.EPOCH);
    }
}
