package vn.edu.thesis.BE_subject_pathway.controller.api;

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
public class AdmissionSearchController {

    private final AdmissionSearchService admissionSearchService;

    @PostMapping("/search-by-subjects")
    public ResponseEntity<ApiResponse<SubjectSearchResponse>> searchBySubjects(
            @Valid @RequestBody SubjectSearchRequest request) {
        SubjectSearchResponse response =
                admissionSearchService.searchBySubjects(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
