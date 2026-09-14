package vn.edu.thesis.BE_subject_pathway.service;

import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectClassificationResponse;

/**
 * Service quan ly va tra cuu mon hoc.
 */
public interface SubjectService {

    /**
     * Lay danh sach mon hoc goc, phan loai thanh mon bat buoc va mon tu chon.
     *
     * @return danh sach mon hoc da duoc phan nhom
     */
    SubjectClassificationResponse getSubjectClassifications();
}
