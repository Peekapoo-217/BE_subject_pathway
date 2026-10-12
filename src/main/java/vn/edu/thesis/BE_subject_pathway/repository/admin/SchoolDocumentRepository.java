package vn.edu.thesis.BE_subject_pathway.repository.admin;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class SchoolDocumentRepository {
    public record DraftRow(String id, String groupCode, String groupName, String subjectCodes,
            String schoolCode, String proposedSchoolName, String proposedSchoolLocation, String academicYear) {}

    private final JdbcTemplate jdbc;
    public SchoolDocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> createDocument(String documentId, String versionId, String userId,
            String schoolCode, String proposedName, String proposedLocation, String title, String year,
            String objectKey, String filename, long bytes, String sha) {
        jdbc.update("INSERT INTO admin.school_documents(id, school_code, proposed_school_name, proposed_school_location, title, academic_year, created_by) "
                + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?::uuid)", documentId, schoolCode, proposedName, proposedLocation, title, year, userId);
        jdbc.update("INSERT INTO admin.school_document_versions(id, document_id, version_number, object_key, original_filename, content_type, byte_size, sha256, uploaded_by) "
                + "VALUES (?::uuid, ?::uuid, 1, ?, ?, 'application/pdf', ?, ?, ?::uuid)",
                versionId, documentId, objectKey, filename, bytes, sha, userId);
        queueExtraction(versionId);
        return jdbc.queryForMap("SELECT id::text AS id, title, academic_year AS \"academicYear\", status, created_at AS \"createdAt\" FROM admin.school_documents WHERE id=?::uuid", documentId);
    }

    public int lockAndNextVersion(String documentId) {
        jdbc.queryForObject("SELECT id::text FROM admin.school_documents WHERE id=?::uuid AND deleted_at IS NULL FOR UPDATE", String.class, documentId);
        return jdbc.queryForObject("SELECT coalesce(max(version_number),0)+1 FROM admin.school_document_versions WHERE document_id=?::uuid", Integer.class, documentId);
    }

    public void addVersion(String versionId, String documentId, int version, String objectKey,
            String filename, long bytes, String sha, String userId) {
        jdbc.update("INSERT INTO admin.school_document_versions(id,document_id,version_number,object_key,original_filename,content_type,byte_size,sha256,uploaded_by) "
                + "VALUES (?::uuid,?::uuid,?,?,?,'application/pdf',?,?,?::uuid)",
                versionId, documentId, version, objectKey, filename, bytes, sha, userId);
        queueExtraction(versionId);
        jdbc.update("UPDATE admin.school_documents SET status='UPLOADED',updated_at=now() WHERE id=?::uuid", documentId);
    }

    private void queueExtraction(String versionId) {
        jdbc.update("INSERT INTO admin.extraction_jobs(document_version_id) VALUES (?::uuid)", versionId);
    }

    public int updateMetadata(String id, String title, String academicYear) {
        return jdbc.update("UPDATE admin.school_documents SET title=?,academic_year=?,updated_at=now() "
                + "WHERE id=?::uuid AND deleted_at IS NULL AND status NOT IN ('PUBLISHED','DELETED','DELETE_REQUESTED')",
                title, academicYear, id);
    }

    public List<Map<String, Object>> list(String status, String createdAt, String id, int limit) {
        return jdbc.queryForList("SELECT d.id::text AS id, d.title, d.academic_year AS \"academicYear\", d.status, d.created_at AS \"createdAt\", "
                + "coalesce(h.name, d.proposed_school_name) AS \"schoolName\", v.original_filename AS \"filename\" "
                + "FROM admin.school_documents d LEFT JOIN high_schools h ON h.code=d.school_code "
                + "JOIN LATERAL (SELECT original_filename FROM admin.school_document_versions WHERE document_id=d.id ORDER BY version_number DESC LIMIT 1) v ON true "
                + "WHERE d.deleted_at IS NULL AND (?::text IS NULL OR d.status=?) AND (?::timestamptz IS NULL OR (d.created_at, d.id) < (?::timestamptz, ?::uuid)) "
                + "ORDER BY d.created_at DESC, d.id DESC LIMIT ?",
                status, status, createdAt, createdAt, id, limit);
    }

    public List<Map<String, Object>> listDrafts(String status, String createdAt, String id, int limit) {
        return jdbc.queryForList("SELECT d.id::text AS id, d.group_code AS \"groupCode\", d.group_name AS \"groupName\", "
                + "d.subject_codes AS \"subjectCodes\", d.source_pages AS \"sourcePages\", d.confidence, d.created_at AS \"createdAt\", "
                + "doc.title, doc.academic_year AS \"academicYear\", coalesce(h.name, doc.proposed_school_name) AS \"schoolName\" "
                + "FROM admin.subject_group_drafts d JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id LEFT JOIN high_schools h ON h.code=doc.school_code "
                + "WHERE d.status=? AND (?::timestamptz IS NULL OR (d.created_at, d.id) < (?::timestamptz, ?::uuid)) "
                + "ORDER BY d.created_at DESC, d.id DESC LIMIT ?",
                status, createdAt, createdAt, id, limit);
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

    public int requestDelete(String id, String actorId) {
        return jdbc.update("UPDATE admin.school_documents SET status='DELETE_REQUESTED', deletion_requested_by=?::uuid, updated_at=now() "
                + "WHERE id=?::uuid AND deleted_at IS NULL AND status <> 'DELETE_REQUESTED'", actorId, id);
    }

    public int approveDelete(String id, String actorId) {
        return jdbc.update("UPDATE admin.school_documents SET status='DELETED', deleted_by=?::uuid, deleted_at=now(), updated_at=now() "
                + "WHERE id=?::uuid AND status='DELETE_REQUESTED'", actorId, id);
    }

    public void auditDeleteApproved(String id, String actorId) {
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, document_id, action) VALUES (?::uuid, ?::uuid, 'DOCUMENT_DELETE_APPROVED')", actorId, id);
    }

    public List<DraftRow> lockSubmittedDraft(String draftId) {
        return jdbc.query("SELECT d.id::text AS draft_id, d.group_code, d.group_name, d.subject_codes::text, "
                + "doc.school_code, doc.proposed_school_name, doc.proposed_school_location, doc.academic_year "
                + "FROM admin.subject_group_drafts d JOIN admin.school_document_versions v ON v.id=d.document_version_id "
                + "JOIN admin.school_documents doc ON doc.id=v.document_id WHERE d.id=?::uuid AND d.status='SUBMITTED' FOR UPDATE OF d",
                (rs, row) -> new DraftRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)), draftId);
    }

    public boolean subjectExists(String code) {
        return !jdbc.queryForList("SELECT code FROM subjects WHERE code=?", String.class, code).isEmpty();
    }

    public void createSchoolIfAbsent(String schoolCode, String name, String location) {
        jdbc.update("INSERT INTO high_schools(code, name, location) VALUES (?, ?, ?) ON CONFLICT(code) DO NOTHING", schoolCode, name, location);
    }

    public void addSubjectGroup(String schoolCode, String groupCode, String groupName, String subjectCode, String year) {
        jdbc.update("INSERT INTO subject_groups(school_code, group_code, group_name, subject_code, academic_year) "
                + "VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING", schoolCode, groupCode, groupName, subjectCode, year);
    }

    public void publishDraft(String draftId, String actorId, String schoolCode, String groupCode) {
        jdbc.update("UPDATE admin.subject_group_drafts SET status='PUBLISHED', reviewed_by=?::uuid, reviewed_at=now() WHERE id=?::uuid", actorId, draftId);
        jdbc.update("UPDATE admin.school_documents SET school_code=?, status='PUBLISHED', updated_at=now() WHERE id=(SELECT document_id FROM admin.school_document_versions WHERE id=(SELECT document_version_id FROM admin.subject_group_drafts WHERE id=?::uuid))", schoolCode, draftId);
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, draft_id, action, details) VALUES (?::uuid, ?::uuid, 'SUBJECT_GROUP_PUBLISHED', jsonb_build_object('schoolCode', ?, 'groupCode', ?))",
                actorId, draftId, schoolCode, groupCode);
    }
}
