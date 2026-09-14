package vn.edu.thesis.BE_subject_pathway.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.thesis.BE_subject_pathway.entity.AdmissionOffer;
import vn.edu.thesis.BE_subject_pathway.repository.projection.SearchStatsProjection;

/**
 * Repository cho AdmissionOffer: thong ke so nganh / so truong
 * tu danh sach to hop hop le, di qua bang trung gian offer_combinations.
 * Tat ca tinh toan thong ke dien ra trong DB (rule jpa-database-performance).
 */
public interface AdmissionOfferRepository
        extends JpaRepository<AdmissionOffer, Long> {

    /**
     * Mot query duy nhat tra ca 2 dem: so nganh (program_code phan biet)
     * va so truong (university_code phan biet), qua offer_combinations.
     * Chi xet cac offer co chua ma phuong thuc :methodCode (token chinh xac
     * trong method_codes, VD "100" trong "100,201" nhung khong khop "1100").
     */
    @Query(value = """
            SELECT COUNT(DISTINCT o.program_code)    AS totalMajors,
                   COUNT(DISTINCT o.university_code) AS totalUniversities
            FROM admission_offers o
            JOIN offer_combinations oc ON oc.offer_id = o.id
            WHERE oc.canonical_combination_id IN (:combinationIds)
              AND (:methodCode = ANY(
                     string_to_array(replace(o.method_codes, ' ', ''), ',')))
            """, nativeQuery = true)
    SearchStatsProjection countMajorsAndUniversities(
            @Param("combinationIds") List<String> combinationIds,
            @Param("methodCode") String methodCode);
}
