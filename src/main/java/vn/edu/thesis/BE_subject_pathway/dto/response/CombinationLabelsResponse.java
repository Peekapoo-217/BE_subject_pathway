package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** Public code lookup; one code can have multiple descriptions and canonical variants. */
public record CombinationLabelsResponse(String requestedCode, List<Label> labels) {
    public record Label(
            String code,
            String subjectDetails,
            String canonicalCombinationId,
            String sourceUrl,
            String sourcePeriod,
            OffsetDateTime retrievedAt) {
    }
}
