# Admin subsystem database and operations

## PostgreSQL organization

Flyway V4 creates the admin subsystem tables, V6 moves them into the PostgreSQL `admin` schema, V7 adds the delete workflow and school/year draft grouping indexes, V8 aligns auth/extraction table ownership and adds atomic publication plus the graph-sync outbox, and V9 adds proposed school province/code metadata. V8 preserves existing account, session, extraction, source-file, and published data while renaming/moving tables and dropping the then-unused proposed location field. V9 restores province metadata for newly proposed schools and rebuilds the generated grouping key. The application SQL uses schema-qualified names so behavior does not depend on `search_path`.

Tables in `admin`:

- `school_documents`, `school_document_versions`, `extraction_log`, `subject_group_drafts`
- `subject_group_graph_sync_jobs` (retryable PostgreSQL-to-Neo4j outbox)
- `admin_approval_audit`, `llm_configurations`

`app_users` and `refresh_sessions` are in `public`, with `public.app_users` as the source of truth for accounts. Shared domain tables such as `high_schools`, `subjects`, and `subject_groups` also remain in `public`. In DBeaver, refresh the database navigator after Flyway V8 runs. A DBeaver workspace is a client-side organization feature and does not create a database group.

## Apply migrations

Run the backend with the intended PostgreSQL connection after confirming the target database in its local environment configuration. Flyway applies V4 through V8 in order at application startup. Do not manually execute a migration against a database where Flyway history or prior schema state is unknown.

V7 adds a stored generated grouping key to `admin.school_documents`; PostgreSQL computes it for existing rows while applying the `ALTER TABLE`, which can hold a strong table lock. Before applying V7 to a production database, check the document row count and schedule the migration when that table can tolerate the lock. This target-specific lock duration was not measured; the migration was exercised only against a disposable PostgreSQL instance.

V8 rebuilds `school_group_key` without `proposed_school_location` and drops that column. Proposed schools are grouped by normalized name; registered schools still group by school code. It also changes `admin.extraction_jobs` to `admin.extraction_log`, moves `app_users` and `refresh_sessions` back to `public`, adds the one-to-four subject check for drafts, and creates the graph-sync outbox. Review the configured database and Flyway history before starting the backend; this workspace change does not apply V8 to a database.

To verify after startup, inspect the `admin` schema in DBeaver or run:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'admin'
ORDER BY table_name;
```

## Private PDF and image storage

The backend stores PDF, PNG, JPEG, and WebP objects in a private Cloudflare R2 bucket and exposes access through authenticated backend endpoints. A single image is a complete document version and source page 1. PDF files are limited to 50 MiB and images to 10 MiB; the worker also rejects images above 20 megapixels. Configure the R2 endpoint, bucket, access key ID, secret access key, and presigning settings in the local backend environment; keep credentials out of source control. The extraction worker uses the same bucket and accesses PostgreSQL admin tables with the `admin.` schema prefix.

The extraction worker only creates drafts. A SUPER_ADMIN reviews and publishes the complete submitted set for one school/year through the backend; the backend writes public subject groups transactionally and enqueues a Neo4j projection job. The graph projection worker retries the outbox asynchronously; PostgreSQL remains the source of truth.

## Document deletion and draft grouping

Each upload creates its own document. The admin UI no longer offers an add-version action. Existing `school_document_versions` rows remain because they identify the original R2 object and extraction provenance.

An ADMIN delete action sets `DELETE_REQUESTED`; SUPER_ADMIN can approve that request. SUPER_ADMIN can also soft-delete a document directly. Final soft-delete cancels its PENDING, RUNNING, and RETRY extraction jobs and removes every non-PUBLISHED draft from all its source versions. Before each draft is removed, the backend writes its full fields to `admin_approval_audit.details` and clears `draft_id` references from prior audit rows so the RESTRICT foreign key remains valid. Published drafts and the public `subject_groups` rows remain. The R2 objects and version rows remain available for audit.

The worker claims jobs only for active documents and locks the document before claiming or writing results. It checks the job and document state again after OCR/LLM work. A delete request blocks new claims and blocks draft edits, submission, and publication; a job already running may finish while preserving `DELETE_REQUESTED`. Final soft-delete cancels that job, so a late success or failure cannot recreate drafts or change the deleted state.

The admin draft list pages school/year groups with a signed keyset cursor. Registered schools use `school_code`; proposed schools use a generated `school_group_key` from normalized name and province. The upload form fetches province choices through the backend proxy and asks the backend to suggest/check a school code. A code collision with an existing school of the same normalized name and province offers reuse; collisions with a different school require another code. The backend repeats identity and code checks on upload/edit and creates `high_schools` only during publication. Expanding a group loads its drafts in a second keyset-paginated request and separates source versions by their version IDs. A valid subject group has one to four distinct subject codes; a school may offer fewer than four. SUPER_ADMIN can hard-delete one unpublished draft after a confirmation step; the operation writes an audit snapshot and does not change the uploaded document or its R2 object. Active extraction for that source version returns HTTP `409`.

New document upload and metadata edits store the starting year as four digits (`YYYY`), selected with the admin year picker. Existing database rows remain as-is; the form presents a legacy range such as `2026-2027` as its starting year if it is edited. Province API URL is configurable with `PROVINCES_API_URL`; the backend caches the validated result in memory for 24 hours and serves stale cached values if the provider is temporarily unavailable. The default source is `https://34tinhthanh.com/api/provinces`.

