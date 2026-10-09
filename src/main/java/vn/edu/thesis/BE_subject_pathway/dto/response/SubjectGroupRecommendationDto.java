package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Mot nhom mon duoc xep hang kem cac dau hieu giai thich. */
@Getter
@AllArgsConstructor
public class SubjectGroupRecommendationDto {

    private final String groupCode;
    private final String groupName;
    private final String academicYear;
    private final List<SubjectDto> subjects;
    private final double score;
    private final List<SubjectDto> matchedPreferredSubjects;
    private final List<SubjectDto> lowConfidenceSubjects;
    private final List<String> explanations;
}
