package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.edu.thesis.BE_subject_pathway.repository.admin.SchoolDocumentRepository;
import vn.edu.thesis.BE_subject_pathway.repository.admin.SchoolDocumentRepository.DraftRow;

@Service
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class SchoolDocumentService {
    private final SchoolDocumentRepository repository;
    private final R2StorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CursorCodec cursors;
    private final long maxBytes;

    public SchoolDocumentService(SchoolDocumentRepository repository, R2StorageService storage, CursorCodec cursors,
            @Value("${app.storage.max-upload-bytes:52428800}") long maxBytes) {
        this.repository = repository;
        this.storage = storage;
        this.cursors = cursors;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public Map<String, Object> upload(String userId, MultipartFile file, String title, String year,
            String schoolCode, String proposedName, String proposedLocation) {
        if (file.isEmpty() || file.getSize() > maxBytes)
            throw new IllegalArgumentException("PDF rỗng hoặc vượt giới hạn dung lượng");
        if (!"application/pdf".equalsIgnoreCase(file.getContentType()) || !hasPdfHeader(file))
            throw new IllegalArgumentException("Chỉ chấp nhận tệp PDF hợp lệ");
        if ((schoolCode == null || schoolCode.isBlank()) && (proposedName == null || proposedName.isBlank()))
            throw new IllegalArgumentException("Cần chọn trường hoặc khai báo tên trường mới");
        String documentId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        String objectKey = "school-documents/" + documentId + "/versions/1/" + versionId + ".pdf";
        String sha = sha256(file);
        boolean uploaded = false;
        try {
            try (InputStream input = file.getInputStream()) {
                storage.put(objectKey, input, file.getSize(), "application/pdf");
                uploaded = true;
            }
            return repository.createDocument(documentId, versionId, userId, blankToNull(schoolCode),
                    blankToNull(proposedName), blankToNull(proposedLocation), title, year, objectKey,
                    safeFilename(file.getOriginalFilename()), file.getSize(), sha);
        } catch (Exception exception) {
            if (uploaded)
                try {
                    storage.delete(objectKey);
                } catch (Exception ignored) {
                }
            if (exception instanceof RuntimeException runtime)
                throw runtime;
            throw new IllegalStateException("Không thể lưu tài liệu PDF", exception);
        }
    }

    @Transactional
    public Map<String, Object> addVersion(String documentId, String userId, MultipartFile file) {
        validatePdf(file);
        int version = repository.lockAndNextVersion(documentId);
        String versionId = UUID.randomUUID().toString();
        String objectKey = "school-documents/" + documentId + "/versions/" + version + "/" + versionId + ".pdf";
        boolean uploaded = false;
        try {
            try (InputStream input = file.getInputStream()) {
                storage.put(objectKey, input, file.getSize(), "application/pdf");
                uploaded = true;
            }
            repository.addVersion(versionId, documentId, version, objectKey,
                    safeFilename(file.getOriginalFilename()), file.getSize(), sha256(file), userId);
            return Map.of("id", versionId, "versionNumber", version, "filename",
                    safeFilename(file.getOriginalFilename()));
        } catch (Exception exception) {
            if (uploaded)
                try {
                    storage.delete(objectKey);
                } catch (Exception ignored) {
                }
            if (exception instanceof RuntimeException runtime)
                throw runtime;
            throw new IllegalStateException("Không thể lưu phiên bản PDF", exception);
        }
    }

    public void updateMetadata(String id, String title, String academicYear) {
        int changed = repository.updateMetadata(id, title.trim(), academicYear.trim());
        if (changed != 1)
            throw new IllegalArgumentException("Tài liệu không tồn tại hoặc không thể sửa ở trạng thái hiện tại");
    }

    public Map<String, Object> list(String status, int size, String cursor) {
        int limit = Math.max(1, Math.min(size, 100));
        Cursor decoded = decodeCursor(cursor, status == null ? "" : status);
        List<Map<String, Object>> rows = repository.list(status, decoded == null ? null : decoded.createdAt,
                decoded == null ? null : decoded.id, limit + 1);
        return page(rows, limit, status == null ? "" : status);
    }

    public Map<String, Object> listDrafts(int size, String cursor, String status) {
        int limit = Math.max(1, Math.min(size, 100));
        Cursor decoded = decodeCursor(cursor, status);
        List<Map<String, Object>> rows = repository.listDrafts(status, decoded == null ? null : decoded.createdAt,
                decoded == null ? null : decoded.id, limit + 1);
        return page(rows, limit, status);
    }

    @Transactional
    public void rejectDraft(String draftId, String actorId, String note) {
        int count = repository.rejectDraft(draftId, actorId, note);
        if (count != 1)
            throw new IllegalArgumentException("Bản nháp không tồn tại hoặc đã được xử lý");
        repository.auditRejected(draftId, actorId, note);
    }

    @Transactional
    public void editDraft(String draftId, String groupCode, String groupName, String subjectCodesJson) {
        try {
            List<String> codes = mapper.readValue(subjectCodesJson, new TypeReference<>() {
            });
            if (codes.size() != 4 || codes.stream().anyMatch(code -> code == null || code.isBlank()))
                throw new IllegalArgumentException("Draft phải có đúng bốn mã môn");
            int changed = repository.editDraft(draftId, groupCode.trim(), groupName.trim(), mapper.writeValueAsString(codes));
            if (changed != 1)
                throw new IllegalArgumentException("Bản nháp không tồn tại hoặc đã được xử lý");
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Danh sách mã môn phải là JSON array", exception);
        }
    }

    @Transactional
    public void submitDraft(String draftId, String actorId) {
        int count = repository.submitDraft(draftId);
        if (count != 1)
            throw new IllegalArgumentException("Bản nháp không tồn tại hoặc đã gửi duyệt");
        repository.auditSubmitted(draftId, actorId);
    }

    public String downloadUrl(String documentId) {
        String key = repository.latestObjectKey(documentId);
        return storage.presignedGet(key);
    }

    public void requestDelete(String id, String actorId) {
        int changed = repository.requestDelete(id, actorId);
        if (changed != 1)
            throw new IllegalArgumentException("Tài liệu không tồn tại hoặc đã có yêu cầu xóa");
    }

    @Transactional
    public void approveDelete(String id, String actorId) {
        int count = repository.approveDelete(id, actorId);
        if (count != 1)
            throw new IllegalArgumentException("Không tìm thấy yêu cầu xóa đang chờ duyệt");
        repository.auditDeleteApproved(id, actorId);
    }

    @Transactional
    public void approveDraft(String draftId, String actorId) {
        var rows = repository.lockSubmittedDraft(draftId);
        if (rows.isEmpty())
            throw new IllegalArgumentException("Bản nháp không tồn tại hoặc đã được duyệt");
        DraftRow draft = rows.get(0);
        try {
            List<String> subjects = mapper.readValue(draft.subjectCodes(), new TypeReference<>() {
            });
            if (subjects.size() != 4 || subjects.stream().distinct().count() != 4)
                throw new IllegalArgumentException("Mỗi nhóm phải có đúng bốn môn khác nhau");
            for (String code : subjects) {
                if (!repository.subjectExists(code))
                    throw new IllegalArgumentException("Mã môn chưa có trong danh mục: " + code);
            }
            String schoolCode = draft.schoolCode();
            if (schoolCode == null) {
                String schoolNameKey = draft.proposedSchoolName().trim().toLowerCase(java.util.Locale.ROOT);
                schoolCode = "PDF"
                        + UUID.nameUUIDFromBytes(schoolNameKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                .toString().replace("-", "").substring(0, 12).toUpperCase();
                repository.createSchoolIfAbsent(schoolCode, draft.proposedSchoolName(), draft.proposedSchoolLocation());
            }
            for (String code : subjects)
                repository.addSubjectGroup(schoolCode, draft.groupCode(), draft.groupName(), code, draft.academicYear());
            repository.publishDraft(draftId, actorId, schoolCode, draft.groupCode());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Invalid extracted draft JSON", exception);
        }
    }

    private record Cursor(String createdAt, String id) {
    }

    private Map<String, Object> page(List<Map<String, Object>> rows, int size, String filter) {
        boolean hasNext = rows.size() > size;
        List<Map<String, Object>> items = rows.subList(0, Math.min(size, rows.size()));
        String next = null;
        if (hasNext && !items.isEmpty()) {
            Map<String, Object> last = items.get(items.size() - 1);
            Object created = last.get("createdAt");
            Object id = last.get("id");
            next = cursors.encode(created.toString() + "|" + id + "|" + filter);
        }
        return Map.of("items", items, "hasNext", hasNext, "nextCursor", next == null ? "" : next);
    }

    private Cursor decodeCursor(String cursor) {
        return decodeCursor(cursor, "");
    }

    private Cursor decodeCursor(String cursor, String filter) {
        if (cursor == null || cursor.isBlank())
            return null;
        try {
            String[] values = cursors.decode(cursor).split("\\|", 3);
            if (values.length != 3 || !values[2].equals(filter == null ? "" : filter))
                throw new IllegalArgumentException();
            return new Cursor(values[0], values[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cursor không hợp lệ");
        }
    }

    private boolean hasPdfHeader(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return new String(input.readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
        } catch (Exception e) {
            return false;
        }
    }

    private void validatePdf(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > maxBytes)
            throw new IllegalArgumentException("PDF rỗng hoặc vượt giới hạn dung lượng");
        if (!"application/pdf".equalsIgnoreCase(file.getContentType()) || !hasPdfHeader(file))
            throw new IllegalArgumentException("Chỉ chấp nhận tệp PDF hợp lệ");
    }

    private String sha256(MultipartFile file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate PDF digest", e);
        }
    }

    private String safeFilename(String name) {
        return name == null ? "upload.pdf"
                : name.replaceAll("[^A-Za-z0-9._-]", "_").substring(0, Math.min(name.length(), 255));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
