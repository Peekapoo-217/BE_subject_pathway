package vn.edu.thesis.BE_subject_pathway.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import vn.edu.thesis.BE_subject_pathway.dto.request.CombinationResolveRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationResolveResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationLabelsResponse;
import vn.edu.thesis.BE_subject_pathway.service.AdmissionCombinationLabelService;

@RestController
@RequestMapping("/api/v1/admissions/combinations")
@RequiredArgsConstructor
@Tag(name = "Admission combinations", description = "Tra cuu ma to hop va cac bien the tu danh muc")
public class AdmissionCombinationController {
    private final AdmissionCombinationLabelService service;

    @GetMapping("/{code}")
    @Operation(
            summary = "Tra cac nhan moi nhat cua mot ma to hop",
            description = "Tra ve cac mo ta cong khai trong snapshot moi nhat va ma to hop chuan neu da xac minh.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tra cuu thanh cong"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Ma to hop khong hop le"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Khong tim thay ma to hop")
    })
    public ResponseEntity<ApiResponse<CombinationLabelsResponse>> findLabels(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(service.findLatestLabels(code)));
    }

    @PostMapping("/resolve-by-subjects")
    @Operation(
            summary = "Resolve exact admission combination by subject codes",
            description = "Matches a complete subject-only component set. Does not change the subset/capability semantics of search-by-subjects.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exact matches or an empty scoped result"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid subject codes or component count")
    })
    public ResponseEntity<ApiResponse<CombinationResolveResponse>> resolveExactSubjectSet(
            @Valid @RequestBody CombinationResolveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.resolveExactSubjectSet(request.subjectCodes())));
    }
}
