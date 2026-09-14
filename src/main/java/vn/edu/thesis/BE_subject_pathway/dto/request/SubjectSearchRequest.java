package vn.edu.thesis.BE_subject_pathway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
}
