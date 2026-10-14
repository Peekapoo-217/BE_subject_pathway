package vn.edu.thesis.BE_subject_pathway.repository.admin;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminManagementRepository {
    private final JdbcTemplate jdbc;

    public AdminManagementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> listUsers(String role, String createdAt, String id, int limit) {
        return jdbc.queryForList("SELECT id::text AS id, username, role, active, created_at AS \"createdAt\" "
                + "FROM public.app_users WHERE (?::text IS NULL OR role=?) AND (?::timestamptz IS NULL OR (created_at,id)<(?::timestamptz,?::uuid)) "
                + "ORDER BY created_at DESC,id DESC LIMIT ?", role, role, createdAt, createdAt, id, limit);
    }

    public Map<String, Object> createUser(String id, String username, String passwordHash, String role) {
        jdbc.update("INSERT INTO public.app_users(id,username,password_hash,role) VALUES (?::uuid,?,?,?)",
                id, username, passwordHash, role);
        return jdbc.queryForMap("SELECT id::text AS id, username, role, active, created_at AS \"createdAt\" FROM public.app_users WHERE id=?::uuid", id);
    }

    public boolean isLastActiveSuperAdmin(String id) {
        Integer remaining = jdbc.queryForObject("SELECT count(*) FROM public.app_users WHERE role='SUPER_ADMIN' AND active AND id<>?::uuid", Integer.class, id);
        Integer target = jdbc.queryForObject("SELECT count(*) FROM public.app_users WHERE id=?::uuid AND role='SUPER_ADMIN' AND active", Integer.class, id);
        return target != null && target == 1 && (remaining == null || remaining == 0);
    }

    public int updateUser(String id, String role, boolean active) {
        return jdbc.update("UPDATE public.app_users SET role=?,active=?,token_version=token_version+1,updated_at=now() WHERE id=?::uuid", role, active, id);
    }

    public int resetPassword(String id, String hash) {
        return jdbc.update("UPDATE public.app_users SET password_hash=?,token_version=token_version+1,updated_at=now() WHERE id=?::uuid", hash, id);
    }

    public List<Map<String, Object>> listCombinations(Boolean active, String after, int limit) {
        return jdbc.queryForList("SELECT combination_id AS id, combination_display AS display, component_count AS \"componentCount\", is_active AS active "
                + "FROM admission_combinations WHERE (?::boolean IS NULL OR is_active=?) AND (?::text IS NULL OR combination_id>?) "
                + "ORDER BY combination_id LIMIT ?", active, active, after, after, limit);
    }

    public List<Map<String, Object>> findSubjects(List<String> codes) {
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        return jdbc.queryForList("SELECT code,name FROM subjects WHERE code IN (" + placeholders + ") ORDER BY code", codes.toArray());
    }

    public void createCombination(String id, String display, List<Map<String, Object>> subjects) {
        jdbc.update("INSERT INTO admission_combinations(combination_id,combination_display,component_count,is_active) VALUES (?,?,?,true)", id, display, subjects.size());
        for (Map<String, Object> subject : subjects)
            jdbc.update("INSERT INTO admission_combination_components(combination_id,component_code,component_name,component_type,subject_code,weight) VALUES (?,?,?,'SUBJECT',?,1)",
                    id, subject.get("code"), subject.get("name"), subject.get("code"));
    }

    public int updateCombinationDisplay(String id, String display) {
        return jdbc.update("UPDATE admission_combinations SET combination_display=?,updated_at=now() WHERE combination_id=?", display, id);
    }

    public int archiveCombination(String id) {
        return jdbc.update("UPDATE admission_combinations SET is_active=false,updated_at=now() WHERE combination_id=? AND is_active=true", id);
    }

    public List<Map<String, Object>> listLlmConfigurations() {
        return jdbc.queryForList("SELECT id::text AS id,purpose,endpoint,model,active,(encrypted_api_key <> '') AS \"keyConfigured\",updated_at AS \"updatedAt\" FROM admin.llm_configurations ORDER BY purpose");
    }

    public Map<String, Object> activeLlmConfiguration(String purpose) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT endpoint,model,encrypted_api_key FROM admin.llm_configurations WHERE purpose=? AND active=true", purpose);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public void saveLlmConfiguration(String actorId, String purpose, String endpoint, String model, String encrypted, boolean active) {
        jdbc.update("INSERT INTO admin.llm_configurations(purpose,endpoint,model,encrypted_api_key,active,updated_by) VALUES (?,?,?,?,?,?::uuid) "
                        + "ON CONFLICT (purpose) DO UPDATE SET endpoint=excluded.endpoint, model=excluded.model, "
                        + "encrypted_api_key=excluded.encrypted_api_key, active=excluded.active, updated_by=excluded.updated_by, updated_at=now()",
                purpose, endpoint, model, encrypted, active, actorId);
    }
}
