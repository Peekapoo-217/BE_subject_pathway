package vn.edu.thesis.BE_subject_pathway.service;

import java.util.List;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupDto;

/**
 * Hop dich vu tra cuu truong THPT va nhom mon hoc theo truong.
 */
public interface HighSchoolService {

    /**
     * Lay danh sach tat ca truong THPT sap xep theo ma tang dan.
     */
    List<HighSchoolDto> getAllHighSchools();

    /**
     * Lay cac nhom mon hoc (kem danh sach mon) cua mot truong.
     * @throws vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException
     *         khi ma truong khong ton tai (HTTP 404)
     */
    List<SubjectGroupDto> getSubjectGroupsBySchool(String schoolCode);
}
