package vn.edu.thesis.BE_subject_pathway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Thong tin rut gon cua truong THPT trong response.
 */
@Getter
@AllArgsConstructor
public class HighSchoolDto {

    private final String code;
    private final String name;
}
