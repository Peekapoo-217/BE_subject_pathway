package vn.edu.thesis.BE_subject_pathway.repository.projection;

/** A real university offer connected to a canonical admission combination. */
public interface AdmissionPathwayProjection {
    String getCombinationId();
    String getUniversityCode();
    String getUniversityName();
    String getProgramCode();
    String getProgramName();
    Short getAdmissionYear();
    String getMethodCodes();
    String getSourceUrl();
}
