package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.edu.thesis.BE_subject_pathway.dto.request.PublishDraftGroupRequest;
import vn.edu.thesis.BE_subject_pathway.service.admin.SchoolDocumentService;

class AdminDocumentControllerTest {
    private final SchoolDocumentService documents = mock(SchoolDocumentService.class);
    private final AdminDocumentController controller = new AdminDocumentController(documents);

    @Test
    void adminDeleteCreatesAnApprovalRequest() {
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

    private UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken("admin-user", "",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
