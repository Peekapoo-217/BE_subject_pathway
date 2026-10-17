package vn.edu.thesis.BE_subject_pathway.controller.api;

import java.security.Principal;
import java.util.Map;
import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminCursorPage;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminDraftGroupDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminSubjectGroupDraftDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminPublicationResult;
import vn.edu.thesis.BE_subject_pathway.dto.response.ProvinceDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SchoolCodeSuggestionDto;
import vn.edu.thesis.BE_subject_pathway.dto.request.PublishDraftGroupRequest;
import vn.edu.thesis.BE_subject_pathway.service.admin.SchoolDocumentService;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/admin/documents")
public class AdminDocumentController {
    private final SchoolDocumentService documents;

    public AdminDocumentController(SchoolDocumentService documents) {
        this.documents = documents;
    }

    public record DocumentUpdateRequest(String title, String academicYear, String schoolCode, String proposedSchoolName,
            String provinceCode, String proposedSchoolCode) {
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update THPT document metadata", description = "Academic year is stored as the four-digit starting year (YYYY). A proposed school can be linked to an existing school or kept with a selected province and a checked school code.")
    public ApiResponse<Void> updateMetadata(@PathVariable String id, @RequestBody DocumentUpdateRequest request) {
        documents.updateMetadata(id, request.title(), request.academicYear(), request.schoolCode(), request.proposedSchoolName(),
                request.provinceCode(), request.proposedSchoolCode());
        return ApiResponse.ok(null);
    }

    @GetMapping("/provinces")
    @Operation(summary = "List provinces for THPT document registration", description = "Returns the cached province catalog from the configured province API.")
    public ApiResponse<java.util.List<ProvinceDto>> provinces() {
        return ApiResponse.ok(documents.listProvinces());
    }

    @GetMapping("/school-code-suggestion")
    @Operation(summary = "Suggest or check a THPT school code", description = "Suggests initials from the words after THPT, checks code availability, and returns an existing matching school for reuse when the normalized name and province already exist.")
    public ApiResponse<SchoolCodeSuggestionDto> schoolCodeSuggestion(
            @RequestParam String schoolName, @RequestParam String provinceCode,
            @RequestParam(required = false) String code) {
        return ApiResponse.ok(documents.suggestSchoolCode(schoolName, provinceCode, code));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a THPT school document", description = "Every upload creates a separate document. Accepts one PDF (up to 50 MiB) or one PNG, JPEG, or WebP image (up to 10 MiB). Creates an asynchronous extraction job; extracted groups require review before publication.")
    public ApiResponse<Map<String, Object>> upload(Principal principal,
            @RequestParam MultipartFile file, @RequestParam String title, @RequestParam String academicYear,
            @RequestParam(required = false) String schoolCode,
            @RequestParam(required = false) String proposedSchoolName,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String proposedSchoolCode) {
        return ApiResponse.ok(documents.upload(principal.getName(), file, title, academicYear,
                schoolCode, proposedSchoolName, provinceCode, proposedSchoolCode));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status, @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(documents.list(status, size, cursor));
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List soft-deleted documents", description = "Only SUPER_ADMIN can inspect documents retained for restoration or graph unpublication.")
    public ApiResponse<Map<String, Object>> listDeleted(@RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(documents.listDeleted(size, cursor));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a managed document", description = "Deleted documents are returned only to SUPER_ADMIN; other roles receive 404.")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id, Authentication authentication) {
        return ApiResponse.ok(documents.document(id, isSuperAdmin(authentication)));
    }

    @GetMapping("/{id}/download-url")
    public ApiResponse<String> download(@PathVariable String id, Authentication authentication) {
        return ApiResponse.ok(documents.downloadUrl(id, isSuperAdmin(authentication)));
    }

    @PostMapping("/{id}/retry-extraction")
    @Operation(summary = "Retry failed document extraction", description = "Requeues the failed job for the latest document version without uploading the file again.")
    public ApiResponse<Void> retryExtraction(@PathVariable String id) {
        documents.retryExtraction(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Request or perform document soft-delete", description = "ADMIN creates a DELETE_REQUESTED approval request. SUPER_ADMIN soft-deletes immediately. Approval/soft-delete retains recoverable drafts and source objects while published groups remain available in Neo4j.")
    public ApiResponse<Void> deleteDocument(@PathVariable String id, Principal principal, Authentication authentication) {
        if (isSuperAdmin(authentication)) {
            documents.softDeleteDocument(id, principal.getName());
        } else {
            documents.requestDelete(id, principal.getName());
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Unpublish a soft-deleted document", description = "Marks verified source groups unpublished in PostgreSQL and queues deletion of each group node and its attached Neo4j relationships when no other published source remains. Returns HTTP 202 while the worker applies the projection; poll the graph-sync job until SYNCED.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unpublish(@PathVariable String id, Principal principal) {
        return graphOperationResponse(documents.unpublishDocument(id, principal.getName()));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Restore a soft-deleted document", description = "Restores PostgreSQL drafts and retained object-store versions, republishes document-owned graph groups, and queues a retryable Neo4j projection that recreates missing group nodes and relationships from PostgreSQL.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restore(@PathVariable String id, Principal principal) {
        return graphOperationResponse(documents.restoreDocument(id, principal.getName()));
    }

    @GetMapping("/{id}/graph-sync/{jobId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get document graph synchronization status", description = "Returns PENDING, RUNNING, RETRY, or SYNCED for the lifecycle job. SYNCED means the Neo4j projection completed.")
    public ApiResponse<Map<String, Object>> graphSyncStatus(@PathVariable String id, @PathVariable String jobId) {
        return ApiResponse.ok(documents.documentGraphSyncJob(id, jobId));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> graphOperationResponse(Map<String, Object> result) {
        HttpStatus status = "SYNCED".equals(result.get("graphSyncStatus")) ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.ok(result));
    }

    @PostMapping("/{id}/approve-delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Approve document soft-delete request", description = "Completes a DELETE_REQUESTED document soft-delete, cancels active extraction, and retains recoverable drafts and source objects.")
    public ApiResponse<Void> approveDelete(@PathVariable String id, Principal principal) {
        documents.approveDelete(id, principal.getName());
        return ApiResponse.ok(null);
    }

    @GetMapping("/drafts")
    @Operation(summary = "List subject-group drafts", description = "Returns the legacy cursor-paginated flat list. SUPER_ADMIN sees DRAFT and SUBMITTED; ADMIN sees DRAFT.")
    public ApiResponse<Map<String, Object>> drafts(@RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor, Authentication authentication) {
        return ApiResponse.ok(documents.listDrafts(size, cursor, visibleDraftStates(authentication)));
    }

    @GetMapping("/draft-groups/drafts")
    @Operation(summary = "List drafts in one school/year group", description = "Use with a groupKey and academicYear returned by /draft-groups. Each draft identifies its source document version.")
    public ApiResponse<AdminCursorPage<AdminSubjectGroupDraftDto>> draftsForGroup(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam String groupKey,
            @RequestParam String academicYear, Authentication authentication) {
        return ApiResponse.ok(documents.listDraftsForGroup(size, cursor, visibleDraftStates(authentication), groupKey, academicYear));
    }

    @GetMapping("/draft-groups")
    @Operation(summary = "List school/year draft groups", description = "Returns a keyset-paginated school and academic-year summary for documents that have visible drafts.")
    public ApiResponse<AdminCursorPage<AdminDraftGroupDto>> draftGroups(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor, Authentication authentication) {
        return ApiResponse.ok(documents.listDraftGroups(size, cursor, visibleDraftStates(authentication)));
    }

    @DeleteMapping("/drafts/{draftId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Hard-delete one unpublished draft", description = "Deletes one unpublished draft after writing an audit snapshot. A draft whose extraction source is actively processing returns HTTP 409.")
    public ApiResponse<Void> hardDeleteDraft(@PathVariable String draftId, Principal principal) {
        documents.hardDeleteDraft(draftId, principal.getName());
        return ApiResponse.ok(null);
    }

    @PostMapping("/draft-groups/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Publish all reviewed groups for one school and academic year",
            description = "SUPER_ADMIN confirms and publishes the complete current DRAFT/SUBMITTED set atomically, without a prior submission step. Queues a retryable Neo4j projection.")
    public ApiResponse<AdminPublicationResult> publishDraftGroup(@Valid @RequestBody PublishDraftGroupRequest request,
            Principal principal) {
        return ApiResponse.ok(documents.publishDraftGroup(request.groupKey(), request.academicYear(),
                request.reviewedDraftIds(), principal.getName()));
    }

    @org.springframework.web.bind.annotation.PutMapping("/drafts/{draftId}")
    public ApiResponse<Void> editDraft(@PathVariable String draftId,
            @RequestParam String groupCode, @RequestParam String groupName,
            @RequestParam String subjectCodes) {
        documents.editDraft(draftId, groupCode, groupName, subjectCodes);
        return ApiResponse.ok(null);
    }

    @PostMapping("/drafts/{draftId}/submit")
    @PreAuthorize("hasRole('ADMIN')")
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

    private String visibleDraftStates(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN"))
                ? "DRAFT,SUBMITTED" : "DRAFT";
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
}
