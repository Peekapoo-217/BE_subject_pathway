package vn.edu.thesis.BE_subject_pathway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PublishDraftGroupRequest(
        @NotBlank String groupKey,
        @NotBlank String academicYear,
        @NotEmpty List<@NotBlank String> reviewedDraftIds) {}
