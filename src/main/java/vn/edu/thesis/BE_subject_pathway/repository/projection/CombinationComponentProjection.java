package vn.edu.thesis.BE_subject_pathway.repository.projection;

/** Subject component used to verify that an admission combination is fully covered. */
public interface CombinationComponentProjection {
    String getCombinationId();
    String getComponentType();
    String getComponentCode();
    String getComponentName();
    String getSubjectCode();
}
