package vn.edu.thesis.BE_subject_pathway.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Subjects", description = "Danh muc va phan loai mon hoc")
public class SubjectController {

    private final SubjectService subjectService;

    /**
     * Tra ve danh sach mon hoc goc duoc phan loai thanh mon bat buoc va mon tu chon.
     */
    @GetMapping("/classifications")
    @Operation(
            summary = "Lay phan loai mon hoc",
            description = "Tra ve cac mon hoc goc, tach thanh danh sach mon bat buoc va mon lua chon.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "Lay du lieu thanh cong"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500", description = "Loi he thong",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<SubjectClassificationResponse>> getSubjectClassifications() {
        return ResponseEntity.ok(ApiResponse.success(subjectService.getSubjectClassifications()));
    }
}
