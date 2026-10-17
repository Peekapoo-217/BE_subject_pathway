# OpenAPI and Swagger UI

The Spring Boot API publishes its OpenAPI 3 contract through Springdoc.

When the backend is running locally on port `8080`:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

The school-group search flow loads `/api/v1/high-schools/{schoolCode}/academic-years`,
then `/api/v1/high-schools/{schoolCode}/subject-groups?academicYear=...` for the selected year.
Years are distinct and sorted newest first; unpublished or soft-deleted groups are excluded.
The UI keeps the year control visible after a school is selected, supports retry after load errors,
and clears previous groups/results when the user changes year. Late responses from older selections
are ignored.

The school-based `POST /api/v1/admissions/search-by-subjects` request includes the selected context:

```json
{
  "subjectCodes": ["PHYSICS", "CHEMISTRY"],
  "schoolCode": "TCV",
  "academicYear": "2026",
  "groupCode": "G01"
}
```

Subject codes must match the published group for that school/year. Missing or deleted schools and
unavailable/unpublished groups return 404; partial context, malformed years, or mismatched subjects
return 400. The context fields are supplied together. Existing subject-only callers may omit all
three fields. Requests to load majors add `universityCode` and retain the same school/year/group.
`academicYear` selects the THPT group; university admission-year filtering is not part of this field.

Read-only SQL regression (database migrated through V11): set `RUN_POSTGRES_TESTS=true` and run
`./mvnw -Dtest=SubjectGroupRepositoryPostgresTest test` (Windows: `.\mvnw.cmd`).
The test uses the configured datasource without running migrations or creating fixtures.

