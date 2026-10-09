package vn.edu.thesis.BE_subject_pathway.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import jakarta.validation.Valid;
import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectGroupRecommendationRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupRecommendationsResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupDto;
import vn.edu.thesis.BE_subject_pathway.service.HighSchoolService;
import vn.edu.thesis.BE_subject_pathway.service.SubjectGroupRecommendationService;

/**
 * API tra cuu truong THPT va nhom mon hoc theo truong.
 * Chi lam HTTP concern: nhan request, goi service, boc ApiResponse.
 */
@RestController
@RequestMapping("/api/v1/high-schools")
@RequiredArgsConstructor
@Tag(name = "High schools", description = "Tra cuu truong THPT, nam hoc va nhom mon duoc mo")
public class HighSchoolController {

    private final HighSchoolService highSchoolService;
    private final SubjectGroupRecommendationService recommendationService;

    /**
     * Danh sach toan bo truong THPT (sap xep theo ma tang dan).
     */
    @GetMapping
    @Operation(summary = "Lay danh sach truong THPT")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Lay danh sach thanh cong"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500", description = "Loi he thong",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<HighSchoolDto>>> getAllHighSchools() {
        return ResponseEntity.ok(ApiResponse.ok(highSchoolService.getAllHighSchools()));
    }

    /**
     * Cac nhom mon hoc (kem mon ben trong) cua mot truong theo ma.
     */
    @GetMapping("/{schoolCode}/subject-groups")
    @Operation(
            summary = "Lay nhom mon cua truong THPT",
            description = "Tra ve cac nhom mon va mon thanh phan cua truong. Co the loc theo nam hoc.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Lay danh sach thanh cong"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "Khong tim thay truong hoac du lieu nhom mon",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500", description = "Loi he thong",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<SubjectGroupDto>>> getSubjectGroups(
            @Parameter(description = "Ma truong THPT", example = "THPT01", required = true)
            @PathVariable String schoolCode,
            @Parameter(description = "Nam hoc can loc", example = "2026-2027")
            @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(ApiResponse.ok(
                academicYear == null
                        ? highSchoolService.getSubjectGroupsBySchool(schoolCode)
                        : highSchoolService.getSubjectGroupsBySchool(schoolCode, academicYear)));
    }

    @GetMapping("/{schoolCode}/academic-years")
    @Operation(summary = "Lay cac nam hoc cua truong THPT")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Lay danh sach nam hoc thanh cong"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "Khong tim thay truong hoac nam hoc",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500", description = "Loi he thong",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<String>>> getAcademicYears(
            @Parameter(description = "Ma truong THPT", example = "THPT01", required = true)
            @PathVariable String schoolCode) {
        return ResponseEntity.ok(ApiResponse.ok(
                highSchoolService.getAcademicYearsBySchool(schoolCode)));
    }

    @PostMapping("/{schoolCode}/subject-groups/recommendations")
    @Operation(
            summary = "Goi y nhom mon cua truong THPT",
            description = "Xep hang Top-K nhom mon dang duoc mo tai truong va nam hoc theo mon yeu thich "
                    + "cung muc do tu tin cua hoc sinh.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Xep hang thanh cong"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "Request hoac ma mon tham chieu khong hop le",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "Khong tim thay truong hoac nhom mon phu hop",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500", description = "Loi he thong",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<SubjectGroupRecommendationsResponse>> recommendSubjectGroups(
            @Parameter(description = "Ma truong THPT", example = "THPT01", required = true)
            @PathVariable String schoolCode,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "So thich, muc tu tin va so luong ket qua can lay",
                    content = @Content(
                            schema = @Schema(implementation = SubjectGroupRecommendationRequest.class),
                            examples = @ExampleObject(value = """
                                    {"academicYear":"2026-2027","preferredSubjectCodes":["PHYSICS","INFORMATICS"],"confidenceBySubjectCode":{"PHYSICS":4,"INFORMATICS":5},"topK":3}
                                    """)))
            @RequestBody SubjectGroupRecommendationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                recommendationService.recommend(schoolCode, request)));
    }
}
