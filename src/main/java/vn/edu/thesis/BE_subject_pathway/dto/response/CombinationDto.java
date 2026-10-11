package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Thong tin rut gon cua mot to hop xet tuyen trong response.
 */
@Getter
@AllArgsConstructor
public class CombinationDto {

    private final String combinationId;
    private final String combinationDisplay;
    private final Short componentCount;
    private final List<CombinationLabelDto> labels;

    public CombinationDto(String combinationId, String combinationDisplay, Short componentCount) {
        this(combinationId, combinationDisplay, componentCount, List.of());
    }
}
