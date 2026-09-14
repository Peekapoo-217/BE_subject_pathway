package vn.edu.thesis.BE_subject_pathway.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectClassificationResponse;
import vn.edu.thesis.BE_subject_pathway.service.SubjectService;

/**
 * API tra cuu mon hoc va phan loai mon hoc.
 */
@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    /**
     * Tra ve danh sach mon hoc goc duoc phan loai thanh mon bat buoc va mon tu chon.
     */
    @GetMapping("/classifications")
    public ResponseEntity<ApiResponse<SubjectClassificationResponse>> getSubjectClassifications() {
        return ResponseEntity.ok(ApiResponse.success(subjectService.getSubjectClassifications()));
    }
}
