package vn.edu.thesis.BE_subject_pathway.repository.projection;

/**
 * Interface Projection cho ket qua tim to hop hop le.
 * Alias trong native query phai khop ten getter (camelCase).
 */
public interface CombinationProjection {

    String getCombinationId();

    String getCombinationDisplay();

    Short getComponentCount();
}
