package vn.edu.thesis.BE_subject_pathway.repository.projection;

/** Minimal query projection for a training major returned by subject search. */
public interface MajorProjection {

    String getProgramCode();

    String getProgramName();
}
