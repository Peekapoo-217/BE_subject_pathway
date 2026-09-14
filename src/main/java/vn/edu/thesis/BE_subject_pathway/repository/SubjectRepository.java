package vn.edu.thesis.BE_subject_pathway.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.edu.thesis.BE_subject_pathway.entity.Subject;

/**
 * Repository cho Subject (bang subjects).
 * Cung cap lookup don gian theo derived query (khong load entity),
 * theo rule jpa-database-performance.
 */
public interface SubjectRepository extends JpaRepository<Subject, String> {

    /**
     * Lay ma mon hoc cua cac mon bat buoc (is_mandatory = true).
     * Danh sach nay duoc gop voi mon hoc client chon khi tra cuu
     * to hop xet tuyen (hoc sinh luon co mon bat buoc).
     * Dung native query de chi select cot code (khong load entity);
     * property entity la 'code' (so it) nen derived query theo ten
     * method 'findCodesBy...' khong map duoc projection.
     */
    @Query(value = "SELECT s.code FROM subjects s WHERE s.is_mandatory = true", nativeQuery = true)
    List<String> findCodesByIsMandatoryTrue();

    /**
     * Lay danh sach cac mon hoc goc de hien thi UI (loai bo mon phan nhanh
     * co canonicalSubjectCode khac null).
     */
    @Query("SELECT s FROM Subject s WHERE s.canonicalSubjectCode IS NULL ORDER BY s.name ASC")
    List<Subject> findRootSubjects();
}
