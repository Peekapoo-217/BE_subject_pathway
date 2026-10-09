package vn.edu.thesis.BE_subject_pathway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** University with an admission offer matching the subject search. */
@Getter
@AllArgsConstructor
public class UniversityDto {

    private final String universityCode;
    private final String universityName;
}
