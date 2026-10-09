package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Top-K nhom mon thuc te duoc mo tai mot truong va nam hoc. */
@Getter
@AllArgsConstructor
public class SubjectGroupRecommendationsResponse {

    private final String schoolCode;
    private final String academicYear;
    private final int requestedTopK;
    private final int eligibleGroupCount;
    private final List<SubjectGroupRecommendationDto> recommendations;
}
