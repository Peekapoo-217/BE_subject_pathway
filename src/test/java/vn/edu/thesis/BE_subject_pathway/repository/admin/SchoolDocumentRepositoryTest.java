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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
}
