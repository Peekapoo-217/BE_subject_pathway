package vn.edu.thesis.BE_subject_pathway.controller.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.thesis.BE_subject_pathway.service.admin.AdminManagementService;

@RestController
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/internal/llm")
public class InternalLlmConfigController {
    private final AdminManagementService management;
    private final String serviceToken;

    public InternalLlmConfigController(AdminManagementService management,
            @Value("${INTERNAL_SERVICE_TOKEN:}") String serviceToken) {
        this.management = management; this.serviceToken = serviceToken;
    }

    @GetMapping("/{purpose}")
    public Map<String, Object> activeConfiguration(@PathVariable String purpose,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String suppliedToken) {
        if (serviceToken.isBlank() || suppliedToken == null ||
                !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!java.util.List.of("EXTRACTION", "INTENT", "CHAT").contains(purpose))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return management.activeLlmConfiguration(purpose);
    }
}
