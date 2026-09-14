package vn.edu.thesis.BE_subject_pathway.repository.projection;

/**
 * Interface Projection hung du lieu flat tu native query
 * (1 dong = 1 mon trong 1 nhom mon cua truong). Alias SQL phai
 * khop dung ten getter de Spring Data map dung.
 */
public interface SubjectGroupFlatProjection {

    String getGroupCode();

    String getGroupName();

    String getSubjectCode();

    String getSubjectName();
}
