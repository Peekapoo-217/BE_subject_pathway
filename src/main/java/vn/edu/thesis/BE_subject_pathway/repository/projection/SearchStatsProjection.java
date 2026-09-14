package vn.edu.thesis.BE_subject_pathway.repository.projection;

/**
 * Interface Projection cho ket qua thong ke dem nganh / truong
 * tu mot danh sach to hop hop le.
 */
public interface SearchStatsProjection {

    Long getTotalMajors();

    Long getTotalUniversities();
}
