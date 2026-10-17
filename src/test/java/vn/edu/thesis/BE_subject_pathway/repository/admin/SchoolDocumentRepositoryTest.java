package vn.edu.thesis.BE_subject_pathway.repository.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SchoolDocumentRepositoryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsPostgresJsonbAndIntegerArrayToJsonArrays() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Array sourcePages = mock(Array.class);
        when(rs.getString("id")).thenReturn("draft-id");
        when(rs.getString("groupCode")).thenReturn("A01");
        when(rs.getString("groupName")).thenReturn("Toán, Vật lý, Tiếng Anh");
        when(rs.getString("subjectCodes")).thenReturn("[\"MATH\",\"PHYSICS\",\"ENGLISH\"]");
        when(rs.getArray("sourcePages")).thenReturn(sourcePages);
        when(sourcePages.getArray()).thenReturn(new Integer[] {2, 5});
        when(rs.getString("status")).thenReturn("DRAFT");
        when(rs.getObject("confidence")).thenReturn(new BigDecimal("0.95"));
        when(rs.getObject("createdAt")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 14, 9, 30)));
        when(rs.getString("title")).thenReturn("Danh sách nhóm môn");
        when(rs.getString("academicYear")).thenReturn("2026-2027");
        when(rs.getString("schoolName")).thenReturn("Trường THPT mẫu");

        Map<String, Object> row = SchoolDocumentRepository.mapDraftRow(rs);
        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsBytes(row));

        assertEquals(List.of("MATH", "PHYSICS", "ENGLISH"), row.get("subjectCodes"));
        assertEquals(List.of(2, 5), row.get("sourcePages"));
        assertTrue(serialized.get("subjectCodes").isArray());
        assertTrue(serialized.get("sourcePages").isArray());
        assertTrue(serialized.get("subjectCodes").get(0).isTextual());
        assertTrue(serialized.get("sourcePages").get(0).isIntegralNumber());
        assertFalse(serialized.has("array"));
        verify(sourcePages).free();
    }

    @Test
    void rejectsMalformedSubjectCodesBeforeReturningDraftRow() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("subjectCodes")).thenReturn("{\"subject\":\"MATH\"}");

        assertThrows(SQLException.class, () -> SchoolDocumentRepository.mapDraftRow(rs));
        verify(rs, never()).getArray("sourcePages");
    }

    @Test
    void restoreLegacyDraftQueryEscapesJsonbOperatorsAndBindsOnlyDocumentId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> statements = new ArrayList<>();
        List<Object[]> parameters = new ArrayList<>();
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            statements.add(invocation.getArgument(0));
            Object rawParameters = invocation.getArgument(1);
            parameters.add(rawParameters instanceof Object[] values ? values : new Object[] {rawParameters});
            return 1;
        });

        String documentId = "d79be0cb-a597-4d2b-af07-b2c552e32ee8";
        assertEquals(1, new SchoolDocumentRepository(jdbc).restoreDocument(documentId));

        String snapshotSql = statements.get(0);
        assertTrue(snapshotSql.contains("audit.details ?? 'documentVersionId'"));
        assertTrue(snapshotSql.contains("audit.details ?? 'groupCode'"));
        assertTrue(snapshotSql.contains("audit.details ?? 'groupName'"));
        assertEquals(1, parameters.get(0).length);
        assertEquals(documentId, parameters.get(0)[0]);
    }
}
