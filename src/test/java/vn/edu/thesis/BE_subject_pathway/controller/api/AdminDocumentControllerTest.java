package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.edu.thesis.BE_subject_pathway.dto.request.PublishDraftGroupRequest;
import vn.edu.thesis.BE_subject_pathway.service.admin.SchoolDocumentService;

class AdminDocumentControllerTest {
    private final SchoolDocumentService documents = mock(SchoolDocumentService.class);
    private final AdminDocumentController controller = new AdminDocumentController(documents);

    @Test
    void adminDeleteRequestsApproval() {
        var authentication = authentication("ROLE_ADMIN");

        controller.deleteDocument("document", authentication, authentication);

        verify(documents).requestDelete("document", "admin-user");
        verify(documents, never()).softDeleteDocument(anyString(), anyString());
    }

    @Test
    void superAdminDeleteSoftDeletesImmediately() {
        var authentication = authentication("ROLE_SUPER_ADMIN");

        controller.deleteDocument("document", authentication, authentication);

        verify(documents).softDeleteDocument("document", "admin-user");
        verify(documents, never()).requestDelete(anyString(), anyString());
    }

    @Test
    void documentDetailAndDownloadOnlyIncludeDeletedDocumentsForSuperAdmin() {
        var admin = authentication("ROLE_ADMIN");
        var superAdmin = authentication("ROLE_SUPER_ADMIN");

        controller.get("document", admin);
        controller.download("document", admin);
        controller.get("document", superAdmin);
        controller.download("document", superAdmin);

        verify(documents).document("document", false);
        verify(documents).downloadUrl("document", false);
        verify(documents).document("document", true);
        verify(documents).downloadUrl("document", true);
    }

    @Test
    void superAdminPublishesAWholeSchoolYearDraftBatch() {
        var authentication = authentication("ROLE_SUPER_ADMIN");
        var request = new PublishDraftGroupRequest("S:SCHOOL", "2026-2027", List.of("draft-1", "draft-2"));

        controller.publishDraftGroup(request, authentication);

        verify(documents).publishDraftGroup("S:SCHOOL", "2026-2027", List.of("draft-1", "draft-2"), "admin-user");
    }

    @Test
    void provinceOptionsAreReadFromTheSchoolDocumentService() {
        controller.provinces();

        verify(documents).listProvinces();
    }

    @Test
    void schoolCodeSuggestionPassesNameProvinceAndOptionalCustomCode() {
        controller.schoolCodeSuggestion("Trường THPT Tôn Đức Thắng", "66", "TĐT");

        verify(documents).suggestSchoolCode("Trường THPT Tôn Đức Thắng", "66", "TĐT");
    }

    @Test
    void draftSubmissionEndpointIsRestrictedToAdminRole() throws Exception {
        var method = AdminDocumentController.class.getMethod("submitDraft", String.class, Principal.class);

        assertEquals("hasRole('ADMIN')", method.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void deletedDocumentOperationsAreRestrictedToSuperAdmin() throws Exception {
        assertEquals("hasRole('SUPER_ADMIN')", AdminDocumentController.class
                .getMethod("listDeleted", int.class, String.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('SUPER_ADMIN')", AdminDocumentController.class
                .getMethod("unpublish", String.class, Principal.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('SUPER_ADMIN')", AdminDocumentController.class
                .getMethod("restore", String.class, Principal.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('SUPER_ADMIN')", AdminDocumentController.class
                .getMethod("graphSyncStatus", String.class, String.class).getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void graphLifecycleOpenApiDescribesNodeDeletionAndRecreation() throws Exception {
        Operation unpublish = AdminDocumentController.class
                .getMethod("unpublish", String.class, Principal.class).getAnnotation(Operation.class);
        Operation restore = AdminDocumentController.class
                .getMethod("restore", String.class, Principal.class).getAnnotation(Operation.class);

        assertTrue(unpublish.description().contains("deletion of each group node"));
        assertTrue(restore.description().contains("recreates missing group nodes and relationships from PostgreSQL"));
    }

    @Test
    void graphLifecycleOperationReportsAcceptedUntilNeo4jWorkerFinishes() {
        var authentication = authentication("ROLE_SUPER_ADMIN");
        when(documents.unpublishDocument("document", "admin-user")).thenReturn(java.util.Map.of(
                "documentId", "document", "graphPublicationStatus", "UNPUBLISHED",
                "graphSyncStatus", "PENDING", "graphSyncJobId", "job"));

        var response = controller.unpublish("document", authentication);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("PENDING", response.getBody().getData().get("graphSyncStatus"));
    }

    @Test
    void graphLifecycleOperationReportsSuccessOnlyAfterSync() {
        var authentication = authentication("ROLE_SUPER_ADMIN");
        when(documents.restoreDocument("document", "admin-user")).thenReturn(java.util.Map.of(
                "documentId", "document", "graphPublicationStatus", "PUBLISHED",
                "graphSyncStatus", "SYNCED", "graphSyncJobId", "job"));

        var response = controller.restore("document", authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SYNCED", response.getBody().getData().get("graphSyncStatus"));
    }

    private UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken("admin-user", "",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
