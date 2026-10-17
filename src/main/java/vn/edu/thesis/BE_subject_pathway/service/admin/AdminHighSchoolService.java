package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminCursorPage;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminDeletedHighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminHighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolGraphSyncResult;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AdminHighSchoolRepository;

@Service
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminHighSchoolService {
    private static final Pattern THPT_TOKEN = Pattern.compile("(?iu)\\bTHPT\\b");
    private final AdminHighSchoolRepository repository;
    private final ProvinceCatalogService provinces;
    private final CursorCodec cursors;

    public AdminHighSchoolService(AdminHighSchoolRepository repository, ProvinceCatalogService provinces,
            CursorCodec cursors) {
        this.repository = repository;
        this.provinces = provinces;
        this.cursors = cursors;
    }

    public AdminCursorPage<AdminHighSchoolDto> list(int requestedSize, String cursor) {
        int size = Math.max(1, Math.min(requestedSize, 100));
        String afterCode = decodeCursor(cursor);
        List<AdminHighSchoolDto> rows = repository.listActive(afterCode, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminHighSchoolDto> items = rows.subList(0, Math.min(size, rows.size()));
        String nextCursor = hasNext ? cursors.encode(items.get(items.size() - 1).code()) : "";
        return new AdminCursorPage<>(items, hasNext, nextCursor);
    }

    public AdminHighSchoolDto get(String code) {
        return repository.findActive(normalizeCode(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy trường THPT"));
    }

    public AdminCursorPage<AdminDeletedHighSchoolDto> listDeleted(int requestedSize, String cursor) {
        int size = Math.max(1, Math.min(requestedSize, 100));
        String afterCode = decodeCursor(cursor);
        List<AdminDeletedHighSchoolDto> rows = repository.listDeleted(afterCode, size + 1);
        boolean hasNext = rows.size() > size;
        List<AdminDeletedHighSchoolDto> items = rows.subList(0, Math.min(size, rows.size()));
        String nextCursor = hasNext ? cursors.encode(items.get(items.size() - 1).code()) : "";
        return new AdminCursorPage<>(items, hasNext, nextCursor);
    }

    @Transactional
    public AdminHighSchoolDto create(String requestedCode, String name, String provinceCode, String actorId) {
        String normalizedName = normalizeName(name);
        String location = provinces.requireProvinceName(provinceCode);
        ensureIdentityAvailable(normalizedName, location, null);
        String code = requestedCode == null || requestedCode.isBlank()
                ? SchoolCodeGenerator.suggest(normalizedName)
                : SchoolCodeGenerator.validateCustomCode(requestedCode);
        ensureCodeAvailable(code);
        repository.create(code, normalizedName, location, actorId);
        return repository.findActive(code).orElseThrow();
    }

    @Transactional
    public AdminHighSchoolDto update(String currentCode, String requestedCode, String name, String provinceCode,
            String actorId) {
        String current = normalizeCode(currentCode);
        AdminHighSchoolDto existing = get(current);
        String normalizedName = normalizeName(name);
        String location = provinces.requireProvinceName(provinceCode);
        ensureIdentityAvailable(normalizedName, location, current);
        String newCode = requestedCode == null || requestedCode.isBlank()
                ? current
                : SchoolCodeGenerator.validateCustomCode(requestedCode);
        if (!current.equals(newCode)) ensureCodeAvailable(newCode);
        if (!current.equals(newCode) && repository.graphSourceCodeConflict(current, newCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "MÃ£ trÆ°á»ng má»›i xung Ä‘á»™t vá»›i nguá»“n nhÃ³m mÃ´n Ä‘Ã£ ghi nháº­n; cáº§n Ä‘á»‘i soÃ¡t trÆ°á»›c khi Ä‘á»•i mÃ£");
        }
        if (repository.update(current, newCode, normalizedName, location, actorId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trường đã thay đổi; hãy tải lại danh sách");
        }
        return repository.findActive(newCode).orElseThrow();
    }

    @Transactional
    public void softDelete(String code, String actorId) {
        if (repository.softDelete(normalizeCode(code), actorId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trường không tồn tại hoặc đã bị xóa");
        }
    }

    @Transactional
    public HighSchoolGraphSyncResult restore(String requestedCode, String actorId) {
        String code = normalizeCode(requestedCode);
        Map<String, Object> school = repository.lockForRestore(code);
        if (school == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y trÆ°á»ng THPT");
        }
        if (school.get("deletedAt") == null) {
            Map<String, Object> replay = repository.restoreReplay(code);
            if (replay == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "TrÆ°á»ng THPT chÆ°a bá»‹ xÃ³a má»m");
            }
            return new HighSchoolGraphSyncResult(code, Objects.toString(replay.get("graphSyncStatus"), "SYNCED"),
                    (String) replay.get("graphSyncJobId"));
        }
        if (repository.restore(code, school.get("deletedAt"), (String) school.get("deletedBy")) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TrÆ°á»ng Ä‘Ã£ thay Ä‘á»•i; hÃ£y táº£i láº¡i danh sÃ¡ch");
        }
        String jobId = repository.enqueueRestoreGraph(code);
        repository.auditRestored(actorId, code, jobId);
        return new HighSchoolGraphSyncResult(code, "PENDING", jobId);
    }

    public HighSchoolGraphSyncResult graphSyncJob(String requestedCode, String jobId) {
        String code = normalizeCode(requestedCode);
        Map<String, Object> result = repository.graphSyncJobById(code, jobId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Graph sync job khÃ´ng tá»“n táº¡i");
        }
        return new HighSchoolGraphSyncResult(code, (String) result.get("graphSyncStatus"),
                (String) result.get("graphSyncJobId"));
    }

    private String decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        String decoded = cursors.decode(cursor);
        if (decoded.isBlank() || decoded.length() > 30) {
            throw new IllegalArgumentException("Cursor trường THPT không hợp lệ");
        }
        return decoded;
    }

    private void ensureCodeAvailable(String code) {
        repository.lockCode(code);
        if (repository.codeInUse(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã trường đã được sử dụng; vui lòng nhập mã khác");
        }
    }

    private void ensureIdentityAvailable(String name, String location, String excludedCode) {
        repository.lockIdentity(name, location);
        if (repository.proposedIdentityExists(name, location, excludedCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trường cùng tên và tỉnh/thành đã tồn tại");
        }
    }

    private String normalizeCode(String code) {
        return SchoolCodeGenerator.validateCustomCode(code);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 255) {
            throw new IllegalArgumentException("Tên trường phải có từ 1 đến 255 ký tự");
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (!THPT_TOKEN.matcher(normalized).find()) {
            throw new IllegalArgumentException("Tên trường cần có cụm THPT");
        }
        return normalized;
    }
}
