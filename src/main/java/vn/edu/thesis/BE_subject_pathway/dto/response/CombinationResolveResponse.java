package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** Exact composition matches and the catalogue labels that support each match. */
public record CombinationResolveResponse(List<Match> matches, String resultScope) {
    public record Match(
            String combinationId,
            String combinationDisplay,
            int componentCount,
            List<Component> components,
            List<Label> labels) {
    }

    public record Component(String type, String code, String name, String subjectCode) {
    }

    public record Label(
            String code,
            String subjectDetails,
            String sourceUrl,
            String sourcePeriod,
            OffsetDateTime retrievedAt) {
    }
}
