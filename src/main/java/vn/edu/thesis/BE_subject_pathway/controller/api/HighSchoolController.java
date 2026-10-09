package vn.edu.thesis.BE_subject_pathway.controller.api;

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
public class HighSchoolController {

    private final HighSchoolService highSchoolService;
    private final SubjectGroupRecommendationService recommendationService;

    /**
     * Danh sach toan bo truong THPT (sap xep theo ma tang dan).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<HighSchoolDto>>> getAllHighSchools() {
        return ResponseEntity.ok(ApiResponse.ok(highSchoolService.getAllHighSchools()));
    }

    /**
     * Cac nhom mon hoc (kem mon ben trong) cua mot truong theo ma.
     */
    @GetMapping("/{schoolCode}/subject-groups")
    public ResponseEntity<ApiResponse<List<SubjectGroupDto>>> getSubjectGroups(
            @PathVariable String schoolCode,
            @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(ApiResponse.ok(
                academicYear == null
                        ? highSchoolService.getSubjectGroupsBySchool(schoolCode)
                        : highSchoolService.getSubjectGroupsBySchool(schoolCode, academicYear)));
    }

    @GetMapping("/{schoolCode}/academic-years")
    public ResponseEntity<ApiResponse<List<String>>> getAcademicYears(
            @PathVariable String schoolCode) {
        return ResponseEntity.ok(ApiResponse.ok(
                highSchoolService.getAcademicYearsBySchool(schoolCode)));
    }

    @PostMapping("/{schoolCode}/subject-groups/recommendations")
    public ResponseEntity<ApiResponse<SubjectGroupRecommendationsResponse>> recommendSubjectGroups(
            @PathVariable String schoolCode,
            @Valid @RequestBody SubjectGroupRecommendationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                recommendationService.recommend(schoolCode, request)));
    }
}
