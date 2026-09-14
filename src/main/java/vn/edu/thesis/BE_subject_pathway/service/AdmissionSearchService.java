package vn.edu.thesis.BE_subject_pathway.service;

import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectSearchRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectSearchResponse;

/**
 * Hop dich vu tra cuu xet tuyen theo mon hoc THPT.
 */
public interface AdmissionSearchService {

    /**
     * Tim to hop/nganh/truong xet tuyen tu danh sach mon hoc dau vao
     * (toi da 4 mon, da duoc validate o DTO).
     */
    SubjectSearchResponse searchBySubjects(SubjectSearchRequest request);
}
