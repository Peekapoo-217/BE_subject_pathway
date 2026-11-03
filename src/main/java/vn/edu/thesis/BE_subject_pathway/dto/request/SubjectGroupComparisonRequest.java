package vn.edu.thesis.BE_subject_pathway.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Profile and optional study goal used for an evidence-based group comparison. */
@Getter
@Setter
@NoArgsConstructor
public class SubjectGroupComparisonRequest {

    @Pattern(regexp = "\\d{4}(?:-\\d{4})?", message = "Nam hoc phai co dang YYYY hoac YYYY-YYYY")
    private String academicYear;

    @Pattern(regexp = "\\d{4}", message = "Nam tuyen sinh phai co dang YYYY")
    private String admissionYear;

    @Size(max = 20)
    @Pattern(regexp = "[A-Za-z0-9_-]*", message = "Ma phuong thuc khong hop le")
    private String methodCode;

    @Size(max = 9)
    @NotNull
    private List<@Size(max = 30) String> preferredSubjectCodes = List.of();

    @Size(max = 9)
    @NotNull
    private List<@Size(max = 30) String> dislikedSubjectCodes = List.of();

    @Size(max = 9)
    @NotNull
    private Map<@Size(max = 30) String, @Valid StudentGradeInput> gradesBySubjectCode = new LinkedHashMap<>();

    @Size(max = 9)
    @NotNull
    private Map<@Size(max = 30) String, @Size(max = 120) String> selfReportedStrengthBySubjectCode = new LinkedHashMap<>();

    @Size(max = 9)
    @NotNull
    private Map<@Size(max = 30) String, @Min(1) @Max(5) Integer> confidenceBySubjectCode = new LinkedHashMap<>();

    @Size(max = 120)
    private String desiredProgramName;

    @Size(max = 20)
    private String universityCode;

    @Size(max = 100)
    private String universityName;
}
