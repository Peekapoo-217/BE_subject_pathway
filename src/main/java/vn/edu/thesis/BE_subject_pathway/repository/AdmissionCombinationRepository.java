package vn.edu.thesis.BE_subject_pathway.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.thesis.BE_subject_pathway.entity.AdmissionCombination;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationProjection;

/**
 * Repository cho AdmissionCombination.
 * Tim kiem dua tren native query va Interface Projection (khong load entity)
 * theo rule jpa-database-performance.
 */
public interface AdmissionCombinationRepository
        extends JpaRepository<AdmissionCombination, String> {

    /**
     * Tim cac to hop hop le tu danh sach mon hoc dau vao: to hop duoc chap
     * nhan khi MOI mon thanh phan kieu SUBJECT deu co trong danh sach input.
     * Dem tren subject_code phan biet, so sanh voi component_count cua to hop
     * de loai to hop thieu mon (HAVING COUNT(DISTINCT ...) = component_count).
     */
    @Query(value = """
            SELECT c.combination_id AS combinationId,
                   c.combination_display AS combinationDisplay,
                   c.component_count AS componentCount
            FROM admission_combinations c
            JOIN admission_combination_components comp
              ON comp.combination_id = c.combination_id
            WHERE comp.component_type = 'SUBJECT'
              AND comp.subject_code IN (:subjectCodes)
            GROUP BY c.combination_id, c.combination_display, c.component_count
            HAVING COUNT(DISTINCT comp.subject_code) = c.component_count
            ORDER BY c.combination_id
            """, nativeQuery = true)
    List<CombinationProjection> findValidCombinations(
            @Param("subjectCodes") List<String> subjectCodes);
}
