package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Read-only regression against PostgreSQL's actual prepared-parameter type resolution. */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.hikari.read-only=true",
        "app.bootstrap.super-admin-username=",
        "app.bootstrap.super-admin-password="
})
class AdminPaginationPostgresTest {
    @Autowired private AdminManagementService management;
    @Autowired private SchoolDocumentService documents;
    @Autowired private CursorCodec cursors;

    @Test
    void usersLoadWithoutRoleAndAcceptRoleAndCursor() {
        Map<String, Object> first = management.listUsers(20, null, null);
        assertNotNull(first.get("items"));
        assertNotNull(management.listUsers(20, null, "SUPER_ADMIN").get("items"));

        List<?> items = (List<?>) first.get("items");
        if (!items.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> last = (Map<String, Object>) items.get(items.size() - 1);
            String after = cursors.encode(last.get("createdAt") + "|" + last.get("id") + "|");
            assertNotNull(management.listUsers(20, after, null).get("items"));
        }
    }

    @Test
    void combinationsLoadWithoutActiveOrCursorAndContinueToNextPage() {
        Map<String, Object> first = management.listCombinations(20, null, null);
        assertFalse(((List<?>) first.get("items")).isEmpty());
        assertNotNull(management.listCombinations(20, null, true).get("items"));
        assertNotNull(management.listCombinations(20, null, false).get("items"));
        String after = (String) first.get("nextCursor");
        if (!after.isEmpty()) {
            assertNotNull(management.listCombinations(20, after, null).get("items"));
        }
    }

    @Test
    void documentsLoadWithoutStatusAndAcceptStatusFilter() {
        assertNotNull(documents.list(null, 20, null).get("items"));
        assertNotNull(documents.list("PUBLISHED", 20, null).get("items"));
        String after = cursors.encode(OffsetDateTime.now() + "|" + UUID.randomUUID() + "|");
        assertNotNull(documents.list(null, 20, after).get("items"));
    }

    @Test
    void draftsAcceptTimestampCursor() {
        String after = cursors.encode(OffsetDateTime.now() + "|" + UUID.randomUUID() + "|SUBMITTED");
        assertNotNull(documents.listDrafts(20, after, "SUBMITTED").get("items"));
    }
}
