package vn.edu.thesis.BE_subject_pathway.repository.projection;

import java.time.Instant;

/** Public label snapshot and its optional verified canonical mapping. */
public interface CombinationCatalogueLabelProjection {
    String getCode();
    String getSubjectDetails();
    String getCanonicalCombinationId();
    String getSourceUrl();
    String getSourcePeriod();
    Instant getRetrievedAt();
}
