package vn.edu.thesis.BE_subject_pathway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request cho API tra cuu theo mon hoc.
 * Validation: toi da 4 mon, khong mon nao de trong (rule dto-and-entity-separation).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubjectSearchRequest {

    @NotEmpty(message = "Danh sach mon hoc khong duoc de trong")
    @Size(max = 4, message = "Chi duoc chon toi da 4 mon hoc")
    private List<@NotBlank(message = "Ma mon hoc khong duoc de trong") String> subjectCodes;

    @Size(max = 20, message = "Ma truong dai hoc qua dai")
    @Pattern(regexp = "\\S+", message = "Ma truong dai hoc khong hop le")
    private String universityCode;

    @Size(max = 30)
    @Pattern(regexp = "\\S+")
    private String schoolCode;

    @Size(max = 20)
    @Pattern(regexp = "\\d{4}(?:-\\d{4})?", message = "Nam hoc phai co dang YYYY hoac YYYY-YYYY")
    private String academicYear;

    @Size(max = 30)
    @Pattern(regexp = "\\S+")
    private String groupCode;

    @JsonIgnore
    @AssertTrue(message = "Can gui day du schoolCode, academicYear va groupCode")
    public boolean isSchoolGroupContextComplete() {
        return (schoolCode == null && academicYear == null && groupCode == null)
                || (schoolCode != null && academicYear != null && groupCode != null);
    }

    public SubjectSearchRequest(List<String> subjectCodes, String universityCode) {
        this(subjectCodes, universityCode, null, null, null);
    }

    public SubjectSearchRequest(List<String> subjectCodes) {
        this(subjectCodes, null);
    }
}
