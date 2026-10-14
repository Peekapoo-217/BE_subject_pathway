package vn.edu.thesis.BE_subject_pathway.repository.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminDraftGroupDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminSubjectGroupDraftDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class SchoolDocumentRepository {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    public record DraftRow(String id, String documentId, String groupCode, String groupName, String subjectCodes,
            String schoolCode, String proposedSchoolName, String proposedSchoolLocation, String proposedSchoolCode,
            String academicYear, String status) {
        public DraftRow(String id, String documentId, String groupCode, String groupName, String subjectCodes,
                String schoolCode, String proposedSchoolName, String academicYear, String status) {
            this(id, documentId, groupCode, groupName, subjectCodes, schoolCode, proposedSchoolName,
                    null, null, academicYear, status);
        }
    }

    private final JdbcTemplate jdbc;
    public SchoolDocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> createDocument(String documentId, String versionId, String userId,
            String schoolCode, String proposedName, String proposedLocation, String proposedCode, String title, String year,
            String objectKey, String filename, String contentType, long bytes, String sha) {
        jdbc.update("INSERT INTO admin.school_documents(id, school_code, proposed_school_name, proposed_school_location, "
                + "proposed_school_code, title, academic_year, created_by) "
                + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::uuid)",
                documentId, schoolCode, proposedName, proposedLocation, proposedCode, title, year, userId);
        jdbc.update("INSERT INTO admin.school_document_versions(id, document_id, version_number, object_key, original_filename, content_type, byte_size, sha256, uploaded_by) "
                + "VALUES (?::uuid, ?::uuid, 1, ?, ?, ?, ?, ?, ?::uuid)",
                versionId, documentId, objectKey, filename, contentType, bytes, sha, userId);
        queueExtraction(versionId);
        return jdbc.queryForMap("SELECT id::text AS id, title, academic_year AS \"academicYear\", status, created_at AS \"createdAt\" FROM admin.school_documents WHERE id=?::uuid", documentId);
    }

    private void queueExtraction(String versionId) {
        jdbc.update("INSERT INTO admin.extraction_log(document_version_id) VALUES (?::uuid)", versionId);
    }

    public int updateMetadata(String id, String title, String academicYear, String schoolCode, String proposedName,
            String proposedLocation, String proposedCode) {
        return jdbc.update("UPDATE admin.school_documents SET title=?,academic_year=?, school_code=coalesce(?, school_code), "
                + "proposed_school_name=CASE WHEN ? IS NOT NULL THEN NULL WHEN school_code IS NULL THEN coalesce(?, proposed_school_name) ELSE proposed_school_name END, "
                + "proposed_school_location=CASE WHEN ? IS NOT NULL THEN NULL WHEN school_code IS NULL THEN coalesce(?, proposed_school_location) ELSE proposed_school_location END, "
                + "proposed_school_code=CASE WHEN ? IS NOT NULL THEN NULL WHEN school_code IS NULL THEN coalesce(?, proposed_school_code) ELSE proposed_school_code END, "
                + "updated_at=now() "
                + "WHERE id=?::uuid AND deleted_at IS NULL AND status NOT IN ('PUBLISHED','DELETED','DELETE_REQUESTED')",
                title, academicYear, schoolCode,
                schoolCode, proposedName, schoolCode, proposedLocation, schoolCode, proposedCode, id);
    }

    public List<Map<String, Object>> list(String status, String createdAt, String id, int limit) {
        return jdbc.queryForList("SELECT d.id::text AS id, d.title, d.academic_year AS \"academicYear\", d.status, d.created_at AS \"createdAt\", "
                + "coalesce(h.name, d.proposed_school_name) AS \"schoolName\", d.school_code AS \"schoolCode\", "
                + "d.proposed_school_name AS \"proposedSchoolName\", d.proposed_school_location AS \"proposedSchoolLocation\", "
                + "d.proposed_school_code AS \"proposedSchoolCode\", coalesce(h.location, d.proposed_school_location) AS \"schoolLocation\", "
                + "v.original_filename AS \"filename\", j.error_code AS \"errorCode\", gs.status AS \"graphSyncStatus\" "
                + "FROM admin.school_documents d LEFT JOIN high_schools h ON h.code=d.school_code "
                + "JOIN LATERAL (SELECT id, original_filename FROM admin.school_document_versions WHERE document_id=d.id ORDER BY version_number DESC LIMIT 1) v ON true "
                + "LEFT JOIN LATERAL (SELECT error_code FROM admin.extraction_log WHERE document_version_id=v.id ORDER BY created_at DESC LIMIT 1) j ON true "
                + "LEFT JOIN LATERAL (SELECT status FROM admin.subject_group_graph_sync_jobs WHERE school_code=d.school_code "
                + "AND academic_year=d.academic_year ORDER BY created_at DESC, id DESC LIMIT 1) gs ON true "
                + "WHERE d.deleted_at IS NULL AND (?::text IS NULL OR d.status=?) AND (?::timestamptz IS NULL OR (d.created_at, d.id) < (?::timestamptz, ?::uuid)) "
                + "ORDER BY d.created_at DESC, d.id DESC LIMIT ?",
                status, status, createdAt, createdAt, id, limit);
    }

    public List<Map<String, Object>> listDrafts(String status, String createdAt, String id, int limit) {
        return jdbc.query("SELECT d.id::text AS id, d.group_code AS \"groupCode\", d.group_name AS \"groupName\", "
                + "d.subject_codes AS \"subjectCodes\", d.source_pages AS \"sourcePages\", d.status, d.confidence, d.created_at AS \"createdAt\", "
                + "doc.title, doc.academic_year AS \"academicYear\", coalesce(h.name, doc.proposed_school_name) AS \"schoolName\" "
                + "FROM admin.subject_group_drafts d JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id LEFT JOIN high_schools h ON h.code=doc.school_code "
                + "WHERE d.status = ANY(string_to_array(?, ',')) AND doc.deleted_at IS NULL AND doc.status <> 'DELETED' "
                + "AND (?::timestamptz IS NULL OR (d.created_at, d.id) < (?::timestamptz, ?::uuid)) "
                + "ORDER BY d.created_at DESC, d.id DESC LIMIT ?",
                (rs, rowNum) -> mapDraftRow(rs), status, createdAt, createdAt, id, limit);
    }

    public List<AdminDraftGroupDto> listDraftGroups(String status, String groupKeyAfter,
            String academicYearAfter, int limit) {
        return jdbc.query("WITH page_keys AS ("
                + "SELECT doc.school_group_key, doc.academic_year FROM admin.school_documents doc "
                + "WHERE doc.deleted_at IS NULL AND doc.status <> 'DELETED' "
                + "AND (?::text IS NULL OR (doc.school_group_key, doc.academic_year) > (?, ?)) "
                + "AND EXISTS (SELECT 1 FROM admin.school_document_versions ev "
                + "JOIN admin.subject_group_drafts ed ON ed.document_version_id=ev.id "
                + "WHERE ev.document_id=doc.id AND ed.status = ANY(string_to_array(?, ','))) "
                + "GROUP BY doc.school_group_key, doc.academic_year "
                + "ORDER BY doc.school_group_key, doc.academic_year LIMIT ?), "
                + "grouped AS (SELECT doc.school_group_key AS group_key, min(doc.school_code) AS school_code, "
                + "coalesce(min(regexp_replace(btrim(coalesce(h.name, doc.proposed_school_name)), '\\s+', ' ', 'g')), 'Trường chưa xác định') AS school_name, "
                + "min(regexp_replace(btrim(coalesce(h.location, doc.proposed_school_location)), '\\s+', ' ', 'g')) AS location, "
                + "doc.academic_year, count(d.id) AS draft_count, count(DISTINCT v.id) AS source_document_count "
                + "FROM page_keys p JOIN admin.school_documents doc ON doc.school_group_key=p.school_group_key "
                + "AND doc.academic_year=p.academic_year "
                + "JOIN admin.school_document_versions v ON v.document_id=doc.id "
                + "JOIN admin.subject_group_drafts d ON d.document_version_id=v.id "
                + "LEFT JOIN high_schools h ON h.code=doc.school_code "
                + "WHERE d.status = ANY(string_to_array(?, ',')) AND doc.deleted_at IS NULL AND doc.status <> 'DELETED' "
                + "GROUP BY doc.school_group_key, doc.academic_year) "
                + "SELECT group_key, school_name, school_code, location, academic_year, draft_count, source_document_count "
                + "FROM grouped ORDER BY group_key, academic_year",
                (rs, rowNum) -> new AdminDraftGroupDto(rs.getString("group_key"), rs.getString("school_name"),
                        rs.getString("school_code"), rs.getString("location"), rs.getString("academic_year"),
                        rs.getLong("draft_count"), rs.getLong("source_document_count")),
                groupKeyAfter, groupKeyAfter, academicYearAfter, status, limit, status);
    }

    public List<AdminSubjectGroupDraftDto> listDraftsForGroup(String groupKey, String academicYear,
            String status, String createdAt, String id, int limit) {
        return jdbc.query("SELECT d.id::text AS id, doc.id::text AS document_id, v.id::text AS document_version_id, "
                + "v.version_number AS document_version_number, doc.title AS document_title, "
                + "v.original_filename AS document_filename, coalesce(h.name, doc.proposed_school_name, 'Trường chưa xác định') AS school_name, "
                + "doc.school_code, doc.academic_year, doc.status AS document_status, d.group_code, d.group_name, "
                + "d.subject_codes AS subject_codes, d.source_pages, d.status AS draft_status, d.confidence, d.created_at "
                + "FROM admin.subject_group_drafts d "
                + "JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id "
                + "LEFT JOIN high_schools h ON h.code=doc.school_code "
                + "WHERE doc.school_group_key=? AND doc.academic_year=? "
                + "AND d.status = ANY(string_to_array(?, ',')) AND doc.deleted_at IS NULL AND doc.status <> 'DELETED' "
                + "AND (?::timestamptz IS NULL OR (d.created_at, d.id) < (?::timestamptz, ?::uuid)) "
                + "ORDER BY d.created_at DESC, d.id DESC LIMIT ?",
                (rs, rowNum) -> new AdminSubjectGroupDraftDto(rs.getString("id"), rs.getString("document_id"),
                        rs.getString("document_version_id"), rs.getInt("document_version_number"),
                        rs.getString("document_title"), rs.getString("document_filename"), rs.getString("school_name"),
                        rs.getString("school_code"), rs.getString("academic_year"), rs.getString("document_status"),
                        rs.getString("group_code"), rs.getString("group_name"),
                        readSubjectCodes(rs.getString("subject_codes")), readSourcePages(rs.getArray("source_pages")),
                        rs.getString("draft_status"), rs.getBigDecimal("confidence"), rs.getObject("created_at").toString()),
                groupKey, academicYear, status, createdAt, createdAt, id, limit);
    }

    static Map<String, Object> mapDraftRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("groupCode", rs.getString("groupCode"));
        row.put("groupName", rs.getString("groupName"));
        row.put("subjectCodes", readSubjectCodes(rs.getString("subjectCodes")));
        row.put("sourcePages", readSourcePages(rs.getArray("sourcePages")));
        row.put("status", rs.getString("status"));
        row.put("confidence", rs.getObject("confidence"));
        row.put("createdAt", rs.getObject("createdAt"));
        row.put("title", rs.getString("title"));
        row.put("academicYear", rs.getString("academicYear"));
        row.put("schoolName", rs.getString("schoolName"));
        return row;
    }

    private static List<String> readSubjectCodes(String json) throws SQLException {
        final JsonNode values;
        try {
            values = JSON_MAPPER.readTree(json);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SQLException("Invalid subject_codes JSON stored for subject group draft", exception);
        }
        if (values == null || !values.isArray()) {
            throw new SQLException("subject_codes must be a JSON array");
        }

        List<String> result = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            if (!value.isTextual()) {
                throw new SQLException("subject_codes must contain only strings");
            }
            result.add(value.textValue());
        }
        return result;
    }

    private static List<Integer> readSourcePages(Array sourcePages) throws SQLException {
        if (sourcePages == null) {
            throw new SQLException("source_pages cannot be null");
        }
        try {
            Object rawValues = sourcePages.getArray();
            if (!(rawValues instanceof Object[] values)) {
                throw new SQLException("source_pages must be an integer array");
            }

            List<Integer> result = new ArrayList<>(values.length);
            for (Object value : values) {
                if (!(value instanceof Number number)) {
                    throw new SQLException("source_pages must contain only integers");
                }
                result.add(number.intValue());
            }
            return result;
        } finally {
            sourcePages.free();
        }
    }

    public int rejectDraft(String draftId, String actorId, String note) {
        return jdbc.update("UPDATE admin.subject_group_drafts SET status='REJECTED', reviewed_by=?::uuid, reviewed_at=now(), review_note=? "
                + "WHERE id=?::uuid AND status='SUBMITTED'", actorId, note, draftId);
    }

    public void auditRejected(String draftId, String actorId, String note) {
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, draft_id, action, details) VALUES (?::uuid, ?::uuid, 'SUBJECT_GROUP_REJECTED', jsonb_build_object('note', ?))",
                actorId, draftId, note == null ? "" : note);
    }

    public int editDraft(String draftId, String groupCode, String groupName, String subjectCodesJson) {
        return jdbc.update("UPDATE admin.subject_group_drafts SET group_code=?, group_name=?, subject_codes=?::jsonb "
                + "WHERE id=?::uuid AND status IN ('DRAFT','SUBMITTED')", groupCode, groupName, subjectCodesJson, draftId);
    }

    public int submitDraft(String draftId) {
        return jdbc.update("UPDATE admin.subject_group_drafts SET status='SUBMITTED' WHERE id=?::uuid AND status='DRAFT'", draftId);
    }

    public void auditSubmitted(String draftId, String actorId) {
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, draft_id, action) VALUES (?::uuid, ?::uuid, 'SUBJECT_GROUP_SUBMITTED')", actorId, draftId);
    }

    public String latestObjectKey(String documentId) {
        return jdbc.queryForObject("SELECT v.object_key FROM admin.school_document_versions v JOIN admin.school_documents d ON d.id=v.document_id "
                + "WHERE d.id=?::uuid AND d.deleted_at IS NULL ORDER BY v.version_number DESC LIMIT 1", String.class, documentId);
    }

    public int retryLatestFailedJob(String documentId) {
        int changed = jdbc.update("UPDATE admin.extraction_log j SET status='PENDING', attempt_count=0, available_at=now(), "
                + "locked_at=NULL, error_code=NULL, updated_at=now() WHERE j.id=(SELECT ej.id FROM admin.school_documents d "
                + "JOIN admin.school_document_versions v ON v.document_id=d.id "
                + "JOIN admin.extraction_log ej ON ej.document_version_id=v.id "
                + "WHERE d.id=?::uuid AND d.deleted_at IS NULL AND d.status='FAILED' "
                + "ORDER BY v.version_number DESC, ej.created_at DESC LIMIT 1) AND j.status='FAILED'", documentId);
        if (changed == 1)
            jdbc.update("UPDATE admin.school_documents SET status='UPLOADED',updated_at=now() WHERE id=?::uuid", documentId);
        return changed;
    }

    public int requestDelete(String id, String actorId) {
        return jdbc.update("UPDATE admin.school_documents SET status='DELETE_REQUESTED', deletion_requested_by=?::uuid, updated_at=now() "
                + "WHERE id=?::uuid AND deleted_at IS NULL AND status <> 'DELETE_REQUESTED'", actorId, id);
    }

    public int approveDelete(String id, String actorId) {
        return jdbc.update("UPDATE admin.school_documents SET status='DELETED', deleted_by=?::uuid, deleted_at=now(), updated_at=now() "
                + "WHERE id=?::uuid AND status='DELETE_REQUESTED'", actorId, id);
    }

    public int softDeleteDocument(String id, String actorId) {
        return jdbc.update("UPDATE admin.school_documents SET status='DELETED', deleted_by=?::uuid, deleted_at=now(), updated_at=now() "
                + "WHERE id=?::uuid AND deleted_at IS NULL AND status <> 'DELETED'", actorId, id);
    }

    public int cancelActiveJobs(String documentId) {
        return jdbc.update("UPDATE admin.extraction_log j SET status='CANCELLED', locked_at=NULL, updated_at=now() "
                + "FROM admin.school_document_versions v WHERE v.id=j.document_version_id AND v.document_id=?::uuid "
                + "AND j.status IN ('PENDING','RUNNING','RETRY')", documentId);
    }

    public int deleteUnpublishedDraftsForDocument(String documentId, String actorId) {
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, document_id, draft_id, action, details) "
                + "SELECT ?::uuid, v.document_id, d.id, 'SUBJECT_GROUP_DRAFT_DELETED', "
                + "jsonb_build_object('draftId', d.id, 'documentVersionId', v.id, 'groupCode', d.group_code, "
                + "'groupName', d.group_name, 'subjectCodes', d.subject_codes, 'sourcePages', to_jsonb(d.source_pages), "
                + "'confidence', d.confidence, 'status', d.status, 'academicYear', doc.academic_year) "
                + "FROM admin.subject_group_drafts d JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id "
                + "WHERE v.document_id=?::uuid AND d.status <> 'PUBLISHED'", actorId, documentId);
        jdbc.update("UPDATE admin.admin_approval_audit SET details=details || jsonb_build_object('deletedDraftId', draft_id), draft_id=NULL "
                + "WHERE draft_id IN (SELECT d.id FROM admin.subject_group_drafts d "
                + "JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "WHERE v.document_id=?::uuid AND d.status <> 'PUBLISHED')", documentId);
        return jdbc.update("DELETE FROM admin.subject_group_drafts d USING admin.school_document_versions v "
                + "WHERE v.id=d.document_version_id AND v.document_id=?::uuid AND d.status <> 'PUBLISHED'", documentId);
    }

    public int hardDeleteDraft(String draftId, String actorId) {
        List<String> documents = jdbc.query("SELECT doc.id::text FROM admin.subject_group_drafts d "
                + "JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id WHERE d.id=?::uuid FOR UPDATE OF doc",
                (rs, rowNum) -> rs.getString(1), draftId);
        if (documents.isEmpty()) return 0;
        List<Map<String, Object>> lockedDrafts = jdbc.queryForList("SELECT d.status AS draft_status, "
                + "EXISTS (SELECT 1 FROM admin.extraction_log j WHERE j.document_version_id=v.id "
                + "AND j.status IN ('PENDING','RUNNING','RETRY')) AS processing "
                + "FROM admin.subject_group_drafts d JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "WHERE d.id=?::uuid FOR UPDATE OF d", draftId);
        if (lockedDrafts.isEmpty()) return 0;
        Map<String, Object> draft = lockedDrafts.get(0);
        if (Boolean.TRUE.equals(draft.get("processing"))) return -1;
        if ("PUBLISHED".equals(draft.get("draft_status"))) return 0;
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, document_id, draft_id, action, details) "
                + "SELECT ?::uuid, doc.id, d.id, 'SUBJECT_GROUP_DRAFT_DELETED', "
                + "jsonb_build_object('draftId', d.id, 'documentVersionId', v.id, 'groupCode', d.group_code, "
                + "'groupName', d.group_name, 'subjectCodes', d.subject_codes, 'sourcePages', to_jsonb(d.source_pages), "
                + "'confidence', d.confidence, 'status', d.status, 'academicYear', doc.academic_year) "
                + "FROM admin.subject_group_drafts d JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id WHERE d.id=?::uuid AND d.status <> 'PUBLISHED'",
                actorId, draftId);
        jdbc.update("UPDATE admin.admin_approval_audit SET details=details || jsonb_build_object('deletedDraftId', draft_id), draft_id=NULL "
                + "WHERE draft_id=?::uuid", draftId);
        return jdbc.update("DELETE FROM admin.subject_group_drafts WHERE id=?::uuid AND status <> 'PUBLISHED'", draftId);
    }

    public void auditDocumentSoftDeleted(String id, String actorId, String action) {
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, document_id, action) VALUES (?::uuid, ?::uuid, ?)",
                actorId, id, action);
    }

    public boolean lockDraftDocumentForReview(String draftId) {
        return !jdbc.queryForList("SELECT doc.id FROM admin.subject_group_drafts d "
                + "JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id "
                + "WHERE d.id=?::uuid AND doc.status NOT IN ('DELETE_REQUESTED','DELETED') FOR UPDATE OF doc", draftId).isEmpty();
    }

    public void auditDeleteApproved(String id, String actorId) {
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, document_id, action) VALUES (?::uuid, ?::uuid, 'DOCUMENT_DELETE_APPROVED')", actorId, id);
    }

    public List<DraftRow> lockDraftsForPublication(String groupKey, String academicYear) {
        return jdbc.query("SELECT d.id::text AS draft_id, doc.id::text AS document_id, d.group_code, d.group_name, d.subject_codes::text, "
                + "doc.school_code, doc.proposed_school_name, doc.proposed_school_location, doc.proposed_school_code, doc.academic_year, d.status "
                + "FROM admin.subject_group_drafts d JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id WHERE doc.school_group_key=? AND doc.academic_year=? "
                + "AND d.status IN ('DRAFT','SUBMITTED') AND doc.deleted_at IS NULL "
                + "AND doc.status NOT IN ('DELETE_REQUESTED','DELETED') ORDER BY d.created_at DESC, d.id DESC FOR UPDATE OF doc, d",
                (rs, row) -> new DraftRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getString(11)), groupKey, academicYear);
    }

    public int lockDocumentsForPublication(String groupKey, String academicYear) {
        return jdbc.query("SELECT id FROM admin.school_documents WHERE school_group_key=? AND academic_year=? "
                + "AND deleted_at IS NULL AND status NOT IN ('DELETE_REQUESTED','DELETED') ORDER BY id FOR UPDATE",
                (rs, row) -> rs.getString(1), groupKey, academicYear).size();
    }

    public boolean hasActiveExtractionForGroup(String groupKey, String academicYear) {
        return !jdbc.queryForList("SELECT j.id FROM admin.extraction_log j "
                + "JOIN admin.school_document_versions v ON v.id=j.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id "
                + "WHERE doc.school_group_key=? AND doc.academic_year=? AND doc.deleted_at IS NULL "
                + "AND j.status IN ('PENDING','RUNNING','RETRY') LIMIT 1", groupKey, academicYear).isEmpty();
    }

    public Set<String> publishedGroupCodes(String schoolCode, String academicYear) {
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT upper(group_code) FROM subject_groups "
                + "WHERE school_code=? AND academic_year=?", String.class, schoolCode, academicYear));
    }

    public boolean subjectExists(String code) {
        return !jdbc.queryForList("SELECT code FROM subjects WHERE code=?", String.class, code).isEmpty();
    }

    public boolean schoolCodeExists(String schoolCode) {
        return !jdbc.queryForList("SELECT code FROM high_schools WHERE code=?", String.class, schoolCode).isEmpty();
    }

    public Map<String, Object> findSchoolByCode(String schoolCode) {
        List<Map<String, Object>> schools = jdbc.queryForList(
                "SELECT code, name, location FROM high_schools WHERE code=?", schoolCode);
        return schools.isEmpty() ? null : schools.get(0);
    }

    public Map<String, Object> findSchoolByNameAndLocation(String name, String location) {
        List<Map<String, Object>> schools = jdbc.queryForList(
                "SELECT code, name, location FROM high_schools "
                        + "WHERE lower(regexp_replace(btrim(coalesce(name,'')), '\\s+', ' ', 'g')) = "
                        + "lower(regexp_replace(btrim(?), '\\s+', ' ', 'g')) "
                        + "AND lower(regexp_replace(btrim(coalesce(location,'')), '\\s+', ' ', 'g')) = "
                        + "lower(regexp_replace(btrim(?), '\\s+', ' ', 'g')) "
                        + "ORDER BY code LIMIT 1",
                name, location);
        return schools.isEmpty() ? null : schools.get(0);
    }

    public boolean proposedSchoolCodeConflicts(String schoolCode, String name, String location, String excludedDocumentId) {
        return !jdbc.queryForList("SELECT id FROM admin.school_documents WHERE school_code IS NULL AND deleted_at IS NULL "
                + "AND status NOT IN ('DELETED','DELETE_REQUESTED') AND proposed_school_code=? "
                + "AND (?::uuid IS NULL OR id<>?::uuid) "
                + "AND (lower(regexp_replace(btrim(coalesce(proposed_school_name,'')), '\\s+', ' ', 'g')) <> "
                + "lower(regexp_replace(btrim(?), '\\s+', ' ', 'g')) OR "
                + "lower(regexp_replace(btrim(coalesce(proposed_school_location,'')), '\\s+', ' ', 'g')) <> "
                + "lower(regexp_replace(btrim(?), '\\s+', ' ', 'g'))) LIMIT 1",
                schoolCode, excludedDocumentId, excludedDocumentId, name, location).isEmpty();
    }

    public boolean createSchoolIfAbsent(String schoolCode, String name, String location) {
        jdbc.update("INSERT INTO high_schools(code, name, location) VALUES (?, ?, ?) ON CONFLICT(code) DO NOTHING",
                schoolCode, name, location);
        Map<String, Object> stored = findSchoolByCode(schoolCode);
        return stored != null && normalizeSchoolName((String) stored.get("name")).equals(normalizeSchoolName(name))
                && normalizeLocation((String) stored.get("location")).equals(normalizeLocation(location));
    }

    public void addSubjectGroup(String schoolCode, String groupCode, String groupName, String subjectCode, String year) {
        jdbc.update("INSERT INTO subject_groups(school_code, group_code, group_name, subject_code, academic_year) "
                + "VALUES (?, ?, ?, ?, ?)", schoolCode, groupCode, groupName, subjectCode, year);
    }

    public void publishDraft(String draftId, String actorId, String documentId, String schoolCode,
            String groupCode, String batchId) {
        int changed = jdbc.update("UPDATE admin.subject_group_drafts SET status='PUBLISHED', reviewed_by=?::uuid, reviewed_at=now() "
                + "WHERE id=?::uuid AND status IN ('DRAFT','SUBMITTED')", actorId, draftId);
        if (changed != 1) throw new IllegalStateException("Bản nháp đã thay đổi trong khi công bố");
        jdbc.update("UPDATE admin.school_documents SET school_code=?, status='PUBLISHED', updated_at=now() WHERE id=?::uuid",
                schoolCode, documentId);
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, document_id, draft_id, action, details) "
                + "VALUES (?::uuid, ?::uuid, ?::uuid, 'SUBJECT_GROUP_PUBLISHED', jsonb_build_object('schoolCode', ?, 'groupCode', ?, 'batchId', ?))",
                actorId, documentId, draftId, schoolCode, groupCode, batchId);
    }

    public void createGraphSyncJob(String jobId, String schoolCode, String academicYear, List<String> groupCodes) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("INSERT INTO admin.subject_group_graph_sync_jobs "
                    + "(id, school_code, academic_year, group_codes) VALUES (?::uuid, ?, ?, ?)")) {
                statement.setString(1, jobId);
                statement.setString(2, schoolCode);
                statement.setString(3, academicYear);
                statement.setArray(4, connection.createArrayOf("text", groupCodes.toArray()));
                statement.executeUpdate();
                return null;
            }
        });
    }

    private static String normalizeSchoolName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeLocation(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
