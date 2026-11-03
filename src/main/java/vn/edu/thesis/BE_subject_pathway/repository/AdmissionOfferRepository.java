package vn.edu.thesis.BE_subject_pathway.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.thesis.BE_subject_pathway.entity.AdmissionOffer;
import vn.edu.thesis.BE_subject_pathway.repository.projection.MajorProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.AdmissionPathwayProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.SearchStatsProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.UniversityProjection;

/**
 * Repository cho AdmissionOffer: thong ke so nganh / so truong
 * tu danh sach to hop hop le, di qua bang trung gian offer_combinations.
 * Tat ca tinh toan thong ke dien ra trong DB (rule jpa-database-performance).
 */
public interface AdmissionOfferRepository
        extends JpaRepository<AdmissionOffer, Long> {

    /** Universities offering at least one matched combination via the selected method. */
    @Query(value = """
            SELECT DISTINCT u.university_code AS universityCode,
                   u.university_name AS universityName
            FROM admission_offers o
            JOIN universities u ON u.university_code = o.university_code
            JOIN offer_combinations oc ON oc.offer_id = o.id
            WHERE oc.canonical_combination_id IN (:combinationIds)
              AND (:methodCode = ANY(
                     string_to_array(replace(o.method_codes, ' ', ''), ',')))
            ORDER BY u.university_name, u.university_code
            """, nativeQuery = true)
    List<UniversityProjection> findUniversitiesByCombinations(
            @Param("combinationIds") List<String> combinationIds,
            @Param("methodCode") String methodCode);

    /** Return the distinct majors of one university through matched combinations. */
    @Query(value = """
            SELECT DISTINCT m.program_code AS programCode,
                   m.program_name AS programName
            FROM admission_offers o
            JOIN majors m ON m.program_code = o.program_code
            JOIN offer_combinations oc ON oc.offer_id = o.id
            WHERE oc.canonical_combination_id IN (:combinationIds)
              AND o.university_code = :universityCode
              AND (:methodCode = ANY(
                     string_to_array(replace(o.method_codes, ' ', ''), ',')))
            ORDER BY m.program_name, m.program_code
            """, nativeQuery = true)
    List<MajorProjection> findMajorsByCombinationsAndUniversity(
            @Param("combinationIds") List<String> combinationIds,
            @Param("methodCode") String methodCode,
            @Param("universityCode") String universityCode);

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

    /**
     * Latest offer rows connected to the requested canonical combinations and a
     * user-supplied program-name fragment. The offer itself, rather than a graph
     * proximity or cutoff score, establishes the pathway.
     */
    @Query(value = """
            SELECT DISTINCT ON (o.university_code, o.program_code, oc.canonical_combination_id)
                   oc.canonical_combination_id AS combinationId,
                   o.university_code AS universityCode,
                   u.university_name AS universityName,
                   o.program_code AS programCode,
                   m.program_name AS programName,
                   o.admission_year AS admissionYear,
                   o.method_codes AS methodCodes,
                   o.source_url AS sourceUrl
            FROM admission_offers o
            JOIN universities u ON u.university_code = o.university_code
            JOIN majors m ON m.program_code = o.program_code
            JOIN offer_combinations oc ON oc.offer_id = o.id
            WHERE oc.canonical_combination_id IN (:combinationIds)
              AND strpos(lower(m.program_name), lower(:programFragment)) > 0
              AND (:universityCode IS NULL OR o.university_code = :universityCode)
              AND (:universityName IS NULL OR strpos(lower(u.university_name), lower(:universityName)) > 0)
              AND (:admissionYear IS NULL OR o.admission_year = :admissionYear)
              AND (:methodCode IS NULL OR :methodCode = ANY(
                     string_to_array(replace(o.method_codes, ' ', ''), ',')))
            ORDER BY o.university_code, o.program_code,
                     oc.canonical_combination_id, o.admission_year DESC
            """, nativeQuery = true)
    List<AdmissionPathwayProjection> findLatestPathwaysByCombinationAndProgram(
            @Param("combinationIds") List<String> combinationIds,
            @Param("programFragment") String programFragment,
            @Param("universityCode") String universityCode,
            @Param("universityName") String universityName,
            @Param("admissionYear") Integer admissionYear,
            @Param("methodCode") String methodCode);
}
