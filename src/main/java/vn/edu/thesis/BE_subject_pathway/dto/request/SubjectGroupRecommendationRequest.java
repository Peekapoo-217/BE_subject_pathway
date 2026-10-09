package vn.edu.thesis.BE_subject_pathway.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dau vao xep hang nhom 4 mon theo so thich va muc tu tin cua hoc sinh. */
@Getter
@Setter
@NoArgsConstructor
public class SubjectGroupRecommendationRequest {

    @NotBlank(message = "Nam hoc khong duoc de trong")
    private String academicYear;

    @NotNull(message = "Danh sach mon yeu thich khong duoc null")
    @Size(max = 9, message = "Chi ho tro toi da 9 mon yeu thich")
    private List<@NotBlank(message = "Ma mon yeu thich khong duoc de trong") String> preferredSubjectCodes = List.of();

    @NotNull(message = "Danh gia muc tu tin khong duoc null")
    private Map<@NotBlank(message = "Ma mon khong duoc de trong") String,
            @NotNull @Min(1) @Max(5) Integer> confidenceBySubjectCode = new LinkedHashMap<>();

    @Min(1)
    @Max(5)
    private int topK = 3;
}
