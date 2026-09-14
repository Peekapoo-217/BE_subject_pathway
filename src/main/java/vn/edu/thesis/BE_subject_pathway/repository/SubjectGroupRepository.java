package vn.edu.thesis.BE_subject_pathway.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.thesis.BE_subject_pathway.entity.Subject;
import vn.edu.thesis.BE_subject_pathway.repository.projection.SubjectGroupFlatProjection;

/**
 * Repository truy van nhom mon hoc theo truong THPT.
 * Native query tra du lieu flat (1 dong = 1 mon trong 1 nhom),
 * Service se gom nhop bang Java Stream (rule layered-architecture:
 * ranking/transform nam o Service).
 */
public interface SubjectGroupRepository extends JpaRepository<Subject, String> {

    /**
     * Lay danh sach nhom mon cua mot truong: JOIN subjects de lay
     * ten mon. DISTINCT de tranh ban ghi trung khi truong co cung
     * nhom o nhieu academic_year (PK subject_groups gom 4 cot).
     * Thu tu alias phai khop getter cua SubjectGroupFlatProjection.
     */
    @Query(value = """
            SELECT DISTINCT sg.group_code AS groupCode,
                   sg.group_name AS groupName,
                   s.code        AS subjectCode,
                   s.name        AS subjectName
            FROM subject_groups sg
            JOIN subjects s ON sg.subject_code = s.code
            WHERE sg.school_code = :schoolCode
            ORDER BY sg.group_code, s.code
            """, nativeQuery = true)
    List<SubjectGroupFlatProjection> findSubjectGroupsBySchool(
            @Param("schoolCode") String schoolCode);
}
