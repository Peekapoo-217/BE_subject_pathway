package vn.edu.thesis.BE_subject_pathway.repository.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import vn.edu.thesis.BE_subject_pathway.dto.response.AdminHighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.AdminDeletedHighSchoolDto;

@Repository
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminHighSchoolRepository {
    private final JdbcTemplate jdbc;

    public AdminHighSchoolRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminHighSchoolDto> listActive(String afterCode, int limit) {
        return jdbc.query("SELECT h.code, h.name, h.location, h.created_at, h.updated_at, "
                + "coalesce(j.status, 'SYNCED') AS graph_sync_status, j.id::text AS graph_sync_job_id "
                + "FROM public.high_schools h LEFT JOIN LATERAL (SELECT id, status FROM admin.high_school_graph_sync_jobs "
                + "WHERE school_code=h.code ORDER BY created_at DESC, id DESC LIMIT 1) j ON true "
                + "WHERE h.deleted_at IS NULL AND (?::varchar IS NULL OR h.code > ?) ORDER BY h.code LIMIT ?",
                this::map, afterCode, afterCode, limit);
    }

    public List<AdminDeletedHighSchoolDto> listDeleted(String afterCode, int limit) {
        return jdbc.query("SELECT h.code, h.name, h.location, h.deleted_at, coalesce(j.status, 'SYNCED') AS graph_sync_status, "
                + "j.id::text AS graph_sync_job_id FROM public.high_schools h "
                + "LEFT JOIN LATERAL (SELECT id, status FROM admin.high_school_graph_sync_jobs "
                + "WHERE school_code=h.code ORDER BY created_at DESC, id DESC LIMIT 1) j ON true "
                + "WHERE h.deleted_at IS NOT NULL AND (?::varchar IS NULL OR h.code > ?) ORDER BY h.code LIMIT ?",
                (rs, rowNum) -> new AdminDeletedHighSchoolDto(rs.getString("code"), rs.getString("name"),
                        rs.getString("location"), rs.getTimestamp("deleted_at").toInstant(),
                        rs.getString("graph_sync_status"), rs.getString("graph_sync_job_id")),
                afterCode, afterCode, limit);
    }

    public Optional<AdminHighSchoolDto> findActive(String code) {
        return jdbc.query("SELECT h.code, h.name, h.location, h.created_at, h.updated_at, "
                + "coalesce(j.status, 'SYNCED') AS graph_sync_status, j.id::text AS graph_sync_job_id "
                + "FROM public.high_schools h LEFT JOIN LATERAL (SELECT id, status FROM admin.high_school_graph_sync_jobs "
                + "WHERE school_code=h.code ORDER BY created_at DESC, id DESC LIMIT 1) j ON true "
                + "WHERE h.code=? AND h.deleted_at IS NULL",
                this::map, code).stream().findFirst();
    }

    public Map<String, Object> lockForRestore(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT code, deleted_at AS \"deletedAt\", "
                + "deleted_by::text AS \"deletedBy\" FROM public.high_schools WHERE code=? FOR UPDATE", code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int restore(String code, Object deletedAt, String deletedBy) {
        jdbc.update("UPDATE public.subject_groups SET deleted_at=NULL, deleted_by=NULL "
                + "WHERE school_code=? AND deleted_at=? AND deleted_by IS NOT DISTINCT FROM ?::uuid",
                code, deletedAt, deletedBy);
        return jdbc.update("UPDATE public.high_schools SET deleted_at=NULL, deleted_by=NULL, updated_at=now() "
                + "WHERE code=? AND deleted_at=? AND deleted_by IS NOT DISTINCT FROM ?::uuid",
                code, deletedAt, deletedBy);
    }

    public String enqueueRestoreGraph(String code) {
        return jdbc.queryForObject("INSERT INTO admin.high_school_graph_sync_jobs(school_code, operation) "
                + "VALUES (?, 'UPSERT') RETURNING id::text", String.class, code);
    }

    public void auditRestored(String actorId, String code, String graphJobId) {
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id, action, details) "
                + "VALUES (?::uuid, 'HIGH_SCHOOL_RESTORED', jsonb_build_object('schoolCode', ?, 'graphSyncJobId', ?))",
                actorId, code, graphJobId);
    }

    public Map<String, Object> restoreReplay(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.details->>'graphSyncJobId' AS \"graphSyncJobId\", "
                + "coalesce(j.status, 'SYNCED') AS \"graphSyncStatus\" FROM admin.admin_approval_audit a "
                + "LEFT JOIN admin.high_school_graph_sync_jobs j ON j.id=nullif(a.details->>'graphSyncJobId','')::uuid "
                + "WHERE a.action='HIGH_SCHOOL_RESTORED' AND a.details->>'schoolCode'=? "
                + "ORDER BY a.created_at DESC, a.id DESC LIMIT 1", code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> graphSyncJob(String code, String jobId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT school_code AS \"schoolCode\", "
                + "id::text AS \"graphSyncJobId\", status AS \"graphSyncStatus\" "
                + "FROM admin.high_school_graph_sync_jobs WHERE school_code=? AND id=?::uuid", code, jobId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> graphSyncJobById(String code, String jobId) {
        return graphSyncJob(code, jobId);
    }

    public boolean codeInUse(String code) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM public.high_schools WHERE code=? "
                + "UNION ALL SELECT 1 FROM admin.high_school_code_history WHERE previous_code=? "
                + "UNION ALL SELECT 1 FROM admin.school_documents WHERE deleted_at IS NULL AND proposed_school_code=?)",
                Boolean.class, code, code, code);
    }

    public boolean graphSourceCodeConflict(String currentCode, String newCode) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM admin.document_subject_group_sources old "
                + "JOIN admin.document_subject_group_sources current ON current.school_code=? "
                + "AND current.academic_year=old.academic_year AND current.group_code=old.group_code "
                + "AND current.source_kind=old.source_kind AND current.id<>old.id "
                + "WHERE old.school_code=? AND ((old.source_kind='UNATTRIBUTED') OR "
                + "(old.source_kind='DOCUMENT' AND current.document_id=old.document_id)))",
                Boolean.class, newCode, currentCode);
    }

    public void lockCode(String code) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {
        }, code);
    }

    public void lockIdentity(String name, String location) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(? || chr(31) || ?, 0))", rs -> {
        },
                name.toLowerCase(java.util.Locale.ROOT), location.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean proposedIdentityExists(String name, String location, String excludedCode) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM public.high_schools WHERE deleted_at IS NULL "
                + "AND (?::varchar IS NULL OR code<>?) AND lower(regexp_replace(btrim(name), '\\s+', ' ', 'g'))="
                + "lower(regexp_replace(btrim(?), '\\s+', ' ', 'g')) "
                + "AND lower(regexp_replace(btrim(coalesce(location,'')), '\\s+', ' ', 'g'))="
                + "lower(regexp_replace(btrim(?), '\\s+', ' ', 'g'))) ",
                Boolean.class, excludedCode, excludedCode, name, location);
    }

    public void create(String code, String name, String location, String actorId) {
        jdbc.update("INSERT INTO public.high_schools(code,name,location) VALUES (?,?,?)", code, name, location);
        audit(actorId, "HIGH_SCHOOL_CREATED", code, null, name, location);
        enqueueGraph(code, null, "UPSERT");
    }

    public int update(String currentCode, String newCode, String name, String location, String actorId) {
        if (jdbc.queryForList("SELECT code FROM public.high_schools WHERE code=? AND deleted_at IS NULL FOR UPDATE",
                String.class, currentCode).isEmpty())
            return 0;
        if (!currentCode.equals(newCode)) {
            jdbc.update("INSERT INTO admin.high_school_code_history(previous_code,school_code,changed_by) "
                    + "VALUES (?,?,?::uuid)", currentCode, currentCode, actorId);
        }
        int changed = jdbc.update("UPDATE public.high_schools SET code=?, name=?, location=?, updated_at=now() "
                + "WHERE code=? AND deleted_at IS NULL", newCode, name, location, currentCode);
        if (changed == 1) {
            if (!currentCode.equals(newCode)) {
                jdbc.update("UPDATE admin.document_subject_group_sources SET school_code=? WHERE school_code=?",
                        newCode, currentCode);
            }
            audit(actorId, "HIGH_SCHOOL_UPDATED", newCode, currentCode, name, location);
            enqueueGraph(newCode, currentCode.equals(newCode) ? null : currentCode, "UPSERT");
        }
        return changed;
    }

    public int softDelete(String code, String actorId) {
        int changed = jdbc
                .update("UPDATE public.high_schools SET deleted_at=now(), deleted_by=?::uuid, updated_at=now() "
                        + "WHERE code=? AND deleted_at IS NULL", actorId, code);
        if (changed == 1) {
            jdbc.update("UPDATE public.subject_groups SET deleted_at=now(), deleted_by=?::uuid "
                    + "WHERE school_code=? AND deleted_at IS NULL", actorId, code);
            jdbc.update(
                    "UPDATE admin.school_documents doc SET status_before_delete=coalesce(nullif(doc.status_before_delete,'DELETE_REQUESTED'), "
                            + "CASE WHEN doc.status='DELETE_REQUESTED' THEN 'UPLOADED' ELSE doc.status END), "
                            + "status='DELETED', deleted_by=?::uuid, deleted_at=now(), updated_at=now() "
                            + "WHERE doc.deleted_at IS NULL AND (doc.school_code=? OR (doc.proposed_school_code=? "
                            + "AND lower(regexp_replace(btrim(coalesce(proposed_school_name,'')), '\\s+', ' ', 'g'))="
                            + "(SELECT lower(regexp_replace(btrim(name), '\\s+', ' ', 'g')) FROM public.high_schools WHERE code=?) "
                            + "AND lower(regexp_replace(btrim(coalesce(proposed_school_location,'')), '\\s+', ' ', 'g'))="
                            + "(SELECT lower(regexp_replace(btrim(coalesce(location,'')), '\\s+', ' ', 'g')) FROM public.high_schools WHERE code=?)))",
                    actorId, code, code, code, code);
            jdbc.update("UPDATE admin.subject_group_drafts d SET deleted_at=now(), deleted_by=?::uuid "
                    + "FROM admin.school_document_versions v JOIN admin.school_documents doc ON doc.id=v.document_id "
                    + "WHERE d.document_version_id=v.id AND doc.deleted_at IS NOT NULL AND doc.deleted_by=?::uuid "
                    + "AND d.deleted_at IS NULL AND (doc.school_code=? OR (doc.proposed_school_code=? "
                    + "AND lower(regexp_replace(btrim(coalesce(doc.proposed_school_name,'')), '\\s+', ' ', 'g'))="
                    + "(SELECT lower(regexp_replace(btrim(name), '\\s+', ' ', 'g')) FROM public.high_schools WHERE code=?) "
                    + "AND lower(regexp_replace(btrim(coalesce(doc.proposed_school_location,'')), '\\s+', ' ', 'g'))="
                    + "(SELECT lower(regexp_replace(btrim(coalesce(location,'')), '\\s+', ' ', 'g')) FROM public.high_schools WHERE code=?)))",
                    actorId, actorId, code, code, code, code);
            jdbc.update("UPDATE admin.extraction_log j SET status='CANCELLED', locked_at=NULL, updated_at=now() "
                    + "FROM admin.school_document_versions v JOIN admin.school_documents doc ON doc.id=v.document_id "
                    + "WHERE v.id=j.document_version_id AND doc.deleted_by=?::uuid AND doc.deleted_at IS NOT NULL "
                    + "AND j.status IN ('PENDING','RUNNING','RETRY') AND (doc.school_code=? OR (doc.proposed_school_code=? "
                    + "AND lower(regexp_replace(btrim(coalesce(doc.proposed_school_name,'')), '\\s+', ' ', 'g'))="
                    + "(SELECT lower(regexp_replace(btrim(name), '\\s+', ' ', 'g')) FROM public.high_schools WHERE code=?) "
                    + "AND lower(regexp_replace(btrim(coalesce(doc.proposed_school_location,'')), '\\s+', ' ', 'g'))="
                    + "(SELECT lower(regexp_replace(btrim(coalesce(location,'')), '\\s+', ' ', 'g')) FROM public.high_schools WHERE code=?)))",
                    actorId, code,
                    code, code, code);
            jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id,action,details) "
                    + "VALUES (?::uuid,'HIGH_SCHOOL_SOFT_DELETED',jsonb_build_object('schoolCode',?))", actorId, code);
            enqueueGraph(code, null, "DELETE");
        }
        return changed;
    }

    private void audit(String actorId, String action, String code, String previousCode, String name, String location) {
        jdbc.update("INSERT INTO admin.admin_approval_audit(actor_id,action,details) "
                + "VALUES (?::uuid,?,jsonb_build_object('schoolCode',?,'previousCode',?,'name',?,'location',?))",
                actorId, action, code, previousCode, name, location);
    }

    private void enqueueGraph(String code, String previousCode, String operation) {
        jdbc.update("INSERT INTO admin.high_school_graph_sync_jobs(school_code,previous_code,operation) VALUES (?,?,?)",
                code, previousCode, operation);
    }

    private AdminHighSchoolDto map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new AdminHighSchoolDto(rs.getString("code"), rs.getString("name"), rs.getString("location"),
                created == null ? null : created.toInstant(), updated == null ? null : updated.toInstant(),
                rs.getString("graph_sync_status"), rs.getString("graph_sync_job_id"));
    }
}
