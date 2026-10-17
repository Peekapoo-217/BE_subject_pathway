package vn.edu.thesis.BE_subject_pathway.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only regression for native SQL against PostgreSQL with migration V11 applied. */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.hikari.read-only=true",
        "app.bootstrap.super-admin-username=",
        "app.bootstrap.super-admin-password="
})
class SubjectGroupRepositoryPostgresTest {
    @Autowired private SubjectGroupRepository groups;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void academicYearsQueryHandlesASchoolWithoutGroups() {
        // PostgreSQL must resolve the joined column names even when no rows match.
        assertTrue(groups.findAcademicYearsBySchool("").isEmpty());
    }

    @Test
    void postgresJdbcPassesEscapedJsonbKeyOperatorAsSingleBindParameter() {
        boolean hasKey = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT ?::jsonb ?? 'documentVersionId'",
                Boolean.class,
                "{\"documentVersionId\":\"version-id\"}"));

        assertTrue(hasKey);
    }

    @Test
    void academicYearsAreDistinctNewestFirstAndMatchVisibleGroups() {
        var schoolCodes = jdbc.queryForList(
                "SELECT code FROM public.high_schools WHERE deleted_at IS NULL ORDER BY code",
                String.class);
        assertFalse(schoolCodes.isEmpty(), "The regression database must contain an active school");

        for (String schoolCode : schoolCodes) {
            var expectedYears = groups.findSubjectGroupsBySchool(schoolCode).stream()
                    .map(row -> row.getAcademicYear())
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList();

            assertEquals(expectedYears, groups.findAcademicYearsBySchool(schoolCode), schoolCode);
        }
    }
}
