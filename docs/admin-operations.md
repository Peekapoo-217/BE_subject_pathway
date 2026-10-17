# Admin subsystem database and operations

## PostgreSQL organization

Flyway V4 creates the admin subsystem tables, V6 moves them into the PostgreSQL `admin` schema, V7 adds the delete workflow and school/year draft grouping indexes, V8 aligns auth/extraction table ownership and adds atomic publication plus the graph-sync outbox, V9 adds proposed school province/code metadata, V10 adds school soft deletion and school graph-sync jobs, and V11 adds document/graph publication lifecycle tracking. V11 preserves existing document, published group, audit, and object-store data while backfilling group provenance where the publication audit proves it. Groups without a provable source receive an `UNATTRIBUTED` marker and are not hidden by document unpublication. The application SQL uses schema-qualified names so behavior does not depend on `search_path`.

Tables in `admin`:

- `school_documents`, `school_document_versions`, `extraction_log`, `subject_group_drafts`
- `subject_group_graph_sync_jobs` (retryable PostgreSQL-to-Neo4j outbox)
- `admin_approval_audit`, `llm_configurations` (legacy table retained but not read or written by runtime code)
- `high_school_code_history`, `high_school_graph_sync_jobs`

`app_users` and `refresh_sessions` are in `public`, with `public.app_users` as the source of truth for accounts. Shared domain tables such as `high_schools`, `subjects`, and `subject_groups` also remain in `public`. In DBeaver, refresh the database navigator after Flyway V8 runs. A DBeaver workspace is a client-side organization feature and does not create a database group.

## Apply migrations

Run the backend with the intended PostgreSQL connection after confirming the target database in its local environment configuration. Flyway applies pending migrations in order at application startup. Do not manually execute a migration against a database where Flyway history or prior schema state is unknown.

V7 adds a stored generated grouping key to `admin.school_documents`; PostgreSQL computes it for existing rows while applying the `ALTER TABLE`, which can hold a strong table lock. Before applying V7 to a production database, check the document row count and schedule the migration when that table can tolerate the lock. This target-specific lock duration was not measured; the migration was exercised only against a disposable PostgreSQL instance.

V8 rebuilds `school_group_key` without `proposed_school_location` and drops that column. Proposed schools are grouped by normalized name; registered schools still group by school code. It also changes `admin.extraction_jobs` to `admin.extraction_log`, moves `app_users` and `refresh_sessions` back to `public`, adds the one-to-four subject check for drafts, and creates the graph-sync outbox. Review the configured database and Flyway history before starting the backend; this workspace change does not apply V8 to a database.

V10 adds `deleted_at`/`deleted_by` to schools, published subject groups, and drafts; creates a historical code registry and a retryable school graph outbox; and changes document/job foreign keys to cascade school-code updates while restricting physical deletion. V11 adds per-group publication state with a monotonic revision, per-document group provenance, recoverable document status, and document-linked graph-sync jobs. V12 is data-only: it repairs non-conflicting document group provenance rows whose old school code maps unambiguously through `admin.high_school_code_history`; conflicting rows stay unchanged for review. Apply V12 before deploying the lifecycle API and worker. These source changes do not apply migrations to a database.

