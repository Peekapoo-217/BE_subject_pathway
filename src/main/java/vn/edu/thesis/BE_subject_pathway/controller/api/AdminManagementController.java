package vn.edu.thesis.BE_subject_pathway.controller.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import vn.edu.thesis.BE_subject_pathway.service.admin.AdminManagementService;

@RestController
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminManagementController {
    private final AdminManagementService management;
    public AdminManagementController(AdminManagementService management) { this.management = management; }

    public record CreateUserRequest(@NotBlank @Size(max=80) String username,
                                    @NotBlank @Size(min=6,max=200, message="Mật khẩu phải có từ 6 đến 200 ký tự") String password,
                                    @NotBlank String role) {}
    public record UpdateUserRequest(@NotBlank String role, boolean active) {}
    public record PasswordRequest(@NotBlank @Size(min=6,max=200, message="Mật khẩu phải có từ 6 đến 200 ký tự") String password) {}
    public record CreateCombinationRequest(@NotBlank String display, @Size(min=2,max=10) List<String> subjectCodes) {}
    public record DisplayRequest(@NotBlank String display) {}
    public record LlmConfigRequest(@NotBlank String purpose, @NotBlank String endpoint,
                                   @NotBlank String model, @NotBlank String apiKey, boolean active) {}

    @GetMapping("/users")
    public ApiResponse<Map<String,Object>> users(@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String cursor, @RequestParam(required=false) String role) {
        return ApiResponse.ok(management.listUsers(size, cursor, role));
    }
    @PostMapping("/users")
    public ApiResponse<Map<String,Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(management.createUser(request.username(), request.password(), request.role()));
    }
    @PutMapping("/users/{id}")
    public ApiResponse<Void> updateUser(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        management.updateUser(id, request.role(), request.active()); return ApiResponse.ok(null);
    }
    @PatchMapping("/users/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable String id, @Valid @RequestBody PasswordRequest request) {
        management.resetPassword(id, request.password()); return ApiResponse.ok(null);
    }
    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deactivateUser(@PathVariable String id) {
        management.updateUser(id, "USER", false); return ApiResponse.ok(null);
    }

    @GetMapping("/combinations")
    public ApiResponse<Map<String,Object>> combinations(@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String cursor, @RequestParam(required=false) Boolean active) {
        return ApiResponse.ok(management.listCombinations(size, cursor, active));
    }
    @PostMapping("/combinations")
    public ApiResponse<Map<String,Object>> createCombination(@Valid @RequestBody CreateCombinationRequest request) {
        return ApiResponse.ok(management.createCombination(request.display(), request.subjectCodes()));
    }
    @PutMapping("/combinations/{id}")
    public ApiResponse<Void> updateCombination(@PathVariable String id, @Valid @RequestBody DisplayRequest request) {
        management.updateCombinationDisplay(id, request.display()); return ApiResponse.ok(null);
    }
    @DeleteMapping("/combinations/{id}")
    public ApiResponse<Void> archiveCombination(@PathVariable String id) {
        management.archiveCombination(id); return ApiResponse.ok(null);
    }

    @GetMapping("/llm-configurations")
    public ApiResponse<List<Map<String,Object>>> llmConfigurations() { return ApiResponse.ok(management.listLlmConfigurations()); }
    @PostMapping("/llm-configurations")
    public ApiResponse<Void> saveLlmConfiguration(Principal principal, @Valid @RequestBody LlmConfigRequest request) {
        management.saveLlmConfiguration(principal.getName(), request.purpose(), request.endpoint(), request.model(), request.apiKey(), request.active());
        return ApiResponse.ok(null);
    }
}
