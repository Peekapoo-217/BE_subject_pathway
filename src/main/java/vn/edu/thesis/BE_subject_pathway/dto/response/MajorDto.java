package vn.edu.thesis.BE_subject_pathway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Training major included in a subject-based admission search result. */
@Getter
@AllArgsConstructor
public class MajorDto {

    private final String programCode;
    private final String programName;
}