SUPER_ADMIN publication is one atomic action per school/year: every current DRAFT/SUBMITTED draft in that group must be loaded and reviewed, then the UI requires an explicit review confirmation. ADMIN uses **Lưu và gửi duyệt**; SUPER_ADMIN can publish DRAFT rows directly and does not submit drafts for approval. Backend compares the supplied `reviewedDraftIds` to the locked current set and rejects the whole batch on stale data, active extraction, invalid subject codes, or group-code conflict. The outbox row is written in the same PostgreSQL transaction as publication. Neo4j is updated by `graph-projection-worker`, which retries failed jobs and records only a safe error code.

Publication writes the school and its groups to `public.high_schools` and `public.subject_groups`. It does not create university admission combinations or offers. The student flow reads the published group and subjects, then searches existing admission combinations and `admission_offers`; the chatbot uses the Neo4j projection for subject group context and only connects a program to a group when all required subjects are covered.

Image extraction first reads text locally with Tesseract, then sends that text to the effective `EXTRACTION` model through the OpenAI-compatible chat completions API. The model does not need vision support. The worker prompt limits extraction to the school elective-subject group column and excludes the specialized-subject column. If OCR or extraction fails, the admin document list displays a safe reason and offers **Thử trích xuất lại** for the latest failed job after the issue is corrected. Raw OCR text is not persisted. Existing document-version and draft tables already store content type and source page, so no schema migration is required.

## LLM configuration

The SUPER_ADMIN screen exposes two settings: document extraction (`EXTRACTION`) and chat (`CHAT`). The chat profile serves both the separate intent parsing stage (`INTENT`) and answer generation. Configuration metadata from `data-scrawler/.env` is read through its authenticated internal status route; only endpoint, model, and whether a key exists are returned to the backend admin API. The browser never receives the key.

Enter a key to replace the environment key with an encrypted admin-managed key. Leave the key blank when saving to select the environment key. In that mode the RAG API and extraction worker resolve `LLM_API_KEY` (or `GEMINI_API_KEY`) from their environment. Database endpoint/model overrides remain in effect; if no database configuration exists, endpoint/model also come from `LLM_API_BASE` and `LLM_MODEL`. Configure matching `INTERNAL_SERVICE_TOKEN` values in backend and data-scrawler, and set backend `RAG_INTERNAL_API_URL` to the RAG API base URL.

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
