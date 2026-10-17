package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolGraphSyncResult;
import vn.edu.thesis.BE_subject_pathway.service.admin.AdminHighSchoolService;

class AdminHighSchoolControllerTest {
    private final AdminHighSchoolService schools = mock(AdminHighSchoolService.class);
    private final AdminHighSchoolController controller = new AdminHighSchoolController(schools);

    @Test
    void deletedSchoolListAndRestoreAreRestrictedToSuperAdmin() throws Exception {
        assertEquals("hasRole('SUPER_ADMIN')", AdminHighSchoolController.class
                .getMethod("listDeleted", int.class, String.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('SUPER_ADMIN')", AdminHighSchoolController.class
                .getMethod("restore", Principal.class, String.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('SUPER_ADMIN')", AdminHighSchoolController.class
                .getMethod("graphSyncStatus", String.class, String.class).getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void restoreReportsAcceptedUntilTheGraphWorkerFinishes() {
        Principal principal = () -> "super-admin";
        when(schools.restore("TDT", "super-admin"))
                .thenReturn(new HighSchoolGraphSyncResult("TDT", "PENDING", "job"));

        var response = controller.restore(principal, "TDT");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("PENDING", response.getBody().getData().graphSyncStatus());
        verify(schools).restore("TDT", "super-admin");
    }
}
