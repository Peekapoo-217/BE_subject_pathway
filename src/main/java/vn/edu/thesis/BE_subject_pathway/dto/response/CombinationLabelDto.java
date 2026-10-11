package vn.edu.thesis.BE_subject_pathway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Ma to hop va mon chi tiet tu danh muc da duoc lien ket an toan. */
@Getter
@AllArgsConstructor
public class CombinationLabelDto {

    private final String code;
    private final String subjectDetails;
}
