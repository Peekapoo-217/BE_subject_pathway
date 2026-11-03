package vn.edu.thesis.BE_subject_pathway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Exact subject composition request for chatbot entity resolution. */
public record CombinationResolveRequest(
        @NotEmpty
        @Size(min = 2, max = 4)
        List<@NotBlank @Size(max = 30) String> subjectCodes) {
}