The documented application endpoints are:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/high-schools` | List high schools. |
| `GET` | `/api/v1/high-schools/{schoolCode}/subject-groups` | List a school's subject groups, optionally filtered by academic year. |
| `GET` | `/api/v1/high-schools/{schoolCode}/academic-years` | List academic years available for a school. |
| `POST` | `/api/v1/high-schools/{schoolCode}/subject-groups/recommendations` | Rank subject groups for a student's preferences and confidence scores. |
| `GET` | `/api/v1/subjects/classifications` | List mandatory and elective subjects. |
| `POST` | `/api/v1/admissions/search-by-subjects` | Find matching combinations and universities; optionally list majors for one university. |
| `POST` | `/api/v1/auth/register` | Register a regular USER account. |
| `GET` | `/api/v1/auth/csrf` | Obtain a masked CSRF token and establish the CSRF cookie. |
| `POST` | `/api/v1/auth/login` | Return an access token and set the HttpOnly refresh cookie. |
| `POST` | `/api/v1/auth/refresh` | Rotate a refresh token and issue a new access token. |
| `POST` | `/api/v1/auth/logout` | Revoke the current refresh session. |
| `GET` | `/api/v1/auth/me` | Read the authenticated user's profile and role. |
| `GET/POST/PUT/PATCH/DELETE` | `/api/v1/admin/users` | SUPER_ADMIN user listing (cursor), creation, role/active update, password reset, and deactivation. |
| `GET/POST/PUT/DELETE` | `/api/v1/admin/combinations` | SUPER_ADMIN combination listing (cursor), creation, display update, and archive. |
| `GET/POST/PUT/DELETE` | `/api/v1/admin/high-schools` | ADMIN/SUPER_ADMIN keyset-paginated high-school CRUD. School codes can be generated from the name or entered/edited manually; deletes are soft deletes. |
| `GET` | `/api/v1/admin/high-schools/deleted` | SUPER_ADMIN only; cursor-paginated deleted schools. |
| `POST` | `/api/v1/admin/high-schools/{code}/restore` | SUPER_ADMIN only; restores the school and groups soft-deleted by the same operation, then queues graph reconciliation. Related documents remain deleted. |
| `GET` | `/api/v1/admin/high-schools/{code}/graph-sync/{jobId}` | SUPER_ADMIN only; polls school graph reconciliation status. |
| `GET/POST/PUT/DELETE` | `/api/v1/admin/documents` | ADMIN/SUPER_ADMIN active document list (cursor), private PDF/image upload, metadata update, and delete action. ADMIN creates a delete request; SUPER_ADMIN soft-deletes immediately. |
| `GET` | `/api/v1/admin/documents/deleted` | SUPER_ADMIN only; cursor-paginated soft-deleted documents. |
| `GET` | `/api/v1/admin/documents/{id}` | ADMIN/SUPER_ADMIN document detail; a soft-deleted document is visible only to SUPER_ADMIN, while ADMIN receives 404. |
| `GET` | `/api/v1/admin/documents/provinces` | Return the cached province catalog for proposed THPT schools. |
| `GET` | `/api/v1/admin/documents/school-code-suggestion` | Suggest/check a school code; query parameters: `schoolName`, `provinceCode`, optional custom `code`. |
| `GET` | `/api/v1/admin/documents/{id}/download-url` | Get a short-lived URL for the latest authorized document version; returns `404` when its object is absent from the active store. |
| `POST` | `/api/v1/admin/documents/{id}/unpublish` | SUPER_ADMIN only; requires a soft-deleted document with verified published group provenance. When no other published source remains, the Neo4j worker deletes the group node and its attached relationships. Returns 202 while syncing; returns 409 if attribution is missing or ambiguous. |
| `POST` | `/api/v1/admin/documents/{id}/restore` | SUPER_ADMIN only; validates every retained version object, restores PostgreSQL and drafts, then recreates the graph groups and relationships from PostgreSQL. Returns 202 while the graph worker runs; replaying restore returns the existing job. |
| `GET` | `/api/v1/admin/documents/{id}/graph-sync/{jobId}` | SUPER_ADMIN only; polls the lifecycle outbox job. `SYNCED` confirms Neo4j projection completion. |
| `POST` | `/api/v1/admin/documents/{id}/retry-extraction` | Requeue a failed extraction job for the latest version without a new upload. |
| `GET` | `/api/v1/admin/documents/draft-groups` | Cursor-paginated draft groups by school and academic year. SUPER_ADMIN sees DRAFT and SUBMITTED; ADMIN sees DRAFT. |
| `GET` | `/api/v1/admin/documents/draft-groups/drafts` | Cursor-paginated drafts in one group; accepts `groupKey` and `academicYear` from the group summary. |
| `GET/PUT/POST/PATCH` | `/api/v1/admin/documents/drafts` | ADMIN lists/edits/submits DRAFT rows; only ADMIN may submit a draft for approval. |
| `POST` | `/api/v1/admin/documents/draft-groups/publish` | SUPER_ADMIN publishes every reviewed DRAFT/SUBMITTED draft for one school and academic year as one transaction. |
| `DELETE` | `/api/v1/admin/documents/drafts/{draftId}` | SUPER_ADMIN hard-deletes one unpublished draft with an audit snapshot. |
| `POST` | `/api/v1/admin/documents/{id}/approve-delete` | SUPER_ADMIN approval of a document soft-delete request. |

Each `possibleCombinations[]` item includes `labels: [{code, subjectDetails}]` from
the newest imported combination catalogue snapshot. `labels` is empty if no
catalogue row can be linked unambiguously to the canonical combination.

All application endpoints use the common `ApiResponse<T>` envelope (`success`, `message`, `data`).
Each document upload creates a separate document and extraction job. Upload accepts one PDF up to 50 MiB or one PNG, JPEG, or WebP image up to 10 MiB. Uploaded images represent source page 1. The response confirms the queued job, not completed extraction. There is no add-version upload action or endpoint; historical version rows remain as source and audit records. `GET /api/v1/admin/documents` adds nullable `errorCode` for the latest source version's job.

The draft screen first calls `/draft-groups`, then expands a school/year using `/draft-groups/drafts?groupKey=...&academicYear=...`. Both endpoints use signed keyset cursors. The group page is ordered by the generated `school_group_key` and academic year, then aggregates only the current page of groups. Registered schools group by school code. Proposed schools group by normalized name and province. Draft rows include source version metadata so historical files from one document are shown separately.

New document uploads and metadata edits use a four-digit starting calendar year (`YYYY`, 1900–2199); the admin UI selects it from a year calendar. Existing rows are not rewritten by this change. For a new school, send `proposedSchoolName`, `provinceCode`, and `proposedSchoolCode`; otherwise send the selected registered `schoolCode`. Province codes come from `/documents/provinces`. The suggestion response reports `suggestedCode`, `available`, `canReuseExistingSchool`, and existing-school details when a same-name/province school can be reused. The code suggestion takes the first letter of each word after `THPT` (for example, `Trường THPT Tôn Đức Thắng` becomes `TĐT`); code availability and duplicate school identity are checked again on write. If a matching school exists, select it instead of creating another row; if only the code is occupied by a different school, enter a different unique code.

Flyway V9 stores proposed province and code in `admin.school_documents` and rebuilds the generated school/year grouping key to include the province. It does not alter existing school, document, subject-group, or Neo4j records. On publication, the proposed code/name/province are used to create `public.high_schools`; the existing graph projection then reads the published `high_schools.location`. The academic-year value is passed through as a string in PostgreSQL and Neo4j equality filters, so this change requires no query-format migration; legacy year ranges remain readable until edited. The existing Tôn Đức Thắng record is intentionally not backfilled and can be updated separately.

Draft groups may contain one to four distinct subject codes. Extraction, draft editing, publication, and recommendation filtering accept this range; they do not require exactly four subjects. To publish, SUPER_ADMIN loads every page for one school/year, reviews the complete set, checks the confirmation that the set was reviewed, then calls `POST /draft-groups/publish` with `{groupKey, academicYear, reviewedDraftIds}`. DRAFT rows may be published directly; ADMIN uses the separate submit endpoint to request review. The backend locks the school/year documents and drafts, rejects stale IDs, unsupported states, active extraction, invalid subjects, or duplicate/already-published group codes, and publishes the complete set atomically. Each successful publication inserts a PostgreSQL outbox job; the graph projection worker retries Neo4j sync in the background. The response includes `batchId`, `publishedGroupCount`, and initial `graphSyncStatus: PENDING`; document listing exposes the latest graph sync status.

ADMIN `DELETE /documents/{id}` records `DELETE_REQUESTED`; SUPER_ADMIN must approve it before soft deletion. SUPER_ADMIN may soft-delete directly. A completed soft delete updates PostgreSQL and recoverable drafts, retains MinIO source keys, and leaves Neo4j unchanged, so published groups remain searchable. Deleted documents are excluded from normal lists; only SUPER_ADMIN can inspect, restore, or unpublish them.

`DELETE /documents/{id}` by ADMIN changes the document to `DELETE_REQUESTED`; SUPER_ADMIN may approve it through `POST /documents/{id}/approve-delete`. Approval and direct SUPER_ADMIN deletion soft-delete the document and recoverable drafts in PostgreSQL while retaining MinIO objects and published graph relations. `POST /documents/{id}/unpublish` recomputes visibility for source-attributed groups. A group remains published when another document contributes a published source. Migration V11 marks groups without verified document provenance as `UNATTRIBUTED`; attempting to unpublish a document that owns such a group returns HTTP 409 without changing graph visibility. Successful unpublication sets `is_published=false` in PostgreSQL and queues an outbox job; when no published source remains, the worker deletes the scoped `SubjectGroup` node and its attached relationships from Neo4j. Shared school and subject nodes remain. `POST /documents/{id}/restore` validates that all retained MinIO objects exist, restores PostgreSQL document/draft state, and queues a retryable graph projection that recreates missing group nodes and relationships from PostgreSQL. Lifecycle actions are retryable; repeat unpublish returns the current lifecycle job rather than creating another one. The UI displays PENDING/RUNNING/RETRY and only reports graph completion after the status endpoint returns `SYNCED`.

SUPER_ADMIN `DELETE /documents/drafts/{draftId}` hard-deletes one unpublished draft after storing its audit snapshot. It leaves the uploaded document, source object, published data, and document status unchanged. If that source version has an active extraction job, the endpoint returns HTTP `409`. The endpoint rejects PUBLISHED drafts. Edit, submit, and publish operations are blocked while a source document is `DELETE_REQUESTED` or `DELETED`. Oversized multipart requests return HTTP `413`.
Validation failures return HTTP `400`; invalid login credentials and refresh tokens return `401`;
duplicate usernames at registration return `409`; missing business data returns `404`; and
unexpected errors return `500` with a generic customer-facing message.

AI provider settings are read only from `data-scrawler` environment variables (`LLM_API_KEY`, `LLM_API_BASE`, and `LLM_MODEL`). There is no admin API for reading or changing AI settings. Chat answer generation and intent classification remain separate processing steps while using that configured model.

High-school administration uses `GET /api/v1/admin/high-schools?size=20&cursor=...`, `GET /{code}`, `POST /`, `PUT /{code}`, and `DELETE /{code}`. The list uses a signed keyset cursor ordered by school code and caps page size at 100. Create/update requests contain `{code?, name, provinceCode}`. An omitted code is generated from the first letters after `THPT`; code changes retain the previous code in history, and a historical/current/proposed document code cannot be reused. Delete marks the school, published subject groups, related documents, and drafts as deleted, queues Neo4j cleanup, and retains objects in the active store for audit.

Document objects use one environment-selected S3-compatible provider: `MINIO` locally or `R2`. PostgreSQL stores object keys; it does not store the selected provider, and changing providers does not copy objects. Backend upload and the extraction worker use the same bucket and credentials. `download-url` returns HTTP `404` when the key is not present in the active provider.
Login accepts any non-empty password and verifies it against the stored hash. Registration,
SUPER_ADMIN user creation, and password reset require 6 to 200 characters. Usernames are limited
to 80 characters.

## Browser authentication contract (2026-10-12)

1. `GET /api/v1/auth/csrf` with credentials. Keep its cookie and read `data.token` / `data.headerName`.
2. Send `X-XSRF-TOKEN: <data.token>` on every auth POST. The token from the endpoint is masked; do not substitute an unrelated value. Fetch again after login/register/logout, which clear the old CSRF cookie.
3. Login/register JSON remains `{username, password}`. Success returns `data: {accessToken, tokenType: "Bearer", expiresIn: 900, user: {id, username, role}}`. **No refreshToken is returned in JSON.**
4. The server sets `sp_refresh` with `HttpOnly; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800`, host-only. Production also requires `Secure`; local HTTP explicitly sets `AUTH_COOKIE_SECURE=false`.
5. Refresh/logout POST bodies are empty (`{}` is also allowed). Only the refresh cookie authenticates these operations, not a token in JSON or a Bearer token. Refresh rotates the cookie and resets its seven-day lifetime. Logout removes the session and cookie; the old access token immediately stops working.
6. Other protected APIs use `Authorization: Bearer <accessToken>`. Each request validates the JWT, account role/version in PostgreSQL and session in Redis. Cookie presence alone cannot authorize an admin API.

All auth responses are non-cacheable. Missing/expired/reused refresh tokens return 401, CSRF rejection returns 403, and Redis unavailability returns 503 without internal connection details. Logout failure does not clear the cookie or falsely report success. Valid logout with an already missing session is idempotent.

Swagger: call `/auth/csrf`, copy `data.token` into the `csrfToken` authorization input, then login. The browser retains the HttpOnly cookie automatically. Fetch a fresh CSRF token after login, and enter the returned access token in `bearerAuth` for `/auth/me` or other protected APIs. Do not copy or expose refresh cookies.

This replaces the former refresh-token-in-JSON contract. Deploy frontend/backend together and sign in again after upgrading; old JWTs without a session ID are rejected. `public.refresh_sessions` is retained as historical schema and is no longer queried or written by the authentication flow. `public.app_users` remains the account source of truth. Admin document workflow tables stay in `admin`; the extraction table is named `admin.extraction_log`.
