# OpenAPI and Swagger UI

The Spring Boot API publishes its OpenAPI 3 contract through Springdoc.

When the backend is running locally on port `8080`:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

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
| `GET/POST` | `/api/v1/admin/llm-configurations` | SUPER_ADMIN reads safe status for document extraction and chat configuration, then saves endpoint/model and an optional API key. A blank key selects the `data-scrawler` environment key. |
| `GET/POST/PUT/DELETE` | `/api/v1/admin/documents` | ADMIN/SUPER_ADMIN document listing (cursor), private PDF or image upload, metadata update, ADMIN delete request, or SUPER_ADMIN direct soft-delete. |
| `GET` | `/api/v1/admin/documents/provinces` | Return the cached province catalog for proposed THPT schools. |
| `GET` | `/api/v1/admin/documents/school-code-suggestion` | Suggest/check a school code; query parameters: `schoolName`, `provinceCode`, optional custom `code`. |
| `GET` | `/api/v1/admin/documents/{id}/download-url` | Get a short-lived URL for the latest authorized document version. |
| `POST` | `/api/v1/admin/documents/{id}/retry-extraction` | Requeue a failed extraction job for the latest version without a new upload. |
| `GET` | `/api/v1/admin/documents/draft-groups` | Cursor-paginated draft groups by school and academic year. SUPER_ADMIN sees DRAFT and SUBMITTED; ADMIN sees DRAFT. |
| `GET` | `/api/v1/admin/documents/draft-groups/drafts` | Cursor-paginated drafts in one group; accepts `groupKey` and `academicYear` from the group summary. |
| `GET/PUT/POST/PATCH` | `/api/v1/admin/documents/drafts` | ADMIN lists/edits/submits DRAFT rows; only ADMIN may submit a draft for approval. |
| `POST` | `/api/v1/admin/documents/draft-groups/publish` | SUPER_ADMIN publishes every reviewed DRAFT/SUBMITTED draft for one school and academic year as one transaction. |
| `DELETE` | `/api/v1/admin/documents/drafts/{draftId}` | SUPER_ADMIN hard-deletes one unpublished draft with an audit snapshot. |
| `POST` | `/api/v1/admin/documents/{id}/approve-delete` | SUPER_ADMIN approval of a document soft-delete request. |
| `GET` | `/api/v1/internal/llm/{purpose}` | Internal worker profile retrieval guarded by `X-Internal-Service-Token`; `INTENT` resolves to `CHAT`, and blank saved keys are returned empty for worker-side environment fallback. |

Each `possibleCombinations[]` item includes `labels: [{code, subjectDetails}]` from
the newest imported combination catalogue snapshot. `labels` is empty if no
catalogue row can be linked unambiguously to the canonical combination.

All application endpoints use the common `ApiResponse<T>` envelope (`success`, `message`, `data`).
Each document upload creates a separate document and extraction job. Upload accepts one PDF up to 50 MiB or one PNG, JPEG, or WebP image up to 10 MiB. Uploaded images represent source page 1. The response confirms the queued job, not completed extraction. There is no add-version upload action or endpoint; historical version rows remain as source and audit records. `GET /api/v1/admin/documents` adds nullable `errorCode` for the latest source version's job.

The draft screen first calls `/draft-groups`, then expands a school/year using `/draft-groups/drafts?groupKey=...&academicYear=...`. Both endpoints use signed keyset cursors. The group page is ordered by the generated `school_group_key` and academic year, then aggregates only the current page of groups. Registered schools group by school code. Proposed schools group by normalized name and province. Draft rows include source version metadata so historical files from one document are shown separately.

New document uploads and metadata edits use a four-digit starting calendar year (`YYYY`, 1900–2199); the admin UI selects it from a year calendar. Existing rows are not rewritten by this change. For a new school, send `proposedSchoolName`, `provinceCode`, and `proposedSchoolCode`; otherwise send the selected registered `schoolCode`. Province codes come from `/documents/provinces`. The suggestion response reports `suggestedCode`, `available`, `canReuseExistingSchool`, and existing-school details when a same-name/province school can be reused. The code suggestion takes the first letter of each word after `THPT` (for example, `Trường THPT Tôn Đức Thắng` becomes `TĐT`); code availability and duplicate school identity are checked again on write. If a matching school exists, select it instead of creating another row; if only the code is occupied by a different school, enter a different unique code.

Flyway V9 stores proposed province and code in `admin.school_documents` and rebuilds the generated school/year grouping key to include the province. It does not alter existing school, document, subject-group, or Neo4j records. On publication, the proposed code/name/province are used to create `public.high_schools`; the existing graph projection then reads the published `high_schools.location`. The academic-year value is passed through as a string in PostgreSQL and Neo4j equality filters, so this change requires no query-format migration; legacy year ranges remain readable until edited. The existing Tôn Đức Thắng record is intentionally not backfilled and can be updated separately.

Draft groups may contain one to four distinct subject codes. Extraction, draft editing, publication, and recommendation filtering accept this range; they do not require exactly four subjects. To publish, SUPER_ADMIN loads every page for one school/year, reviews the complete set, checks the confirmation that the set was reviewed, then calls `POST /draft-groups/publish` with `{groupKey, academicYear, reviewedDraftIds}`. DRAFT rows may be published directly; ADMIN uses the separate submit endpoint to request review. The backend locks the school/year documents and drafts, rejects stale IDs, unsupported states, active extraction, invalid subjects, or duplicate/already-published group codes, and publishes the complete set atomically. Each successful publication inserts a PostgreSQL outbox job; the graph projection worker retries Neo4j sync in the background. The response includes `batchId`, `publishedGroupCount`, and initial `graphSyncStatus: PENDING`; document listing exposes the latest graph sync status.

ADMIN `DELETE /documents/{id}` changes the state to `DELETE_REQUESTED`; SUPER_ADMIN approval performs the soft delete. SUPER_ADMIN `DELETE /documents/{id}` soft-deletes immediately. Final soft-delete cancels pending, running, and retry extraction jobs; writes a full audit snapshot for each non-published draft; detaches prior audit rows; and deletes those drafts. Published drafts and published `subject_groups` remain, as do R2 objects and document-version records. A worker that was already processing cannot write draft results after the document is deleted.

SUPER_ADMIN `DELETE /documents/drafts/{draftId}` hard-deletes one unpublished draft after storing its audit snapshot. It leaves the uploaded document, source object, published data, and document status unchanged. If that source version has an active extraction job, the endpoint returns HTTP `409`. The endpoint rejects PUBLISHED drafts. Edit, submit, and publish operations are blocked while a source document is `DELETE_REQUESTED` or `DELETED`. Oversized multipart requests return HTTP `413`.
Validation failures return HTTP `400`; invalid login credentials and refresh tokens return `401`;
duplicate usernames at registration return `409`; missing business data returns `404`; and
unexpected errors return `500` with a generic customer-facing message.

LLM configuration purposes exposed to SUPER_ADMIN are `EXTRACTION` and `CHAT`. `INTENT` remains a distinct processing step and uses the `CHAT` profile. The configuration list never returns an API key; it reports `keySource`, `keyConfigured`, and safe endpoint/model metadata. On save, a non-empty `apiKey` replaces and encrypts the stored key; an empty or omitted `apiKey` stores the environment-source marker and uses `LLM_API_KEY` (or `GEMINI_API_KEY`) from `data-scrawler`. The backend reads environment metadata from the protected RAG internal endpoint using the shared `INTERNAL_SERVICE_TOKEN` and `RAG_INTERNAL_API_URL`.
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