To verify after startup, inspect the `admin` schema in DBeaver or run:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'admin'
ORDER BY table_name;
```

## Private PDF and image storage

The backend stores PDF, PNG, JPEG, and WebP objects in a private S3-compatible bucket and exposes access through authenticated backend endpoints. Local development uses MinIO in the Compose `minio` profile; the same backend and extraction worker can select R2 through `OBJECT_STORAGE_PROVIDER` and the shared `OBJECT_STORAGE_*` settings. A single image is a complete document version and source page 1. PDF files are limited to 50 MiB and images to 10 MiB; the worker also rejects images above 20 megapixels. The backend uses an internal S3 endpoint for operations and a separate public endpoint for browser presigned URLs. Keep credentials in ignored local environment files. The extraction worker uses the same bucket and accesses PostgreSQL admin tables with the `admin.` schema prefix.

The extraction worker only creates drafts. A SUPER_ADMIN reviews and publishes the complete submitted set for one school/year through the backend; the backend writes public subject groups transactionally and enqueues a Neo4j projection job. The graph projection worker retries the outbox asynchronously; PostgreSQL remains the source of truth.

## Document deletion and draft grouping

Each upload creates its own document. The admin UI no longer offers an add-version action. Existing `school_document_versions` rows remain because they identify the source object key and extraction provenance. Switching provider does not copy objects: a key is available only when its object exists in the currently selected bucket. The download endpoint returns HTTP 404 when the object is missing from that store.

ADMIN `DELETE /documents/{id}` records `DELETE_REQUESTED`; the document remains active in PostgreSQL until SUPER_ADMIN approves through `/documents/{id}/approve-delete`. SUPER_ADMIN can soft-delete directly. Both completed delete paths retain restorable drafts and MinIO objects, cancel extraction jobs, leave the published graph unchanged, and remove the document from normal lists. Only SUPER_ADMIN may inspect deleted documents, unpublish, or restore. SUPER_ADMIN unpublication is limited to soft-deleted documents. V11 provenance associates a published group with its source document; a group stays published while any other attributed document remains published. An `UNATTRIBUTED` source, a missing document-to-group mapping, or an incomplete set of published group sources blocks the operation with HTTP 409, so legacy or shared graph data is not hidden by inference. PostgreSQL recomputes effective group visibility and inserts the outbox job in the same transaction. The Neo4j worker removes the affected school's `OFFERS_GROUP` relationship when no published document source remains, while retaining the `HighSchool`/`SubjectGroup` nodes and `INCLUDES_SUBJECT` edges. Restore checks every stored object key in the configured MinIO bucket and verifies group provenance before changing PostgreSQL, restores only drafts soft-deleted with that document, marks its source published, and queues an idempotent graph projection. The revision guard prevents stale jobs from recreating a relation after a newer unpublish. Both lifecycle endpoints return HTTP 202 while the worker is pending; `GET /api/v1/admin/documents/{id}/graph-sync/{jobId}` exposes PENDING/RUNNING/RETRY/SYNCED, and the UI reports completion only at SYNCED.

Document restore reads `admin.school_documents` under row lock, confirms an active parent in `public.high_schools`, and HEAD-checks every `admin.school_document_versions.object_key` against the configured bucket. Since soft-delete retains objects in the same bucket, MinIO needs no move or copy; a missing version returns HTTP 409 before PostgreSQL changes. One PostgreSQL transaction restores matching rows in `admin.subject_group_drafts`, requeues only extraction rows in `admin.extraction_log` cancelled by that deletion when no draft exists, activates the document, recalculates `admin.subject_group_publication_state` from `admin.document_subject_group_sources`, inserts `admin.subject_group_graph_sync_jobs`, and writes the restore audit. The worker then MERGEs the stable group IDs and relationships into Neo4j; this recreates a group node previously deleted by unpublication. A graph failure remains retryable without rolling back or duplicating the PostgreSQL restore. Unpublication deletes only the UID for a group whose effective source state is unpublished; shared school/subject nodes and groups still published by another document remain intact.

The worker claims extraction jobs only for active documents and locks the document before claiming or writing results. It checks the job and document state again after OCR/LLM work. A legacy delete request blocks new claims and blocks draft edits, submission, and publication; a job already running may finish while preserving `DELETE_REQUESTED`. Soft-delete cancels that job, so a late success or failure cannot recreate drafts or change the deleted state.

The admin draft list pages school/year groups with a signed keyset cursor. Registered schools use `school_code`; proposed schools use a generated `school_group_key` from normalized name and province. The upload form fetches province choices through the backend proxy and asks the backend to suggest/check a school code. A code collision with an existing school of the same normalized name and province offers reuse; collisions with a different school require another code. The backend repeats identity and code checks on upload/edit and creates `high_schools` only during publication. Expanding a group loads its drafts in a second keyset-paginated request and separates source versions by their version IDs. A valid subject group has one to four distinct subject codes; a school may offer fewer than four. SUPER_ADMIN can hard-delete one unpublished draft after a confirmation step; the operation writes an audit snapshot and does not change the uploaded document or its R2 object. Active extraction for that source version returns HTTP `409`.

New document upload and metadata edits store the starting year as four digits (`YYYY`), selected with the admin year picker. Existing database rows remain as-is; the form presents a legacy range such as `2026-2027` as its starting year if it is edited. Province API URL is configurable with `PROVINCES_API_URL`; the backend caches the validated result in memory for 24 hours and serves stale cached values if the provider is temporarily unavailable. The default source is `https://34tinhthanh.com/api/provinces`.

