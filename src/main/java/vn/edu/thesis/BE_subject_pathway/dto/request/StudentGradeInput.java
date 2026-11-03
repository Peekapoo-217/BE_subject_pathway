package vn.edu.thesis.BE_subject_pathway.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A grade as reported by the student; values from different scales are never converted. */
@Getter
@Setter
@NoArgsConstructor
public class StudentGradeInput {

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal value;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("100.0")
    private BigDecimal scale;

    @Size(max = 100)
    private String assessmentType;

    @JsonIgnore
    @AssertTrue(message = "Diem hoc tap khong duoc lon hon thang diem")
    public boolean isWithinScale() {
        return value == null || scale == null || value.compareTo(scale) <= 0;
    }
}
