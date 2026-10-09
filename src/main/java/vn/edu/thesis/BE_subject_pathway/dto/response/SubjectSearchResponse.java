package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response cho tra cuu theo mon hoc: so to hop hop le, chi tiet cac
 * to hop, so nganh va so truong xet tuyen qua cac to hop do.
 */
@Getter
@AllArgsConstructor
public class SubjectSearchResponse {

    private final int totalCombinations;
    private final List<CombinationDto> possibleCombinations;
    private final Long totalMajors;
    private final Long totalUniversities;
    private final List<UniversityDto> universities;
    private final List<MajorDto> majors;

    /** Backward-compatible constructor retained for existing callers. */
    public SubjectSearchResponse(
            int totalCombinations,
            List<CombinationDto> possibleCombinations,
            Long totalMajors,
            Long totalUniversities) {
        this(totalCombinations, possibleCombinations, totalMajors, totalUniversities, List.of(), List.of());
    }

    public SubjectSearchResponse(
            int totalCombinations,
            List<CombinationDto> possibleCombinations,
            Long totalMajors,
            Long totalUniversities,
            List<MajorDto> majors) {
        this(totalCombinations, possibleCombinations, totalMajors, totalUniversities, List.of(), majors);
    }
}
