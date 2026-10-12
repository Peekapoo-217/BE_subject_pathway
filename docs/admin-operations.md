# Admin subsystem database and operations

## PostgreSQL organization

Flyway V4 creates the admin subsystem tables and V6 moves them into the PostgreSQL `admin` schema. V6 is additive and preserves rows, constraints, indexes, and foreign keys. The application SQL uses schema-qualified names so behavior does not depend on `search_path`.

Tables in `admin`:

- `app_users`, `refresh_sessions`
- `school_documents`, `school_document_versions`, `extraction_jobs`, `subject_group_drafts`
- `admin_approval_audit`, `llm_configurations`

Shared domain tables such as `high_schools`, `subjects`, and `subject_groups` remain in `public`. In DBeaver, refresh the database navigator after Flyway V6 runs; the admin tables appear under the `admin` schema. A DBeaver workspace is a client-side organization feature and does not create a database group.

## Apply migrations

Run the backend with the intended PostgreSQL connection after confirming the target database in its local environment configuration. Flyway applies V4, V5, and V6 in order at application startup. Do not manually execute V6 against a database where Flyway history or V4 state is unknown.

To verify after startup, inspect the `admin` schema in DBeaver or run:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'admin'
ORDER BY table_name;
```

## Private PDF storage

The backend stores PDF objects in a private Cloudflare R2 bucket and exposes access through authenticated backend endpoints. Configure the R2 endpoint, bucket, access key ID, secret access key, and presigning settings in the local backend environment; keep credentials out of source control. The extraction worker uses the same bucket and accesses PostgreSQL admin tables with the `admin.` schema prefix.

The worker only creates extraction drafts. A SUPER_ADMIN reviews and publishes accepted subject groups through the backend; it does not write published domain records directly.

## Redis / Memurai browser sessions

Authentication now uses Memurai (Redis protocol) for ephemeral sessions. PostgreSQL `admin.app_users` remains the source of truth for accounts, permissions, active status and `token_version`. The legacy `admin.refresh_sessions` table is preserved but unused; no new SQL migration or data deletion is needed.

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
