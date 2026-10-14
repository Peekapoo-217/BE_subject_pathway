package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminDraftGroupDto;
import vn.edu.thesis.BE_subject_pathway.repository.admin.SchoolDocumentRepository;

class SchoolDocumentServiceTest {
    private final SchoolDocumentRepository repository = mock(SchoolDocumentRepository.class);
    private final R2StorageService storage = mock(R2StorageService.class);
    private final CursorCodec cursors = mock(CursorCodec.class);
    private final ProvinceCatalogService provinces = mock(ProvinceCatalogService.class);
    private final SchoolDocumentService service = new SchoolDocumentService(repository, storage, cursors, provinces, 50L * 1024 * 1024);

    @Test
    void uploadsPngWithActualContentTypeAndExtension() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        var file = new MockMultipartFile("file", "groups.png", "image/png", png);

        when(repository.schoolCodeExists("SCHOOL")).thenReturn(true);
        service.upload("user", file, "Groups", "2026", "SCHOOL", null, null, null);

        verify(storage).put(endsWith(".png"), any(InputStream.class), eq((long) png.length), eq("image/png"));
        verify(repository).createDocument(anyString(), anyString(), eq("user"), eq("SCHOOL"), isNull(), isNull(), isNull(),
                eq("Groups"), eq("2026"), endsWith(".png"), eq("groups.png"), eq("image/png"),
                eq((long) png.length), anyString());
    }

    @Test
    void uploadsJpegAsNewDocumentAndPassesItToR2AsImage() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", output);
        var file = new MockMultipartFile("file", "groups.jpg", "image/jpeg", output.toByteArray());

        when(repository.schoolCodeExists("SCHOOL")).thenReturn(true);
        service.upload("user", file, "Groups", "2026", "SCHOOL", null, null, null);

        verify(storage).put(endsWith(".jpg"), any(InputStream.class), eq(file.getSize()), eq("image/jpeg"));
        verify(repository).createDocument(anyString(), anyString(), eq("user"), eq("SCHOOL"), isNull(), isNull(), isNull(),
                eq("Groups"), eq("2026"), endsWith(".jpg"), eq("groups.jpg"), eq("image/jpeg"),
                eq(file.getSize()), anyString());
    }

    @Test
    void rejectsSpoofedImageBeforeStorageOrDatabaseWrite() {
        var file = new MockMultipartFile("file", "groups.png", "image/png", "not an image".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.upload("user", file, "Groups", "2026", "SCHOOL", null, null, null));
        verifyNoInteractions(storage, repository);
    }

    @Test
    void uploadsNewSchoolWithDerivedCodeAndResolvedProvince() throws Exception {
        var file = new MockMultipartFile("file", "groups.pdf", "application/pdf", "%PDF-1.7 groups".getBytes());
        when(provinces.requireProvinceName("66")).thenReturn("Tỉnh Đắk Lắk");
        when(repository.findSchoolByCode("TĐT")).thenReturn(null);
        when(repository.proposedSchoolCodeConflicts("TĐT", "Trường THPT Tôn Đức Thắng", "Tỉnh Đắk Lắk", null))
                .thenReturn(false);

        service.upload("user", file, "Groups", "2026", "", "Trường THPT Tôn Đức Thắng", "66", "");

        verify(repository).createDocument(anyString(), anyString(), eq("user"), isNull(),
                eq("Trường THPT Tôn Đức Thắng"), eq("Tỉnh Đắk Lắk"), eq("TĐT"), eq("Groups"), eq("2026"),
                endsWith(".pdf"), eq("groups.pdf"), eq("application/pdf"), eq((long) file.getSize()), anyString());
    }

    @Test
    void suggestsReusingAnExistingSchoolByNameAndProvinceEvenWhenItsCodeDiffers() {
        when(provinces.requireProvinceName("66")).thenReturn("Province");
        when(repository.findSchoolByNameAndLocation("Trường THPT Tôn Đức Thắng", "Province"))
                .thenReturn(Map.of("code", "TONDUCTHANG", "name", "Trường THPT Tôn Đức Thắng", "location", "Province"));

        var suggestion = service.suggestSchoolCode("Trường THPT Tôn Đức Thắng", "66", null);

        assertEquals(false, suggestion.available());
        assertEquals(true, suggestion.canReuseExistingSchool());
        assertEquals("TONDUCTHANG", suggestion.existingSchoolCode());
    }

    @Test
    void rejectsCreatingASecondSchoolWithTheSameNameAndProvince() {
        var file = new MockMultipartFile("file", "groups.pdf", "application/pdf", "%PDF-1.7 groups".getBytes());
        when(provinces.requireProvinceName("66")).thenReturn("Province");
        when(repository.findSchoolByNameAndLocation("Trường THPT Tôn Đức Thắng", "Province"))
                .thenReturn(Map.of("code", "TONDUCTHANG", "name", "Trường THPT Tôn Đức Thắng", "location", "Province"));

        assertThrows(IllegalArgumentException.class, () -> service.upload("user", file, "Groups", "2026", "",
                "Trường THPT Tôn Đức Thắng", "66", "TĐT"));

        verifyNoInteractions(storage);
        verify(repository, never()).createDocument(anyString(), anyString(), anyString(), any(), any(), any(), any(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void rejectsAcademicYearRangeInsteadOfSingleCalendarYear() {
        var file = new MockMultipartFile("file", "groups.pdf", "application/pdf", "%PDF-1.7 groups".getBytes());
        assertThrows(IllegalArgumentException.class,
                () -> service.upload("user", file, "Groups", "2026-2027", "SCHOOL", null, null, null));
        verifyNoInteractions(storage, repository);
    }

    @Test
    void onlyFailedLatestJobCanBeRetried() {
        when(repository.retryLatestFailedJob("document")).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.retryExtraction("document"));
    }

    @Test
    void superAdminSoftDeleteCancelsJobsAndArchivesUnpublishedDrafts() {
        when(repository.softDeleteDocument("document", "admin")).thenReturn(1);

        service.softDeleteDocument("document", "admin");

        var order = inOrder(repository);
        order.verify(repository).softDeleteDocument("document", "admin");
        order.verify(repository).cancelActiveJobs("document");
        order.verify(repository).deleteUnpublishedDraftsForDocument("document", "admin");
        order.verify(repository).auditDocumentSoftDeleted("document", "admin", "DOCUMENT_SOFT_DELETED");
    }

    @Test
    void approvingAdminDeleteAlsoCancelsJobsAndCleansDrafts() {
        when(repository.approveDelete("document", "super-admin")).thenReturn(1);

        service.approveDelete("document", "super-admin");

        verify(repository).cancelActiveJobs("document");
        verify(repository).deleteUnpublishedDraftsForDocument("document", "super-admin");
        verify(repository).auditDeleteApproved("document", "super-admin");
    }

    @Test
    void singleDraftDeleteReturnsConflictWhileSourceIsProcessing() {
        when(repository.hardDeleteDraft("draft", "super-admin")).thenReturn(-1);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.hardDeleteDraft("draft", "super-admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void draftGroupsUseASeparateKeysetCursor() {
        when(repository.listDraftGroups("DRAFT", null, null, 3)).thenReturn(List.of(
                new AdminDraftGroupDto("S:A", "School A", "A", null, "2026-2027", 2, 1),
                new AdminDraftGroupDto("S:B", "School B", "B", null, "2026-2027", 1, 1),
                new AdminDraftGroupDto("S:C", "School C", "C", null, "2026-2027", 1, 1)));
        when(cursors.encode(anyString())).thenReturn("signed-cursor");

        var page = service.listDraftGroups(2, null, "DRAFT");

        assertEquals(2, page.items().size());
        assertEquals(true, page.hasNext());
        assertEquals("signed-cursor", page.nextCursor());
        verify(cursors).encode(contains("S:B"));
    }

    @Test
    void draftGroupCursorIsBoundToItsSchoolYearAndRoleFilter() {
        when(cursors.decode("signed")).thenReturn("{\"groupKey\":\"S:A\",\"academicYear\":\"2026-2027\",\"status\":\"DRAFT\"}");
        when(repository.listDraftGroups("DRAFT", "S:A", "2026-2027", 3)).thenReturn(List.of());

        var page = service.listDraftGroups(2, "signed", "DRAFT");

        assertEquals(false, page.hasNext());
        verify(repository).listDraftGroups("DRAFT", "S:A", "2026-2027", 3);
    }

    @Test
    void draftItemCursorIsBoundToItsSchoolYearAndRoleFilter() {
        when(cursors.decode("signed")).thenReturn("{\"createdAt\":\"2026-10-14T00:00:00Z\",\"id\":\"draft-id\","
                + "\"groupKey\":\"S:A\",\"academicYear\":\"2026-2027\",\"status\":\"DRAFT\"}");
        when(repository.listDraftsForGroup("S:A", "2026-2027", "DRAFT", "2026-10-14T00:00:00Z", "draft-id", 3))
                .thenReturn(List.of());

        var page = service.listDraftsForGroup(2, "signed", "DRAFT", "S:A", "2026-2027");

        assertEquals(false, page.hasNext());
        verify(repository).listDraftsForGroup("S:A", "2026-2027", "DRAFT", "2026-10-14T00:00:00Z", "draft-id", 3);
    }

    @Test
    void editDraftAcceptsOneToFourDistinctCatalogSubjects() {
        when(repository.lockDraftDocumentForReview("draft")).thenReturn(true);
        when(repository.editDraft("draft", "A01", "Nhóm A01", "[\"MATH\"]")).thenReturn(1);
        when(repository.subjectExists("MATH")).thenReturn(true);

        service.editDraft("draft", "A01", "Nhóm A01", "[\"MATH\"]");

        verify(repository).editDraft("draft", "A01", "Nhóm A01", "[\"MATH\"]");
    }

    @Test
    void editDraftAcceptsFourDistinctCatalogSubjects() {
        when(repository.lockDraftDocumentForReview("draft")).thenReturn(true);
        when(repository.editDraft("draft", "A01", "Nhóm A01",
                "[\"MATH\",\"LITERATURE\",\"PHYSICS\",\"CHEMISTRY\"]")).thenReturn(1);
        for (String code : List.of("MATH", "LITERATURE", "PHYSICS", "CHEMISTRY"))
            when(repository.subjectExists(code)).thenReturn(true);

        service.editDraft("draft", "A01", "Nhóm A01", "[\"MATH\",\"LITERATURE\",\"PHYSICS\",\"CHEMISTRY\"]");

        verify(repository).editDraft("draft", "A01", "Nhóm A01", "[\"MATH\",\"LITERATURE\",\"PHYSICS\",\"CHEMISTRY\"]");
    }

    @Test
    void editDraftRejectsDuplicateSubjectsBeforeWriting() {
        assertThrows(IllegalArgumentException.class,
                () -> service.editDraft("draft", "A01", "Nhóm A01", "[\"MATH\",\"MATH\"]"));

        verify(repository, never()).editDraft(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void editDraftRejectsMoreThanFourSubjectsBeforeWriting() {
        assertThrows(IllegalArgumentException.class,
                () -> service.editDraft("draft", "A01", "Nhóm A01",
                        "[\"MATH\",\"LITERATURE\",\"PHYSICS\",\"CHEMISTRY\",\"BIOLOGY\"]"));

        verify(repository, never()).editDraft(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void publishesAllSubmittedGroupsAndQueuesOneGraphProjection() {
        var draft = new SchoolDocumentRepository.DraftRow("draft-1", "doc-1", "A01", "Nhóm A01",
                "[\"MATH\"]", "SCHOOL", null, "2026-2027", "SUBMITTED");
        var secondDraft = new SchoolDocumentRepository.DraftRow("draft-2", "doc-2", "B02", "Nhóm B02",
                "[\"PHYSICS\"]", "SCHOOL", null, "2026-2027", "SUBMITTED");
        when(repository.lockDocumentsForPublication("S:SCHOOL", "2026-2027")).thenReturn(2);
        when(repository.hasActiveExtractionForGroup("S:SCHOOL", "2026-2027")).thenReturn(false);
        when(repository.lockDraftsForPublication("S:SCHOOL", "2026-2027")).thenReturn(List.of(draft, secondDraft));
        when(repository.subjectExists("MATH")).thenReturn(true);
        when(repository.subjectExists("PHYSICS")).thenReturn(true);
        when(repository.publishedGroupCodes("SCHOOL", "2026-2027")).thenReturn(java.util.Set.of());

        var result = service.publishDraftGroup("S:SCHOOL", "2026-2027", List.of("draft-1", "draft-2"), "super-admin");

        assertEquals(2, result.publishedGroupCount());
        assertEquals("PENDING", result.graphSyncStatus());
        verify(repository).addSubjectGroup("SCHOOL", "A01", "Nhóm A01", "MATH", "2026-2027");
        verify(repository).addSubjectGroup("SCHOOL", "B02", "Nhóm B02", "PHYSICS", "2026-2027");
        ArgumentCaptor<String> batchIds = ArgumentCaptor.forClass(String.class);
        verify(repository).publishDraft(eq("draft-1"), eq("super-admin"), eq("doc-1"), eq("SCHOOL"),
                eq("A01"), batchIds.capture());
        verify(repository).publishDraft(eq("draft-2"), eq("super-admin"), eq("doc-2"), eq("SCHOOL"),
                eq("B02"), batchIds.capture());
        assertEquals(batchIds.getAllValues().get(0), batchIds.getAllValues().get(1));
        verify(repository).createGraphSyncJob(batchIds.getAllValues().get(0), "SCHOOL", "2026-2027", List.of("A01", "B02"));
    }

    @Test
    void superAdminCanPublishDraftWithoutSubmission() {
        var draft = new SchoolDocumentRepository.DraftRow("draft-1", "doc-1", "A01", "Nhóm A01",
                "[\"MATH\"]", "SCHOOL", null, "2026-2027", "DRAFT");
        when(repository.lockDocumentsForPublication("S:SCHOOL", "2026-2027")).thenReturn(1);
        when(repository.hasActiveExtractionForGroup("S:SCHOOL", "2026-2027")).thenReturn(false);
        when(repository.lockDraftsForPublication("S:SCHOOL", "2026-2027")).thenReturn(List.of(draft));
        when(repository.subjectExists("MATH")).thenReturn(true);
        when(repository.publishedGroupCodes("SCHOOL", "2026-2027")).thenReturn(java.util.Set.of());

        var result = service.publishDraftGroup("S:SCHOOL", "2026-2027", List.of("draft-1"), "super-admin");

        assertEquals(1, result.publishedGroupCount());
        assertEquals("PENDING", result.graphSyncStatus());
        verify(repository).addSubjectGroup("SCHOOL", "A01", "Nhóm A01", "MATH", "2026-2027");
        verify(repository).publishDraft(eq("draft-1"), eq("super-admin"), eq("doc-1"), eq("SCHOOL"),
                eq("A01"), anyString());
        verify(repository).createGraphSyncJob(anyString(), eq("SCHOOL"), eq("2026-2027"), eq(List.of("A01")));
    }

    @Test
    void batchPublishCanIncludeDraftAndSubmittedRowsTogether() {
        var draft = new SchoolDocumentRepository.DraftRow("draft-1", "doc-1", "A01", "Nhóm A01",
                "[\"MATH\"]", "SCHOOL", null, "2026-2027", "DRAFT");
        var submitted = new SchoolDocumentRepository.DraftRow("draft-2", "doc-2", "B02", "Nhóm B02",
                "[\"PHYSICS\"]", "SCHOOL", null, "2026-2027", "SUBMITTED");
        when(repository.lockDocumentsForPublication("S:SCHOOL", "2026-2027")).thenReturn(2);
        when(repository.hasActiveExtractionForGroup("S:SCHOOL", "2026-2027")).thenReturn(false);
        when(repository.lockDraftsForPublication("S:SCHOOL", "2026-2027")).thenReturn(List.of(draft, submitted));
        when(repository.subjectExists("MATH")).thenReturn(true);
        when(repository.subjectExists("PHYSICS")).thenReturn(true);
        when(repository.publishedGroupCodes("SCHOOL", "2026-2027")).thenReturn(java.util.Set.of());

        var result = service.publishDraftGroup("S:SCHOOL", "2026-2027", List.of("draft-1", "draft-2"), "super-admin");

        assertEquals(2, result.publishedGroupCount());
        verify(repository, times(2)).publishDraft(anyString(), eq("super-admin"), anyString(), eq("SCHOOL"),
                anyString(), anyString());
    }

    @Test
    void batchPublishRejectsStaleReviewedDraftList() {
        var draft = new SchoolDocumentRepository.DraftRow("draft-1", "doc-1", "A01", "Nhóm A01",
                "[\"MATH\"]", "SCHOOL", null, "2026-2027", "SUBMITTED");
        when(repository.lockDocumentsForPublication("S:SCHOOL", "2026-2027")).thenReturn(1);
        when(repository.hasActiveExtractionForGroup("S:SCHOOL", "2026-2027")).thenReturn(false);
        when(repository.lockDraftsForPublication("S:SCHOOL", "2026-2027")).thenReturn(List.of(draft));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.publishDraftGroup("S:SCHOOL", "2026-2027", List.of("stale-id"), "super-admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).createGraphSyncJob(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void batchPublishRejectsWhileAnyExtractionIsActive() {
        when(repository.lockDocumentsForPublication("S:SCHOOL", "2026-2027")).thenReturn(1);
        when(repository.hasActiveExtractionForGroup("S:SCHOOL", "2026-2027")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.publishDraftGroup("S:SCHOOL", "2026-2027", List.of("draft-1"), "super-admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).lockDraftsForPublication(anyString(), anyString());
        verify(repository, never()).createGraphSyncJob(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void batchPublishRejectsDuplicateGroupCodesBeforeWriting() {
        var first = new SchoolDocumentRepository.DraftRow("draft-1", "doc-1", "A01", "Nhóm A01",
                "[\"MATH\"]", "SCHOOL", null, "2026-2027", "SUBMITTED");
        var duplicate = new SchoolDocumentRepository.DraftRow("draft-2", "doc-2", "a01", "Nhóm khác",
                "[\"PHYSICS\"]", "SCHOOL", null, "2026-2027", "SUBMITTED");
        when(repository.lockDocumentsForPublication("S:SCHOOL", "2026-2027")).thenReturn(2);
        when(repository.hasActiveExtractionForGroup("S:SCHOOL", "2026-2027")).thenReturn(false);
        when(repository.lockDraftsForPublication("S:SCHOOL", "2026-2027")).thenReturn(List.of(first, duplicate));
        when(repository.subjectExists("MATH")).thenReturn(true);
        when(repository.subjectExists("PHYSICS")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.publishDraftGroup("S:SCHOOL", "2026-2027", List.of("draft-1", "draft-2"), "super-admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).addSubjectGroup(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(repository, never()).createGraphSyncJob(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void batchPublishRejectsGroupCodeAlreadyPublished() {
        var draft = new SchoolDocumentRepository.DraftRow("draft-1", "doc-1", "a01", "Nhóm A01",
                "[\"MATH\"]", "SCHOOL", null, "2026-2027", "SUBMITTED");
        when(repository.lockDocumentsForPublication("S:SCHOOL", "2026-2027")).thenReturn(1);
        when(repository.hasActiveExtractionForGroup("S:SCHOOL", "2026-2027")).thenReturn(false);
        when(repository.lockDraftsForPublication("S:SCHOOL", "2026-2027")).thenReturn(List.of(draft));
        when(repository.subjectExists("MATH")).thenReturn(true);
        when(repository.publishedGroupCodes("SCHOOL", "2026-2027")).thenReturn(java.util.Set.of("A01"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.publishDraftGroup("S:SCHOOL", "2026-2027", List.of("draft-1"), "super-admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).addSubjectGroup(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
