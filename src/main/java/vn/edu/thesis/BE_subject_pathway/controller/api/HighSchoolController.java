package vn.edu.thesis.BE_subject_pathway.controller.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupDto;
import vn.edu.thesis.BE_subject_pathway.service.HighSchoolService;

/**
 * API tra cuu truong THPT va nhom mon hoc theo truong.
 * Chi lam HTTP concern: nhan request, goi service, boc ApiResponse.
 */
@RestController
@RequestMapping("/api/v1/high-schools")
@RequiredArgsConstructor
public class HighSchoolController {

    private final HighSchoolService highSchoolService;

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
            @PathVariable String schoolCode) {
        return ResponseEntity.ok(ApiResponse.ok(
                highSchoolService.getSubjectGroupsBySchool(schoolCode)));
    }
}
