package vn.edu.thesis.BE_subject_pathway.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.thesis.BE_subject_pathway.entity.HighSchool;

/**
 * Repository cho HighSchool. findAllByOrderByCodeAsc() la derived
 * query - Spring Data tu sinh SQL, khong can @Query.
 */
public interface HighSchoolRepository extends JpaRepository<HighSchool, String> {

    /**
     * Lay toan bo truong THPT sap xep theo ma truong tang dan.
     */
    List<HighSchool> findAllByOrderByCodeAsc();
}
