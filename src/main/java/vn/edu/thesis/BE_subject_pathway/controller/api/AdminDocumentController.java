package vn.edu.thesis.BE_subject_pathway.controller.api;

import java.security.Principal;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import vn.edu.thesis.BE_subject_pathway.service.admin.SchoolDocumentService;

@RestController
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/admin/documents")
public class AdminDocumentController {
    private final SchoolDocumentService documents;

    public AdminDocumentController(SchoolDocumentService documents) {
        this.documents = documents;
    }

    public record DocumentUpdateRequest(String title, String academicYear) {
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateMetadata(@PathVariable String id, @RequestBody DocumentUpdateRequest request) {
        documents.updateMetadata(id, request.title(), request.academicYear());
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> addVersion(@PathVariable String id, Principal principal,
            @RequestParam MultipartFile file) {
        return ApiResponse.ok(documents.addVersion(id, principal.getName(), file));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(Principal principal,
            @RequestParam MultipartFile file, @RequestParam String title, @RequestParam String academicYear,
            @RequestParam(required = false) String schoolCode,
            @RequestParam(required = false) String proposedSchoolName,
            @RequestParam(required = false) String proposedSchoolLocation) {
        return ApiResponse.ok(documents.upload(principal.getName(), file, title, academicYear,
                schoolCode, proposedSchoolName, proposedSchoolLocation));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status, @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(documents.list(status, size, cursor));
    }

    @GetMapping("/{id}/download-url")
    public ApiResponse<String> download(@PathVariable String id) {
        return ApiResponse.ok(documents.downloadUrl(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> requestDelete(@PathVariable String id, Principal principal) {
        documents.requestDelete(id, principal.getName());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/approve-delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> approveDelete(@PathVariable String id, Principal principal) {
        documents.approveDelete(id, principal.getName());
        return ApiResponse.ok(null);
    }

    @GetMapping("/drafts")
    public ApiResponse<Map<String, Object>> drafts(@RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor, Authentication authentication) {
        String state = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ? "SUBMITTED" : "DRAFT";
        return ApiResponse.ok(documents.listDrafts(size, cursor, state));
    }

    @PatchMapping("/drafts/{draftId}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> approveDraft(@PathVariable String draftId, Principal principal) {
        documents.approveDraft(draftId, principal.getName());
        return ApiResponse.ok(null);
    }

    @org.springframework.web.bind.annotation.PutMapping("/drafts/{draftId}")
    public ApiResponse<Void> editDraft(@PathVariable String draftId,
            @RequestParam String groupCode, @RequestParam String groupName,
            @RequestParam String subjectCodes) {
        documents.editDraft(draftId, groupCode, groupName, subjectCodes);
        return ApiResponse.ok(null);
    }

    @PostMapping("/drafts/{draftId}/submit")
    public ApiResponse<Void> submitDraft(@PathVariable String draftId, Principal principal) {
        documents.submitDraft(draftId, principal.getName());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/drafts/{draftId}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> rejectDraft(@PathVariable String draftId, Principal principal,
            @RequestParam(required = false) String note) {
        documents.rejectDraft(draftId, principal.getName(), note);
        return ApiResponse.ok(null);
    }
}