SUPER_ADMIN publication is one atomic action per school/year: every current DRAFT/SUBMITTED draft in that group must be loaded and reviewed, then the UI requires an explicit review confirmation. ADMIN uses **Lưu và gửi duyệt**; SUPER_ADMIN can publish DRAFT rows directly and does not submit drafts for approval. Backend compares the supplied `reviewedDraftIds` to the locked current set and rejects the whole batch on stale data, active extraction, invalid subject codes, or group-code conflict. The outbox row is written in the same PostgreSQL transaction as publication. Neo4j is updated by `graph-projection-worker`, which retries failed jobs and records only a safe error code.

Publication writes the school and its groups to `public.high_schools` and `public.subject_groups`. It does not create university admission combinations or offers. The student flow reads the published group and subjects, then searches existing admission combinations and `admission_offers`; the chatbot uses the Neo4j projection for subject group context and only connects a program to a group when all required subjects are covered.

Image extraction first reads text locally with Tesseract, then sends that text to the effective `EXTRACTION` model through the OpenAI-compatible chat completions API. The model does not need vision support. The worker prompt limits extraction to the school elective-subject group column and excludes the specialized-subject column. If OCR or extraction fails, the admin document list displays a safe reason and offers **Thử trích xuất lại** for the latest failed job after the issue is corrected. Raw OCR text is not persisted. Existing document-version and draft tables already store content type and source page, so no schema migration is required.

## High-school management and AI configuration

ADMIN and SUPER_ADMIN can list, create, edit, and soft-delete schools through `/api/v1/admin/high-schools`. Listing is keyset-paginated by school code. SUPER_ADMIN alone can list deleted schools, restore one, and poll its graph job. New codes default to the initials after `THPT` and remain editable. A code is not reusable if present as a current code, historical code, or active proposed-document code. Code changes preserve the old code in `admin.high_school_code_history`; PostgreSQL foreign keys update published groups/documents and the Neo4j outbox records deletion of the old code plus upsert of the new one.

Deleting a school sets the deleted timestamp on its published groups, documents, and drafts in one PostgreSQL transaction, cancels active extraction jobs, writes audit history, and queues graph cleanup. Public APIs and graph full-sync reads exclude deleted schools/groups immediately. The worker removes graph nodes asynchronously. Restoring the school reactivates only the school and groups deleted by that same operation; its documents remain deleted until SUPER_ADMIN restores them individually after every retained version key is confirmed in the active object store. School graph jobs lock the current PostgreSQL school row and reconcile from its present state, so a stale DELETE job cannot remove a school restored before the job runs. Objects in the active store and document version rows remain available for audit.

School restore locks `public.high_schools`, restores only `public.subject_groups` whose `deleted_at` and `deleted_by` match that school deletion, writes `admin.high_school_graph_sync_jobs` and `admin.admin_approval_audit`, and commits those PostgreSQL changes together. It deliberately leaves `admin.school_documents`, their versions, drafts, and MinIO objects in their deleted state until each document is restored separately.

### Local MinIO Compose

From the monorepo root, configure `OBJECT_STORAGE_*` credentials in the ignored `BE_subject_pathway/.env`, then start the complete stack, including MinIO, with:

```powershell
docker compose up -d --build
```

MinIO exposes the S3 API on `http://localhost:9000` and console on `http://localhost:9001`. The `minio_data` volume retains uploaded objects. The `minio-init` job creates the configured private bucket idempotently before backend uploads and OCR jobs start. Keep `OBJECT_STORAGE_INTERNAL_ENDPOINT=http://minio:9000` and `OBJECT_STORAGE_PUBLIC_ENDPOINT=http://localhost:9000` for Docker. The backend and worker use the same provider, bucket and credentials. The current Compose setup selects MinIO; switching its containers to R2 requires changing the Compose storage settings. Existing objects are not copied when switching stores.

