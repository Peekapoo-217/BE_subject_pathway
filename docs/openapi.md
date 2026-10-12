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
| `GET/POST` | `/api/v1/admin/llm-configurations` | SUPER_ADMIN runtime LLM profile listing and update; API keys are encrypted at rest. |
| `GET/POST/PUT/DELETE` | `/api/v1/admin/documents` | ADMIN document listing (cursor), private PDF upload, metadata update, delete request, and version upload. |
| `GET` | `/api/v1/admin/documents/{id}/download-url` | Get a short-lived URL for an authorized PDF download. |
| `GET/PUT/POST/PATCH` | `/api/v1/admin/documents/drafts` | ADMIN draft listing/edit/submission and SUPER_ADMIN review, approval, or rejection. |
| `POST` | `/api/v1/admin/documents/{id}/approve-delete` | SUPER_ADMIN approval of a document soft-delete request. |
| `GET` | `/api/v1/internal/llm/{purpose}` | Internal worker profile retrieval guarded by `X-Internal-Service-Token`. |

Each `possibleCombinations[]` item includes `labels: [{code, subjectDetails}]` from
the newest imported combination catalogue snapshot. `labels` is empty if no
catalogue row can be linked unambiguously to the canonical combination.

All application endpoints use the common `ApiResponse<T>` envelope (`success`, `message`, `data`).
Validation failures return HTTP `400`; invalid login credentials and refresh tokens return `401`;
duplicate usernames at registration return `409`; missing business data returns `404`; and
unexpected errors return `500` with a generic customer-facing message.
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

This replaces the former refresh-token-in-JSON contract. Deploy frontend/backend together and sign in again after upgrading; old JWTs without a session ID are rejected. `admin.refresh_sessions` is retained as historical schema and is no longer queried or written by the authentication flow.
