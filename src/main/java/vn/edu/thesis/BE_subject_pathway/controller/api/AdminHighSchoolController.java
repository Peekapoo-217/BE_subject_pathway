package vn.edu.thesis.BE_subject_pathway.controller.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminCursorPage;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminDeletedHighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminHighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolGraphSyncResult;
import vn.edu.thesis.BE_subject_pathway.service.admin.AdminHighSchoolService;

@RestController
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/admin/high-schools")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin high schools", description = "ADMIN/SUPER_ADMIN management of high-school catalog records")
@SecurityRequirement(name = "bearerAuth")
public class AdminHighSchoolController {
    private final AdminHighSchoolService schools;

    public AdminHighSchoolController(AdminHighSchoolService schools) {
        this.schools = schools;
    }

    public record SchoolRequest(String code, @NotBlank @Size(max = 255) String name,
            @NotBlank String provinceCode) {}

    @GetMapping
    @Operation(summary = "List high schools", description = "Returns active schools ordered by code using a signed keyset cursor.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "School page loaded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid cursor")})
    public ApiResponse<AdminCursorPage<AdminHighSchoolDto>> list(
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(schools.list(size, cursor));
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List soft-deleted high schools", description = "Only SUPER_ADMIN can inspect and restore deleted schools.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Deleted school page loaded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid cursor"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN required")})
    public ApiResponse<AdminCursorPage<AdminDeletedHighSchoolDto>> listDeleted(
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(schools.listDeleted(size, cursor));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a high school", description = "Returns one active school by its current code.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "School loaded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "School not found")})
    public ApiResponse<AdminHighSchoolDto> get(@PathVariable String code) {
        return ApiResponse.ok(schools.get(code));
    }

    @PostMapping
    @Operation(summary = "Create a high school", description = "Creates a school and queues its graph projection. The code is generated when omitted.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "School created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Code or school identity already exists")})
    public ApiResponse<AdminHighSchoolDto> create(Principal principal, @Valid @RequestBody SchoolRequest request) {
        return ApiResponse.ok(schools.create(request.code(), request.name(), request.provinceCode(), principal.getName()));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update a high school", description = "Updates school details; a changed code is recorded in code history and projected to Neo4j.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "School updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "School not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Code or school identity already exists")})
    public ApiResponse<AdminHighSchoolDto> update(Principal principal, @PathVariable String code,
            @Valid @RequestBody SchoolRequest request) {
        return ApiResponse.ok(schools.update(code, request.code(), request.name(), request.provinceCode(), principal.getName()));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Soft delete a high school", description = "Soft deletes its groups, related documents, and drafts and queues graph cleanup; R2 objects are retained.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "School soft deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "School not found or already deleted")})
    public ApiResponse<Void> softDelete(Principal principal, @PathVariable String code) {
        schools.softDelete(code, principal.getName());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{code}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Restore a soft-deleted high school", description = "Restores the school and groups soft-deleted by the same school deletion, then queues an idempotent graph reconciliation. Related documents remain soft-deleted and must be restored separately.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "School restored and graph already synchronized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "School restored; graph synchronization pending"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "School not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "School state changed or is not restorable")})
    public ResponseEntity<ApiResponse<HighSchoolGraphSyncResult>> restore(
            Principal principal, @PathVariable String code) {
        HighSchoolGraphSyncResult result = schools.restore(code, principal.getName());
        HttpStatus status = "SYNCED".equals(result.graphSyncStatus()) ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.ok(result));
    }

    @GetMapping("/{code}/graph-sync/{jobId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get high-school graph synchronization status", description = "Returns the current status of the restore projection job.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Graph sync status loaded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Graph sync job not found")})
    public ApiResponse<HighSchoolGraphSyncResult> graphSyncStatus(
            @PathVariable String code, @PathVariable String jobId) {
        return ApiResponse.ok(schools.graphSyncJob(code, jobId));
    }
}
