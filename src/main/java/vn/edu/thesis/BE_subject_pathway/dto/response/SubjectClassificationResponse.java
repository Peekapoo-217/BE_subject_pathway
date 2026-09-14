package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Phan loai mon hoc goc thanh 2 nhom: mon bat buoc va mon tu chon.
 */
@Getter
@AllArgsConstructor
public class SubjectClassificationResponse {

    private final List<SubjectDto> mandatorySubjects;
    private final List<SubjectDto> electiveSubjects;
}
