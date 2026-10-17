package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminCursorPage;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminDraftGroupDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminSubjectGroupDraftDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminPublicationResult;
import vn.edu.thesis.BE_subject_pathway.dto.response.ProvinceDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SchoolCodeSuggestionDto;
import vn.edu.thesis.BE_subject_pathway.repository.admin.SchoolDocumentRepository;
import vn.edu.thesis.BE_subject_pathway.repository.admin.SchoolDocumentRepository.DraftRow;

@Service
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class SchoolDocumentService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private record UploadType(String contentType, String extension) {}
    private record ProposedSchool(String schoolCode, String name, String location, String code) {}
    private final SchoolDocumentRepository repository;
    private final ObjectStorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CursorCodec cursors;
    private final ProvinceCatalogService provinces;
    private final long maxBytes;

    public SchoolDocumentService(SchoolDocumentRepository repository, ObjectStorageService storage, CursorCodec cursors,
            ProvinceCatalogService provinces,
            @Value("${app.storage.max-upload-bytes:52428800}") long maxBytes) {
        this.repository = repository;
        this.storage = storage;
        this.cursors = cursors;
        this.provinces = provinces;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public Map<String, Object> upload(String userId, MultipartFile file, String title, String year,
            String schoolCode, String proposedName, String provinceCode, String proposedCode) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Tên tài liệu không được để trống");
        UploadType type = validateDocument(file);
        validateAcademicYear(year);
        ProposedSchool proposedSchool = resolveSchool(schoolCode, proposedName, provinceCode, proposedCode, null);
        String documentId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        String objectKey = "school-documents/" + documentId + "/versions/1/" + versionId + type.extension();
        String sha = sha256(file);
        boolean uploaded = false;
        try {
            try (InputStream input = file.getInputStream()) {
                storage.put(objectKey, input, file.getSize(), type.contentType());
                uploaded = true;
            }
            return repository.createDocument(documentId, versionId, userId, proposedSchool.schoolCode(),
                    proposedSchool.name(), proposedSchool.location(), proposedSchool.code(), title.trim(), year.trim(), objectKey,
                    safeFilename(file.getOriginalFilename(), type.extension()), type.contentType(), file.getSize(), sha);
        } catch (Exception exception) {
            if (uploaded)
                try {
                    storage.delete(objectKey);
                } catch (Exception ignored) {
                }
            if (exception instanceof RuntimeException runtime)
                throw runtime;
            throw new IllegalStateException("Không thể lưu tài liệu", exception);
        }
    }

    @Transactional
    public void updateMetadata(String id, String title, String academicYear, String schoolCode, String proposedName,
            String provinceCode, String proposedCode) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Tên tài liệu không được để trống");
        validateAcademicYear(academicYear);
        ProposedSchool proposedSchool = null;
        if (schoolCode != null || proposedName != null || provinceCode != null || proposedCode != null) {
            proposedSchool = resolveSchool(schoolCode, proposedName, provinceCode, proposedCode, id);
        }
        int changed = repository.updateMetadata(id, title.trim(), academicYear.trim(),
                proposedSchool == null ? null : proposedSchool.schoolCode(),
                proposedSchool == null ? null : proposedSchool.name(),
                proposedSchool == null ? null : proposedSchool.location(),
                proposedSchool == null ? null : proposedSchool.code());
        if (changed != 1)
            throw new IllegalArgumentException("Tài liệu không tồn tại hoặc không thể sửa ở trạng thái hiện tại");
    }

    public List<ProvinceDto> listProvinces() {
        return provinces.list();
    }

    public SchoolCodeSuggestionDto suggestSchoolCode(String schoolName, String provinceCode, String customCode) {
        String location = provinces.requireProvinceName(provinceCode);
        String code = customCode == null || customCode.isBlank()
                ? SchoolCodeGenerator.suggest(schoolName)
                : SchoolCodeGenerator.validateCustomCode(customCode);
        Map<String, Object> existingSchoolByIdentity = repository.findSchoolByNameAndLocation(schoolName, location);
        if (hasSchoolRecord(existingSchoolByIdentity)) {
            return new SchoolCodeSuggestionDto(code, false, true,
                    (String) existingSchoolByIdentity.get("code"), (String) existingSchoolByIdentity.get("name"),
                    (String) existingSchoolByIdentity.get("location"));
        }
        Map<String, Object> existing = repository.findSchoolByCode(code);
        String existingName = hasSchoolRecord(existing) ? (String) existing.get("name") : null;
        String existingLocation = hasSchoolRecord(existing) ? (String) existing.get("location") : null;
        boolean sameSchool = hasSchoolRecord(existing) && normalizeSchoolName(existingName).equals(normalizeSchoolName(schoolName))
                && normalizeSchoolName(existingLocation).equals(normalizeSchoolName(location));
        boolean conflictWithDraft = repository.proposedSchoolCodeConflicts(code, schoolName, location, null);
        return new SchoolCodeSuggestionDto(code, !hasSchoolRecord(existing) && !conflictWithDraft,
                sameSchool, hasSchoolRecord(existing) ? code : null, existingName, existingLocation);
    }

    public Map<String, Object> list(String status, int size, String cursor) {
        return list(status, false, size, cursor);
    }

    public Map<String, Object> listDeleted(int size, String cursor) {
        return list("DELETED", true, size, cursor);
    }

    private Map<String, Object> list(String status, boolean deleted, int size, String cursor) {
        int limit = Math.max(1, Math.min(size, 100));
        String filter = (deleted ? "deleted:" : "active:") + (status == null ? "" : status);
        Cursor decoded = decodeCursor(cursor, filter);
        List<Map<String, Object>> rows = repository.list(status, deleted, decoded == null ? null : decoded.createdAt,
                decoded == null ? null : decoded.id, limit + 1);
        return page(rows, limit, filter);
    }

    public Map<String, Object> document(String id, boolean allowDeleted) {
        Map<String, Object> document = repository.findDocument(id);
        if (document == null || (document.get("deletedAt") != null && !allowDeleted)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "KhÃ´ng tÃ¬m tháº¥y tÃ i liá»‡u");
        }
        document.remove("objectKey");
        return document;
    }

    public Map<String, Object> listDrafts(int size, String cursor, String status) {
        int limit = Math.max(1, Math.min(size, 100));
        Cursor decoded = decodeCursor(cursor, status);
        List<Map<String, Object>> rows = repository.listDrafts(status, decoded == null ? null : decoded.createdAt,
                decoded == null ? null : decoded.id, limit + 1);
        return page(rows, limit, status);
    }

    public AdminCursorPage<AdminDraftGroupDto> listDraftGroups(int size, String cursor, String status) {
        int limit = Math.max(1, Math.min(size, 100));
        DraftGroupsCursor decoded = decodeTypedCursor(cursor, DraftGroupsCursor.class);
        if (decoded != null && !decoded.status().equals(status))
            throw new IllegalArgumentException("Cursor không hợp lệ");
        List<AdminDraftGroupDto> rows = repository.listDraftGroups(status,
                decoded == null ? null : decoded.groupKey(), decoded == null ? null : decoded.academicYear(), limit + 1);
        boolean hasNext = rows.size() > limit;
        List<AdminDraftGroupDto> items = rows.subList(0, Math.min(limit, rows.size()));
        String nextCursor = "";
        if (hasNext && !items.isEmpty()) {
            AdminDraftGroupDto last = items.get(items.size() - 1);
            nextCursor = encodeTypedCursor(new DraftGroupsCursor(last.groupKey(), last.academicYear(), status));
        }
        return new AdminCursorPage<>(items, hasNext, nextCursor);
    }

    public AdminCursorPage<AdminSubjectGroupDraftDto> listDraftsForGroup(int size, String cursor,
            String status, String groupKey, String academicYear) {
        int limit = Math.max(1, Math.min(size, 100));
        DraftItemsCursor decoded = decodeTypedCursor(cursor, DraftItemsCursor.class);
        if (decoded != null && (!decoded.status().equals(status) || !decoded.groupKey().equals(groupKey)
                || !decoded.academicYear().equals(academicYear)))
            throw new IllegalArgumentException("Cursor không hợp lệ");
        List<AdminSubjectGroupDraftDto> rows = repository.listDraftsForGroup(groupKey, academicYear, status,
                decoded == null ? null : decoded.createdAt(), decoded == null ? null : decoded.id(), limit + 1);
        boolean hasNext = rows.size() > limit;
        List<AdminSubjectGroupDraftDto> items = rows.subList(0, Math.min(limit, rows.size()));
        String nextCursor = "";
        if (hasNext && !items.isEmpty()) {
            AdminSubjectGroupDraftDto last = items.get(items.size() - 1);
            nextCursor = encodeTypedCursor(new DraftItemsCursor(last.createdAt(), last.id(), groupKey, academicYear, status));
        }
        return new AdminCursorPage<>(items, hasNext, nextCursor);
    }

    @Transactional
    public void rejectDraft(String draftId, String actorId, String note) {
        if (!repository.lockDraftDocumentForReview(draftId))
            throw new IllegalArgumentException("Không thể từ chối bản nháp của tài liệu đang chờ xóa hoặc đã xóa");
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
            validateGroup(groupCode, groupName, codes);
            for (String code : codes) {
                if (!repository.subjectExists(code))
                    throw new IllegalArgumentException("Mã môn chưa có trong danh mục: " + code);
            }
            if (!repository.lockDraftDocumentForReview(draftId))
                throw new IllegalArgumentException("Không thể sửa bản nháp của tài liệu đang chờ xóa hoặc đã xóa");
            int changed = repository.editDraft(draftId, groupCode.trim(), groupName.trim(), mapper.writeValueAsString(codes));
            if (changed != 1)
                throw new IllegalArgumentException("Bản nháp không tồn tại hoặc đã được xử lý");
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Danh sách mã môn phải là JSON array", exception);
        }
    }

    @Transactional
    public void submitDraft(String draftId, String actorId) {
        if (!repository.lockDraftDocumentForReview(draftId))
            throw new IllegalArgumentException("Không thể gửi duyệt bản nháp của tài liệu đang chờ xóa hoặc đã xóa");
        int count = repository.submitDraft(draftId);
        if (count != 1)
            throw new IllegalArgumentException("Bản nháp không tồn tại hoặc đã gửi duyệt");
        repository.auditSubmitted(draftId, actorId);
    }

    public String downloadUrl(String documentId, boolean allowDeleted) {
        document(documentId, allowDeleted);
        String key = repository.latestObjectKey(documentId, allowDeleted);
        if (!storage.exists(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Tệp không tồn tại trong kho lưu trữ đang được cấu hình");
        }
        return storage.presignedGet(key);
    }

    @Transactional
    public void retryExtraction(String documentId) {
        if (repository.retryLatestFailedJob(documentId) != 1)
            throw new IllegalArgumentException("Chỉ có thể thử lại tài liệu có lần trích xuất mới nhất thất bại");
    }

    @Transactional
    public void requestDelete(String id, String actorId) {
        int changed = repository.requestDelete(id, actorId);
        if (changed != 1)
            throw new IllegalArgumentException("Tài liệu không tồn tại hoặc đã có yêu cầu xóa");
        repository.auditDocumentSoftDeleted(id, actorId, "DOCUMENT_DELETE_REQUESTED");
    }

    @Transactional
    public void approveDelete(String id, String actorId) {
        int count = repository.approveDelete(id, actorId);
        if (count != 1)
            throw new IllegalArgumentException("Không tìm thấy yêu cầu xóa đang chờ duyệt");
        repository.cancelActiveJobs(id);
        repository.softDeleteDraftsForDocument(id, actorId);
        repository.auditDeleteApproved(id, actorId);
    }

    @Transactional
    public void softDeleteDocument(String id, String actorId) {
        if (repository.softDeleteDocument(id, actorId) != 1)
            throw new IllegalArgumentException("Tài liệu không tồn tại hoặc đã được xóa");
        repository.cancelActiveJobs(id);
        repository.softDeleteDraftsForDocument(id, actorId);
        repository.auditDocumentSoftDeleted(id, actorId, "DOCUMENT_SOFT_DELETED");
    }

    @Transactional
    public Map<String, Object> unpublishDocument(String id, String actorId) {
        Map<String, Object> document = repository.lockLifecycleDocument(id);
        if (document == null || document.get("deletedAt") == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chá»‰ cÃ³ thá»ƒ ngá»«ng cÃ´ng bá»‘ tÃ i liá»‡u Ä‘Ã£ xÃ³a má»m");
        }
        if ("UNPUBLISHED".equals(document.get("graphPublicationStatus"))) {
            return graphOperationResult(id, "UNPUBLISHED", repository.latestDocumentGraphSyncJob(id));
        }
        if (!repository.isSchoolActiveForDocument(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cáº§n khÃ´i phá»¥c trÆ°á»ng THPT trÆ°á»›c khi thao tÃ¡c tÃ i liá»‡u");
        }
        if (repository.hasUnattributedGroupSource(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "KhÃ´ng thá»ƒ xÃ¡c minh nhÃ³m mÃ´n chá»‰ thuá»™c tÃ i liá»‡u nÃ y; graph chÆ°a bá»‹ thay Ä‘á»•i");
        }
        List<String> groups = verifiedDocumentGroups(id, true);
        if (repository.setDocumentGraphPublication(id, false) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TÃ i liá»‡u Ä‘Ã£ thay Ä‘á»•i; hÃ£y táº£i láº¡i");
        }
        String jobId = enqueueDocumentGraphSync(id, document, groups);
        repository.auditDocumentSoftDeleted(id, actorId, "DOCUMENT_UNPUBLISHED");
        return graphOperationResult(id, "UNPUBLISHED", jobId == null ? null : Map.of(
                "graphSyncJobId", jobId, "graphSyncStatus", "PENDING"));
    }

    @Transactional
    public Map<String, Object> restoreDocument(String id, String actorId) {
        Map<String, Object> document = repository.lockLifecycleDocument(id);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        if (document.get("deletedAt") == null) {
            Map<String, Object> replay = repository.restoreReplay(id);
            if (replay == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Document is not soft-deleted");
            }
            return graphOperationResult(id, "PUBLISHED", replay);
        }
        if (!repository.isSchoolActiveForDocument(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cáº§n khÃ´i phá»¥c trÆ°á»ng THPT trÆ°á»›c khi khÃ´i phá»¥c tÃ i liá»‡u");
        }
        List<String> groups = verifiedDocumentGroups(id,
                "PUBLISHED".equals(document.get("statusBeforeDelete"))
                        || "UNPUBLISHED".equals(document.get("graphPublicationStatus")));
        List<String> objectKeys = repository.objectKeys(id);
        if (objectKeys.isEmpty() || objectKeys.stream().anyMatch(key -> !storage.exists(key))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Thiáº¿u tá»‡p trong kho lÆ°u trá»¯ Ä‘ang cáº¥u hÃ¬nh; bá»• sung tá»‡p Ä‘Ãºng bucket rá»“i thá»­ láº¡i");
        }
        if (repository.restoreDocument(id) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TÃ i liá»‡u Ä‘Ã£ thay Ä‘á»•i; hÃ£y táº£i láº¡i");
        }
        String jobId = enqueueDocumentGraphSync(id, document, groups);
        repository.auditDocumentRestored(id, actorId, jobId);
        return graphOperationResult(id, "PUBLISHED", jobId == null ? null : Map.of(
                "graphSyncJobId", jobId, "graphSyncStatus", "PENDING"));
    }

    private List<String> verifiedDocumentGroups(String documentId, boolean requireGroups) {
        List<String> sourceGroups = repository.documentGroupCodes(documentId);
        List<String> publishedGroups = repository.publishedDocumentGroupCodes(documentId);
        if (!sourceGroups.containsAll(publishedGroups)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot verify all published graph groups for this document; review its source mapping first");
        }
        if (!sourceGroups.isEmpty() && !repository.documentSourceGroupsAreActive(documentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "One or more document-owned subject groups are not active in PostgreSQL");
        }
        if (requireGroups && sourceGroups.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This document has no verified published group source for a graph lifecycle operation");
        }
        return sourceGroups;
    }

    public Map<String, Object> documentGraphSyncJob(String documentId, String jobId) {
        Map<String, Object> result = repository.findDocumentGraphSyncJob(documentId, jobId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Graph sync job not found");
        }
        return result;
    }

    private String enqueueDocumentGraphSync(String id, Map<String, Object> document, List<String> groups) {
        if (groups.isEmpty() || document.get("schoolCode") == null) return null;
        return repository.createDocumentGraphSyncJob(id, (String) document.get("schoolCode"),
                (String) document.get("academicYear"), groups);
    }

    private Map<String, Object> graphOperationResult(String id, String publicationStatus, Map<String, Object> job) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("documentId", id);
        result.put("graphPublicationStatus", publicationStatus);
        result.put("graphSyncStatus", job == null ? "SYNCED" : job.getOrDefault("graphSyncStatus", "PENDING"));
        result.put("graphSyncJobId", job == null ? null : job.get("graphSyncJobId"));
        return result;
    }

    @Transactional
    public void hardDeleteDraft(String draftId, String actorId) {
        int deleted = repository.hardDeleteDraft(draftId, actorId);
        if (deleted == -1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa nháp khi tài liệu đang được trích xuất");
        if (deleted != 1)
            throw new IllegalArgumentException("Bản nháp không tồn tại hoặc đã công bố");
    }

    @Transactional
    public AdminPublicationResult publishDraftGroup(String groupKey, String academicYear,
            List<String> reviewedDraftIds, String actorId) {
        if (groupKey == null || groupKey.isBlank() || academicYear == null || academicYear.isBlank())
            throw new IllegalArgumentException("Cần chọn trường và năm học hợp lệ");
        if (reviewedDraftIds == null || reviewedDraftIds.isEmpty()
                || reviewedDraftIds.stream().anyMatch(id -> id == null || id.isBlank())
                || new HashSet<>(reviewedDraftIds).size() != reviewedDraftIds.size()) {
            throw new IllegalArgumentException("Danh sách bản nháp đã rà soát không hợp lệ");
        }
        if (repository.lockDocumentsForPublication(groupKey, academicYear) == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không còn tài liệu đang hoạt động trong trường/năm học này");
        if (repository.hasActiveExtractionForGroup(groupKey, academicYear))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vẫn còn tài liệu đang được trích xuất");

        List<DraftRow> drafts = repository.lockDraftsForPublication(groupKey, academicYear);
        if (drafts.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không có bản nháp cần công bố");
        Set<String> actualIds = drafts.stream().map(DraftRow::id).collect(java.util.stream.Collectors.toSet());
        if (!actualIds.equals(new HashSet<>(reviewedDraftIds)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Danh sách bản nháp đã thay đổi; hãy tải lại và rà soát lại toàn bộ");
        if (drafts.stream().anyMatch(draft -> !"DRAFT".equals(draft.status()) && !"SUBMITTED".equals(draft.status())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể công bố bản nháp đang chờ xử lý");

        Set<String> draftGroupCodes = new HashSet<>();
        List<String> groupCodes = new java.util.ArrayList<>();
        Map<String, List<String>> subjectsByDraft = new java.util.LinkedHashMap<>();
        String schoolCode = drafts.get(0).schoolCode();
        String proposedName = drafts.get(0).proposedSchoolName();
        String proposedLocation = drafts.get(0).proposedSchoolLocation();
        String proposedCode = drafts.get(0).proposedSchoolCode();
        for (DraftRow draft : drafts) {
            if (!java.util.Objects.equals(schoolCode, draft.schoolCode())
                    || (schoolCode == null && (!normalizeSchoolName(proposedName).equals(normalizeSchoolName(draft.proposedSchoolName()))
                    || !normalizeSchoolName(proposedLocation).equals(normalizeSchoolName(draft.proposedSchoolLocation()))
                    || !java.util.Objects.equals(proposedCode, draft.proposedSchoolCode())))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bản nháp không cùng một trường/năm học");
            }
            List<String> subjects = parseSubjectCodes(draft.subjectCodes());
            validateGroup(draft.groupCode(), draft.groupName(), subjects);
            for (String code : subjects) {
                if (!repository.subjectExists(code))
                    throw new IllegalArgumentException("Mã môn chưa có trong danh mục: " + code);
            }
            String normalizedGroupCode = draft.groupCode().trim().toUpperCase(java.util.Locale.ROOT);
            if (!draftGroupCodes.add(normalizedGroupCode))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã nhóm bị trùng trong lô: " + draft.groupCode());
            groupCodes.add(draft.groupCode());
            subjectsByDraft.put(draft.id(), subjects);
        }

        if (schoolCode == null) {
            schoolCode = proposedCode == null || proposedCode.isBlank()
                    ? SchoolCodeGenerator.suggest(proposedName)
                    : SchoolCodeGenerator.validateCustomCode(proposedCode);
            if (repository.proposedSchoolCodeConflicts(schoolCode, proposedName, proposedLocation, null)
                    || !repository.createSchoolIfAbsent(schoolCode,
                            proposedName.trim().replaceAll("\\s+", " "), proposedLocation))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên trường trùng với một mã trường đã tồn tại");
        }
        Set<String> existingCodes = repository.publishedGroupCodes(schoolCode, academicYear);
        List<String> conflicts = groupCodes.stream()
                .filter(code -> existingCodes.contains(code.trim().toUpperCase(java.util.Locale.ROOT)))
                .distinct().toList();
        if (!conflicts.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Mã nhóm đã được công bố; không có nhóm nào trong lô được công bố: " + String.join(", ", conflicts));

        for (DraftRow draft : drafts) {
            for (String code : subjectsByDraft.get(draft.id()))
                repository.addSubjectGroup(schoolCode, draft.groupCode(), draft.groupName(), code, academicYear);
        }
        String batchId = UUID.randomUUID().toString();
        for (DraftRow draft : drafts)
            repository.publishDraft(draft.id(), actorId, draft.documentId(), schoolCode, draft.groupCode(), batchId);
        repository.createGraphSyncJob(batchId, schoolCode, academicYear, groupCodes);
        return new AdminPublicationResult(batchId, drafts.size(), "PENDING");
    }

    private List<String> parseSubjectCodes(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Danh sách mã môn trong bản nháp không hợp lệ", exception);
        }
    }

    private void validateGroup(String groupCode, String groupName, List<String> codes) {
        if (groupCode == null || groupCode.isBlank() || groupCode.trim().length() > 30
                || groupName == null || groupName.isBlank() || groupName.trim().length() > 100)
            throw new IllegalArgumentException("Mã nhóm và tên nhóm không hợp lệ hoặc vượt giới hạn độ dài");
        if (codes == null || codes.isEmpty() || codes.size() > 4
                || codes.stream().anyMatch(code -> code == null || code.isBlank())
                || codes.stream().distinct().count() != codes.size()) {
            throw new IllegalArgumentException("Mỗi nhóm cần từ 1 đến 4 mã môn hợp lệ và không trùng nhau");
        }
    }

    private String normalizeSchoolName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private boolean hasSchoolRecord(Map<String, Object> school) {
        return school != null && !school.isEmpty();
    }

    private record Cursor(String createdAt, String id) {
    }

    private record DraftGroupsCursor(String groupKey, String academicYear, String status) {}
    private record DraftItemsCursor(String createdAt, String id, String groupKey, String academicYear, String status) {}

    private <T> T decodeTypedCursor(String cursor, Class<T> type) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            return mapper.readValue(cursors.decode(cursor), type);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cursor không hợp lệ");
        }
    }

    private String encodeTypedCursor(Object cursor) {
        try {
            return cursors.encode(mapper.writeValueAsString(cursor));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Không thể tạo cursor", exception);
        }
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

    private UploadType validateDocument(MultipartFile file) {
        if (file.isEmpty())
            throw new IllegalArgumentException("Tệp tài liệu rỗng");
        String mime = file.getContentType();
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            UploadType type;
            if ("application/pdf".equalsIgnoreCase(mime) && startsWith(header, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                type = new UploadType("application/pdf", ".pdf");
            else if ("image/png".equalsIgnoreCase(mime) && startsWith(header, new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
                type = new UploadType("image/png", ".png");
            else if ("image/jpeg".equalsIgnoreCase(mime) && startsWith(header, new byte[] {(byte) 255, (byte) 216, (byte) 255}))
                type = new UploadType("image/jpeg", ".jpg");
            else if ("image/webp".equalsIgnoreCase(mime) && header.length >= 12 && startsWith(header, "RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))
                type = new UploadType("image/webp", ".webp");
            else
                throw new IllegalArgumentException("Chỉ chấp nhận PDF, PNG, JPEG hoặc WebP hợp lệ");
            if (file.getSize() > ("application/pdf".equals(type.contentType()) ? maxBytes : MAX_IMAGE_BYTES))
                throw new IllegalArgumentException("Tệp vượt giới hạn dung lượng");
            return type;
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Không đọc được tệp tài liệu", exception);
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length && java.util.Arrays.equals(value, 0, prefix.length, prefix, 0, prefix.length);
    }

    private String sha256(MultipartFile file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate document digest", e);
        }
    }

    private String safeFilename(String name, String extension) {
        return name == null ? "upload" + extension
                : name.replaceAll("[^A-Za-z0-9._-]", "_").substring(0, Math.min(name.length(), 255));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProposedSchool resolveSchool(String schoolCode, String schoolName, String provinceCode,
            String proposedCode, String excludedDocumentId) {
        String selectedSchoolCode = blankToNull(schoolCode);
        if (selectedSchoolCode != null) {
            if (!repository.schoolCodeExists(selectedSchoolCode)) {
                throw new IllegalArgumentException("Mã trường đã chọn không tồn tại");
            }
            return new ProposedSchool(selectedSchoolCode, null, null, null);
        }
        String name = blankToNull(schoolName);
        if (name == null) throw new IllegalArgumentException("Vui lòng nhập tên trường mới");
        String location = provinces.requireProvinceName(provinceCode);
        Map<String, Object> existingSchoolByIdentity = repository.findSchoolByNameAndLocation(name, location);
        if (hasSchoolRecord(existingSchoolByIdentity)) {
            throw new IllegalArgumentException("Trường đã tồn tại. Hãy chọn trường hiện có thay vì khai báo trường mới");
        }
        String code = proposedCode == null || proposedCode.isBlank()
                ? SchoolCodeGenerator.suggest(name)
                : SchoolCodeGenerator.validateCustomCode(proposedCode);
        Map<String, Object> existing = repository.findSchoolByCode(code);
        if (hasSchoolRecord(existing)) {
            String existingName = (String) existing.get("name");
            String existingLocation = (String) existing.get("location");
            if (normalizeSchoolName(existingName).equals(normalizeSchoolName(name))
                    && normalizeSchoolName(existingLocation).equals(normalizeSchoolName(location))) {
                throw new IllegalArgumentException("Trường đã tồn tại. Hãy chọn trường hiện có thay vì khai báo trường mới");
            }
            throw new IllegalArgumentException("Mã trường đã được sử dụng. Hãy nhập mã khác hoặc chọn trường hiện có");
        }
        if (repository.proposedSchoolCodeConflicts(code, name, location, excludedDocumentId)) {
            throw new IllegalArgumentException("Mã trường đang được dùng cho một trường khác trong hồ sơ chưa công bố");
        }
        return new ProposedSchool(null, name, location, code);
    }

    private void validateAcademicYear(String year) {
        if (year == null || !year.trim().matches("(?:19|20|21)\\d{2}")) {
            throw new IllegalArgumentException("Năm học phải là năm bắt đầu gồm 4 chữ số, từ 1900 đến 2199");
        }
    }

}
