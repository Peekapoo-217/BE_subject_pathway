package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.thesis.BE_subject_pathway.repository.admin.AdminManagementRepository;

@Service
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminManagementService {
    private final AdminManagementRepository repository;
    private final PasswordEncoder passwords;
    private final String encryptionKey;
    private final CursorCodec cursors;
    private final SecureRandom random = new SecureRandom();

    public AdminManagementService(AdminManagementRepository repository, PasswordEncoder passwords, CursorCodec cursors,
            @Value("${LLM_CONFIG_ENCRYPTION_KEY:}") String encryptionKey) {
        this.repository = repository;
        this.passwords = passwords;
        this.cursors = cursors;
        this.encryptionKey = encryptionKey;
    }

    public Map<String, Object> listUsers(int size, String cursor, String role) {
        int limit = Math.max(1, Math.min(size, 100));
        Cursor page = parseCursor(cursor, role == null ? "" : role);
        var rows = repository.listUsers(role, page == null ? null : page.createdAt,
                page == null ? null : page.id, limit + 1);
        return pageResult(rows, limit, "createdAt", role == null ? "" : role);
    }

    @Transactional
    public Map<String, Object> createUser(String username, String password, String role) {
        requireRole(role);
        String id = UUID.randomUUID().toString();
        return repository.createUser(id, username.trim(), passwords.encode(password), role);
    }

    @Transactional
    public void updateUser(String id, String role, boolean active) {
        requireRole(role);
        if (!active || !"SUPER_ADMIN".equals(role)) {
            if (repository.isLastActiveSuperAdmin(id))
                throw new IllegalArgumentException("Không thể vô hiệu hóa hoặc hạ quyền SUPER_ADMIN cuối cùng");
        }
        int changed = repository.updateUser(id, role, active);
        if (changed != 1)
            throw new IllegalArgumentException("Không tìm thấy tài khoản");
        // The version change invalidates every Redis session and access token for this
        // account.
    }

    @Transactional
    public void resetPassword(String id, String password) {
        int changed = repository.resetPassword(id, passwords.encode(password));
        if (changed != 1)
            throw new IllegalArgumentException("Không tìm thấy tài khoản");
        // Refresh and bearer authentication both compare this version with PostgreSQL.
    }

    public Map<String, Object> listCombinations(int size, String cursor, Boolean active) {
        int limit = Math.max(1, Math.min(size, 100));
        String filter = active == null ? "" : active.toString();
        String after = cursor == null || cursor.isBlank() ? null : decodeCombinationCursor(cursor, filter);
        var rows = repository.listCombinations(active, after, limit + 1);
        return pageResult(rows, limit, "id", filter);
    }

    @Transactional
    public Map<String, Object> createCombination(String display, List<String> subjectCodes) {
        List<String> codes = subjectCodes.stream().distinct().sorted().toList();
        if (codes.size() < 2 || codes.size() > 10 || codes.size() != subjectCodes.size())
            throw new IllegalArgumentException("Tổ hợp cần 2–10 mã môn khác nhau");
        List<Map<String, Object>> subjects = repository.findSubjects(codes);
        if (subjects.size() != codes.size())
            throw new IllegalArgumentException("Có mã môn không tồn tại trong danh mục");
        String id = String.join("_", codes);
        if (id.length() > 255)
            throw new IllegalArgumentException("Mã canonical quá dài");
        repository.createCombination(id, display.trim(), subjects);
        return Map.of("id", id, "display", display.trim(), "componentCount", codes.size(), "active", true);
    }

    public void updateCombinationDisplay(String id, String display) {
        if (repository.updateCombinationDisplay(id, display.trim()) != 1)
            throw new IllegalArgumentException("Không tìm thấy tổ hợp");
    }

    public void archiveCombination(String id) {
        if (repository.archiveCombination(id) != 1)
            throw new IllegalArgumentException("Không tìm thấy tổ hợp đang hoạt động");
    }

    public List<Map<String, Object>> listLlmConfigurations() {
        return repository.listLlmConfigurations();
    }

    public Map<String, Object> activeLlmConfiguration(String purpose) {
        Map<String, Object> row = repository.activeLlmConfiguration(purpose);
        return Map.of("endpoint", row.get("endpoint"), "model", row.get("model"), "apiKey",
                decrypt((String) row.get("encrypted_api_key")));
    }

    public void saveLlmConfiguration(String actorId, String purpose, String endpoint, String model, String apiKey,
            boolean active) {
        if (!List.of("EXTRACTION", "INTENT", "CHAT").contains(purpose))
            throw new IllegalArgumentException("Mục đích LLM không hợp lệ");
        if (!endpoint.matches("https?://[^\\s]+") || endpoint.length() > 1000)
            throw new IllegalArgumentException("Endpoint phải là URL HTTP(S) hợp lệ");
        if (model.isBlank() || model.length() > 255)
            throw new IllegalArgumentException("Tên model không hợp lệ");
        String encrypted = encrypt(apiKey);
        repository.saveLlmConfiguration(actorId, purpose, endpoint, model, encrypted, active);
    }

    private String encrypt(String value) {
        if (encryptionKey.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("LLM_CONFIG_ENCRYPTION_KEY must contain at least 32 bytes");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt LLM API key", exception);
        }
    }

    private String decrypt(String value) {
        if (encryptionKey.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("LLM_CONFIG_ENCRYPTION_KEY must contain at least 32 bytes");
        try {
            byte[] combined = Base64.getDecoder().decode(value);
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(combined, 12, combined.length);
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt configured LLM API key", exception);
        }
    }

    private record Cursor(String createdAt, String id) {
    }

    private Cursor parseCursor(String cursor, String filter) {
        if (cursor == null || cursor.isBlank())
            return null;
        try {
            String[] parts = cursors.decode(cursor).split("\\|", 3);
            if (parts.length != 3 || !parts[2].equals(filter))
                throw new IllegalArgumentException();
            return new Cursor(parts[0], parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cursor không hợp lệ");
        }
    }

    private String decodeCombinationCursor(String cursor, String filter) {
        try {
            String[] parts = cursors.decode(cursor).split("\\|", 2);
            if (parts.length != 2 || !parts[1].equals(filter))
                throw new IllegalArgumentException();
            return parts[0];
        } catch (Exception e) {
            throw new IllegalArgumentException("Cursor không hợp lệ");
        }
    }

    private Map<String, Object> pageResult(List<Map<String, Object>> rows, int size, String key, String filter) {
        boolean hasNext = rows.size() > size;
        List<Map<String, Object>> items = rows.subList(0, Math.min(size, rows.size()));
        String cursor = "";
        if (hasNext && !items.isEmpty()) {
            Map<String, Object> last = items.get(items.size() - 1);
            String raw = key.equals("id") ? last.get(key).toString() + "|" + filter
                    : last.get("createdAt").toString() + "|" + last.get("id") + "|" + filter;
            cursor = cursors.encode(raw);
        }
        return Map.of("items", items, "hasNext", hasNext, "nextCursor", cursor);
    }

    private void requireRole(String role) {
        if (!List.of("SUPER_ADMIN", "ADMIN", "USER").contains(role))
            throw new IllegalArgumentException("Role không hợp lệ");
    }
}
