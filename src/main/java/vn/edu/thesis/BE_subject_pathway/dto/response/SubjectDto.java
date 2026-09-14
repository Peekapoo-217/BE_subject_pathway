package vn.edu.thesis.BE_subject_pathway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Thong tin rut gon cua mon hoc trong response.
 */
@Getter
@AllArgsConstructor
public class SubjectDto {

    private final String code;
    private final String name;
}
