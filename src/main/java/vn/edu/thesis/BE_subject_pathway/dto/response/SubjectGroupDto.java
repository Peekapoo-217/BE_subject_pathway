package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Nhom mon hoc cua truong THPT: ma nhom, ten nhom va cac mon ben trong.
 */
@Getter
@AllArgsConstructor
public class SubjectGroupDto {

    private final String academicYear;
    private final String groupCode;
    private final String groupName;
    private final List<SubjectDto> subjects;

    /** Backward-compatible constructor for clients/tests that predate year-aware catalogs. */
    public SubjectGroupDto(String groupCode, String groupName, List<SubjectDto> subjects) {
        this(null, groupCode, groupName, subjects);
    }
}