The MinIO image is built from the official source tag `RELEASE.2025-10-15T17-29-55Z`; the upstream release guidance recommends building from source for containers. The upstream repository was archived in April 2026, so review the supported MinIO distribution before upgrading this pinned version.

AI settings are environment-only in `data-scrawler/.env`: `LLM_API_KEY`, `LLM_API_BASE`, and `LLM_MODEL`. Intent parsing and chat answer generation remain separate calls using the configured model. There is no admin AI configuration screen or runtime endpoint override. The legacy `admin.llm_configurations` table remains unused for compatibility; no keys are stored there.

## Redis / Memurai browser sessions

Authentication now uses Memurai (Redis protocol) for ephemeral sessions. PostgreSQL `public.app_users` remains the source of truth for accounts, permissions, active status and `token_version`. The legacy `public.refresh_sessions` table is preserved but unused; V8 changes its schema without deleting its rows.

Configure the backend `.env` using the keys in `.env.example`:

| Variable | Local default | Purpose |
| --- | --- | --- |
| `REDIS_HOST` | `127.0.0.1` | Native backend connection; root Compose overrides this with `host.docker.internal`. |
| `REDIS_PORT` | `6379` | Memurai port. |
| `REDIS_USERNAME`, `REDIS_PASSWORD` | empty | Configure when Memurai authentication is enabled. Never commit credentials. |
| `REDIS_CONNECT_TIMEOUT`, `REDIS_TIMEOUT` | `2s` | Bound connection and command waits. |
| `AUTH_REDIS_KEY_PREFIX` | `subject-pathway:auth:` | Namespace, separated from other applications. |
| `AUTH_COOKIE_SECURE` | `false` in local env | Set **true** with HTTPS in production; application default is true. |
| `ACCESS_TOKEN_TTL_SECONDS` | `900` | Access-token expiry. |
| `REFRESH_TOKEN_TTL_DAYS` | `7` | Sliding refresh/session lifetime. |

Use the same hostname (`localhost`) for local frontend and API. The CORS allowlist must contain the frontend origin and permits credentials; wildcard origins are not supported. Production reverse proxy must route `/api/v1/` to this backend under the frontend's HTTPS origin and preserve `Set-Cookie`. No cookie `Domain` is configured.

Redis keys:

- `<prefix>{sid}:session`: hash fields `userId`, `version`, `hash` (SHA-256 of the full refresh token), with expiry.
- `<prefix>{sid}:used:<digest>`: spent-token marker with seven-day TTL. Lua compare-and-rotate creates this marker atomically with replacing the current hash. Known spent-token replay revokes that session; a random wrong secret does not revoke another user's session.
- Raw refresh tokens and JWTs are never stored in Redis. No `KEYS`/scan is used for runtime revocation: password/role/active updates increment PostgreSQL `token_version`; both access and refresh paths reject all older versions immediately.

The browser serializes cookie mutations across tabs. Other clients must also serialize refresh requests: parallel use of the same refresh token is treated as replay. A lost refresh response may require signing in again. Each device has a separate session. Redis loss/expiry requires re-login; Redis downtime returns 503 for authentication and protected requests. Never report logout success until Redis confirms revocation. Existing in-flight requests may finish; subsequent requests are denied.

For local Compose, run from the monorepo root:

```powershell
docker compose up -d --build backend
```

Check connectivity from the backend container before changing Memurai bind/firewall settings. The local service currently listens on loopback; do not open port 6379 publicly. Persistence configuration is owned by the Memurai installation; application deployment does not alter it.

Tests:

```powershell
cd BE_subject_pathway
.\mvnw.cmd test
# Optional real local Memurai tests (127.0.0.1:6379 without auth):
$env:RUN_REDIS_TESTS='true'
.\mvnw.cmd '-Dtest=RedisSessionStoreIntegrationTest' test
Remove-Item Env:RUN_REDIS_TESTS
```

Integration tests use a random `test:auth:<uuid>:` prefix, clean only their own keys and never flush the database.
