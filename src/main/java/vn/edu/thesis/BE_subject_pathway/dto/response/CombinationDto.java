package vn.edu.thesis.BE_subject_pathway.dto.response;

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
}
