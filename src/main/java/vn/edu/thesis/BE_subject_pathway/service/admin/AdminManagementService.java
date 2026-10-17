package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final CursorCodec cursors;

    public AdminManagementService(AdminManagementRepository repository, PasswordEncoder passwords, CursorCodec cursors) {
        this.repository = repository;
        this.passwords = passwords;
        this.cursors = cursors;
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
