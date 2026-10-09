package vn.edu.thesis.BE_subject_pathway.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectSearchRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectSearchResponse;
import vn.edu.thesis.BE_subject_pathway.service.AdmissionSearchService;

/**
 * API tra cuu xet tuyen theo mon hoc.
 */
@RestController
@RequestMapping("/api/v1/admissions")
@RequiredArgsConstructor
@Tag(name = "Admission search", description = "Tra cuu lo trinh xet tuyen theo cac mon hoc da chon")
public class AdmissionSearchController {

    private final AdmissionSearchService admissionSearchService;

    @PostMapping("/search-by-subjects")
    @Operation(
            summary = "Tra cuu to hop, truong va nganh theo mon hoc",
            description = "Tu danh sach toi da 4 ma mon tu chon, he thong bo sung cac mon bat buoc "
                    + "va tim to hop xet tuyen phu hop. Neu co universityCode, response tra them "
                    + "danh sach nganh cua truong dai hoc do.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Tra cuu thanh cong"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "Request khong hop le",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "Khong tim thay to hop xet tuyen phu hop",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500", description = "Loi he thong",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<SubjectSearchResponse>> searchBySubjects(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Danh sach ma mon va ma truong dai hoc tuy chon",
                    content = @Content(
                            schema = @Schema(implementation = SubjectSearchRequest.class),
                            examples = @ExampleObject(value = """
                                    {"subjectCodes":["PHYSICS","CHEMISTRY","BIOLOGY"],"universityCode":"DQN"}
                                    """)))
            @RequestBody SubjectSearchRequest request) {
        SubjectSearchResponse response =
                admissionSearchService.searchBySubjects(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
