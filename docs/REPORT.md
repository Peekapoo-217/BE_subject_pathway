# REPORT - BE_subject_pathway

## Sửa lỗi năm học và xác thực ngữ cảnh tra cứu — 2026-10-16

- [x] Sửa SQLSTATE `42702` tại API academic-years: định danh `sg.academic_year` trong SELECT và ORDER BY để tránh trùng cột với bảng trạng thái công bố.
- [x] Request tra cứu nhận bộ `schoolCode`, `academicYear`, `groupCode`; kiểm tra trường hoạt động, nhóm đang công bố và tập môn khớp năm đã chọn. Giữ tương thích request chỉ có môn; không dùng năm THPT làm năm tuyển sinh đại học.
- [x] Chạy Java 17 với `RUN_POSTGRES_TESTS=true`: `mvn.cmd -o -Dtest=SubjectGroupRepositoryPostgresTest,AdmissionSearchServiceImplTest,AdmissionSearchControllerTest,HighSchoolServiceImplTest test`; 25/25 đạt, không bỏ qua, gồm 2 kiểm thử PostgreSQL chỉ đọc. Dùng Maven cài sẵn vì wrapper gặp lỗi PowerShell khi đọc Target của thư mục cache.
- [x] `docker compose up -d --build --no-deps backend` thành công. API TCV trả năm `["2026"]`, 4 nhóm; request TCV/2026/G01 trả 36 tổ hợp, 1 đại học; thêm DQN trả 45 ngành. Năm không tồn tại trả 404, tập môn không khớp trả 400.
- [x] Cập nhật hợp đồng API tại `docs/openapi.md`. Không thay đổi schema hay dữ liệu nguồn.

## Sửa lỗi 500 khi tải nhóm bản nháp — 2026-10-14

- [x] Tái hiện bằng `AdminPaginationPostgresTest` trên PostgreSQL ở chế độ chỉ đọc: `listDraftGroups` lỗi SQLSTATE `42703` vì CTE `grouped` không xuất cột `school_code`.
- [x] Bổ sung `min(doc.school_code) AS school_code` vào CTE; nhóm trường đã đăng ký trả đúng mã trường, nhóm trường khai báo mới trả `null`.
- [x] Regression test chuyển từ lỗi sang đạt; `.\mvnw.cmd test` chạy 81 test, 0 lỗi, 10 test tích hợp bỏ qua theo cấu hình mặc định.
- [x] Build/restart backend bằng `docker compose up -d --build backend`; đăng nhập SUPER_ADMIN và gọi `GET /api/v1/admin/documents/draft-groups?size=20` nhận HTTP 200, `success=true`, 1 nhóm. Đăng xuất phiên kiểm tra sau khi lấy CSRF token mới.

## Bỏ thêm phiên bản, xóa tài liệu và nhóm bản nháp — 2026-10-14

- [x] T1. Xóa endpoint/service/repository upload phiên bản; mỗi lần upload tạo một tài liệu mới. Giữ các hàng phiên bản lịch sử làm nguồn R2/OCR.
- [x] T2. DELETE của ADMIN chuyển sang `DELETE_REQUESTED`; SUPER_ADMIN xóa mềm trực tiếp hoặc duyệt yêu cầu. Khi xóa cuối cùng: hủy job PENDING/RUNNING/RETRY, lưu snapshot audit, tách khóa ngoại audit và xóa nháp chưa công bố; giữ draft/nhóm môn đã công bố và file R2.
- [x] T3. Thêm SUPER_ADMIN hard-delete từng draft chưa công bố; ghi snapshot audit, không đổi tài liệu nguồn. Trả HTTP 409 nếu version đó đang có job trích xuất.
- [x] T4. Nhóm bản nháp theo school code hoặc tên/địa điểm trường khai báo và năm học; thêm DTO, keyset cursor, metadata version nguồn. Chặn sửa/gửi/công bố khi tài liệu chờ xóa.
- [x] T5. Flyway V7 thêm `CANCELLED`, khóa nhóm trường/năm học được sinh tự động và index truy vấn audit theo draft. Đã thử V1–V7 trên PostgreSQL container dùng riêng; backend sau đó áp dụng V7 vào PostgreSQL local lúc 03:07 UTC ngày 2026-10-14 (Flyway version 7 thành công).
- [x] T6. Cập nhật OpenAPI và tài liệu vận hành. Kiểm tra gần nhất: `.\mvnw.cmd test` (81 test, 0 lỗi, 10 test tích hợp bỏ qua theo cấu hình mặc định); worker 57/57; Angular 50/50 và `npm run build` thành công.

## Drafts endpoint JSON serialization fix — 2026-10-14

- [x] Replace implicit JDBC row serialization for extracted drafts with explicit mapping: PostgreSQL `jsonb` `subject_codes` becomes `string[]`; `integer[]` `source_pages` becomes `number[]`. Invalid stored values fail during row mapping before the response is serialized.
- [x] Add unit regression coverage and a read-only PostgreSQL integration assertion for draft field types.
- [x] Verification: `mvnw.cmd test -q` completed with 73 tests, 0 failures/errors, 9 skipped; PostgreSQL regression ran 5/5.
- [x] Rebuild and restart backend container; authenticated `GET /api/v1/admin/documents/drafts?size=20` returned HTTP 200 with valid JSON, 3 drafts, and the expected array element types.

## OCR–ETL ảnh tài liệu THPT — 2026-10-13

- [x] Mở rộng upload và upload phiên bản cho PNG, JPEG, WebP (10 MiB) và giữ PDF (50 MiB); kiểm tra MIME/chữ ký, lưu đúng `content_type` và khóa R2. Không cần migration vì schema phiên bản đã có `content_type`.
- [x] Bổ sung API thử lại job thất bại của phiên bản mới nhất, `errorCode` trong danh sách tài liệu, trạng thái bản nháp và quyền xem bản nháp `DRAFT` cho SUPER_ADMIN. Swagger và hướng dẫn vận hành đã cập nhật.
- [x] Kiểm tra: `.\mvnw.cmd test -q` thành công; `SchoolDocumentServiceTest` kiểm tra PNG, PDF, file giả và thử lại; `AdminPaginationPostgresTest` chạy read-only trên PostgreSQL và xác nhận truy vấn bản nháp nhiều trạng thái.
- [ ] Chạy thử upload ảnh/R2/job trên bản triển khai mới. Container backend đang chạy chưa được cập nhật trong đợt sửa code này.

### Cập nhật contract vận hành OCR ảnh — 2026-10-13

- [x] Cập nhật tài liệu: worker OCR ảnh bằng Tesseract trước khi gọi model `EXTRACTION`; model chỉ nhận văn bản nên không cần vision.
- [x] Xác nhận API, schema PostgreSQL và quy trình duyệt draft không đổi; OCR text không được lưu vào database.

## Kiểm tra phân lớp và xác minh lỗi 500 admin — 2026-10-12

- [x] Rà soát: `AdminManagementService`, `SchoolDocumentService`, `AuthService` gọi `JdbcTemplate` trực tiếp, SQL nằm ở lớp service.
- [x] Chuyển PostgreSQL SQL sang `repository/admin` theo từng miền quản lý, tài liệu và tài khoản. Service giữ validation, phân trang, transaction và điều phối Redis/R2.
- [x] Đảm bảo thao tác từ chối/gửi duyệt bản nháp có transaction bao cả cập nhật trạng thái và ghi audit.
- [x] Chạy 65 test backend, gồm kiểm thử phân trang trên PostgreSQL và phiên trên Redis: 0 lỗi.
- [x] Build lại container; đăng nhập SUPER_ADMIN và kiểm tra 8 URL danh sách/bộ lọc admin: tất cả trả HTTP 200.

`BearerTokenFilter` cũng dùng `AuthAccountRepository` để kiểm tra role và `token_version`. `JdbcTemplate` của phân hệ admin hiện nằm trong các repository PostgreSQL; transaction nghiệp vụ vẫn đặt ở service. Đường dẫn danh sách user là `/api/v1/admin/users` (số nhiều).

## Sửa lỗi 500 khi tải danh sách admin — 2026-10-12

- [x] Tái hiện: users/combinations/documents trả 500; PostgreSQL báo không xác định kiểu tham số null ở `? IS NULL`.
- [x] A1. Khai báo kiểu SQL tường minh cho các bộ lọc và cursor nullable.
- [x] A2. Thêm test hồi quy đọc PostgreSQL cho trang đầu, bộ lọc và trang tiếp theo.
- [x] A3. Chạy 65 test (0 lỗi), build lại Docker và xác nhận login cùng 8 URL admin trả HTTP 200.


## Phiên Redis và refresh cookie HttpOnly — 2026-10-12

- [x] Chốt contract: access 15 phút, refresh xoay vòng/gia hạn 7 ngày, nhiều thiết bị, logout thu hồi ngay.
- [x] B1. Redis session store, JWT sid, cookie/CSRF, API và thu hồi phiên.
- [x] B2. Cấu hình Memurai cho native/Docker và Swagger/hướng dẫn vận hành.
- [x] B3. 61 test backend đạt, gồm 4 test tích hợp Memurai; Docker build và kiểm tra login/refresh/logout thực tế đạt.
- [x] B4. Tổng kết kết quả và giới hạn triển khai.

### Thay đổi và contract hiện hành

- `RedisSessionStore` lưu hash SHA-256, user ID và token version theo session ID, TTL trượt 7 ngày. Lua xoay token nguyên tử; marker hash đã dùng phát hiện replay và thu hồi phiên. Token giả không được phép thu hồi phiên hợp lệ.
- JWT có `sid`, thời hạn 15 phút. `BearerTokenFilter` kiểm tra tài khoản PostgreSQL và phiên Redis ở mỗi request; logout chặn token đã cấp ngay ở request tiếp theo. Đổi mật khẩu/quyền/active dùng `token_version` để vô hiệu hóa mọi thiết bị, không quét Redis.
- API trả DTO access token/user; refresh chỉ nằm trong cookie HttpOnly. Thêm CSRF endpoint/header, CORS credentials theo allowlist, cookie Secure theo cấu hình và lỗi Redis 503. Chi tiết tại `docs/openapi.md`.
- Ngừng đọc/ghi `admin.refresh_sessions`; giữ nguyên bảng và migration lịch sử. Không thay đổi dữ liệu tuyển sinh, crawler hoặc Neo4j.
- Đã bổ sung biến môi trường Redis/cookie vào `.env.example` và `.env` local được Git ignore. Root `docker-compose.yml` override `REDIS_HOST=host.docker.internal`; Memurai vẫn bind loopback, không sửa firewall/dịch vụ host.

### Kiểm chứng đã thực hiện

- `$env:RUN_REDIS_TESTS='true'; .\mvnw.cmd test`: **61 test đạt**, không skip; 4 test dùng Memurai thật với prefix test riêng, chỉ dọn key do test tạo.
- Sau cập nhật Swagger: `.\mvnw.cmd '-Dtest=AuthSecurityTest' test` đạt 7/7; OpenAPI JSON thực tế có CSRF endpoint, security schemes và DTO không chứa refreshToken.
- `docker compose up -d --build backend`: build bằng Java 17 và khởi động container thành công.
- API thực tế với tài khoản bootstrap từ env: login 200/SUPER_ADMIN, me 200, refresh 200, logout 200; access token cũ gọi me sau logout trả 401. Không ghi password/token vào output hoặc report.
- Chrome headless với profile tạm riêng: login, cookie HttpOnly, khôi phục hai tab cùng lúc và logout đồng bộ thành công.
- `git diff --check`: không có lỗi whitespace. Git xác nhận `.env` bị ignore.

### Điều kiện vận hành

- Phiên theo contract cũ cần đăng nhập lại. Frontend/backend phải triển khai đồng bộ; production dùng HTTPS/cùng origin, `AUTH_COOKIE_SECURE=true`. Local HTTP dùng false.
- Redis là phụ thuộc bắt buộc của API có xác thực. Redis mất key thì đăng nhập lại; Redis lỗi trả 503. Logout lỗi giữ cookie để có thể thử lại, không báo thành công giả.
- Clients phải tuần tự hóa refresh; request trùng bị xem là replay. Mất response sau khi token đã xoay có thể yêu cầu đăng nhập lại. Persistence Memurai không được thay đổi trong công việc này.


## Đồng bộ mật khẩu tối thiểu 6 ký tự — 2026-10-12

- [x] Cập nhật validation đăng ký, tạo người dùng và đặt lại mật khẩu: tối thiểu 6, tối đa 200 ký tự. Login tiếp tục kiểm tra mật khẩu không rỗng rồi xác thực hash.
- [x] Thêm test HTTP cho mật khẩu 6 ký tự hợp lệ và 5 ký tự bị từ chối ở cả ba API.
- [x] Xác nhận bộ test đầy đủ (61 test ở đợt cập nhật Redis), build lại container và kiểm tra login thực tế 200.


## Sửa đăng nhập SUPER_ADMIN và lỗi API cho production — 2026-10-12

- [x] Tái hiện `POST /api/v1/auth/login` trả 400 do container backend cũ vẫn yêu cầu mật khẩu 8 ký tự, dù source đã đổi thành 7.
- [x] Xác nhận tài khoản bootstrap có role `SUPER_ADMIN`, đang active trong PostgreSQL; cấu hình bootstrap đã có đủ biến môi trường.
- [x] Tách validation: login chỉ yêu cầu mật khẩu không rỗng; đăng ký yêu cầu 7–200 ký tự. Sai thông tin đăng nhập trả 401, tên đăng ký trùng trả 409, dữ liệu không hợp lệ trả 400.
- [x] Thông báo validation bằng tiếng Việt; lỗi hệ thống trả thông báo chung và không lộ chi tiết nội bộ. Cập nhật `docs/openapi.md`.
- [x] Chạy `./mvnw.cmd test` (38 test, gồm 5 test của `AuthControllerTest`); build và khởi động lại container bằng `docker compose up -d --build backend`.
- [x] Gọi login thực tế sau khi cập nhật container: HTTP 200, role `SUPER_ADMIN`.

## Admin THPT và quản trị hệ thống — 2026-10-12

- [x] T1. Migration additive cho người dùng, refresh sessions, PDF/version, extraction jobs/drafts, audit và LLM profiles; V6 nhóm bảng vào schema `admin`.
- [x] T2. Đăng ký/đăng nhập/refresh/logout/me, BCrypt, RBAC và bootstrap SUPER_ADMIN.
- [x] T3. Upload/download private qua Cloudflare R2, phiên bản bất biến và metadata file.
- [x] T4. Worker trích xuất PDF/OCR, xác thực LLM và lưu draft có nguồn trang.
- [x] T5. Duyệt, công bố nhóm môn THPT, xóa mềm/duyệt xóa; giữ nhóm môn đã công bố.
- [x] T6. CRUD người dùng/tổ hợp/cấu hình LLM, phân trang keyset cursor.
- [x] T7. Frontend admin và quản lý phiên token.
- [x] T8. Swagger, tài liệu vận hành, kiểm tra phù hợp và cập nhật báo cáo.

Khởi động 2026-10-12: backend ban đầu có Flyway V1–V3 nhưng chưa có auth/admin. R2 vars chưa được cấu hình trong `.env`; không ghi credential vào report. Phần triển khai hoàn tất trong code và tài liệu. R2 chưa được xác thực kết nối.

### Kết quả triển khai admin

- Flyway V4 tạo các bảng admin, V5 thêm trạng thái active cho tổ hợp, V6 chuyển 8 bảng auth/workflow/audit/LLM vào schema PostgreSQL `admin`; các bảng domain dùng chung vẫn ở `public`.
- SQL trong backend và PDF worker đã qualify schema `admin`. V6 giữ nguyên dữ liệu, khóa ngoại, constraint và index hiện có. DBeaver sẽ hiển thị các bảng dưới schema `admin` sau khi migration chạy và navigator được refresh.
- API và cách vận hành được ghi tại `docs/openapi.md` và `docs/admin-operations.md`.
- Kiểm tra đã chạy: `.\mvnw.cmd test` (29 test đạt); `npm run build`; `npm test -- --watch=false --browsers=ChromeHeadless` (8 test đạt); `python -m unittest discover -s tests` (42 test đạt).
- Spring test context không tạo `JdbcTemplate` nên bỏ qua kiểm tra database. Khi chạy backend cục bộ 2026-10-12, Flyway xác thực 7 migration và xác nhận database đã ở version 6, không cần chạy thêm migration. Bootstrap tạo tài khoản `adminsp`; truy vấn chỉ đọc xác nhận role `SUPER_ADMIN`, active và tổng số SUPER_ADMIN là 1. Secret/JWT chỉ lưu trong `.env` bị Git ignore, không ghi vào report.
- Chưa kiểm thử upload/presigned URL R2 do chưa xác thực kết nối R2.
- Chẩn đoán ban đầu 2026-10-12: source từng hạ ngưỡng mật khẩu login từ 8 xuống 7 ký tự, nhưng container chưa được build lại. Bản sửa hiện tại tách quy tắc login/đăng ký như mục trên.

## Admission combination labels
Ngày triển khai: 2026-10-10.

## Thay đổi

- Flyway V3 thêm bảng `admission_combination_labels`: `code`, `subject_details`, `canonical_combination_id` nullable, `source_url`, `source_period`, `retrieved_at`. Unique key `(source_url, source_period, code, subject_details)` cho phép nhập lại.
- `POST /api/v1/admissions/search-by-subjects` giữ request và các trường cũ; mỗi `possibleCombinations[]` thêm `labels: [{code, subjectDetails}]`. Repository lấy các nhãn liên kết của snapshot mới nhất trong một query. Nhãn không liên kết không xuất hiện trong API.
- Swagger tự mô tả trường mới qua DTO; hợp đồng được ghi ở `docs/openapi.md`.

## Vận hành

Chạy migration V3 trước importer của `data-scrawler`. Bảng mới phụ thuộc `admission_combinations`. Mã nguồn ngoài không thay thế `combination_id` chuẩn, không đổi kết quả tìm kiếm. Nếu chưa nhập dữ liệu, `labels` rỗng.

## Kiểm tra

`mvn test -q` đã chạy thành công ngày 2026-10-10, gồm test ánh xạ nhãn trong service và JSON ở controller. Chưa xác nhận migration với PostgreSQL thực: kết nối local `127.0.0.1:5432` bằng cấu hình hiện có bị từ chối xác thực.

## Kiểm thử luồng chọn nhóm môn THPT (2026-10-11)

- [x] Tạo skill unit test riêng cho backend tại `.agents/skills/java-backend-unit-tests/SKILL.md`.
- [x] Bổ sung test service: bốn môn tự chọn được gộp với môn bắt buộc, loại mã trùng, truy vấn tổ hợp bằng bộ mã đầy đủ, không truy vấn offer khi không có tổ hợp phù hợp.
- [x] Kiểm tra: `.\mvnw.cmd -Dtest=AdmissionSearchServiceImplTest test` (6 test đạt); `.\mvnw.cmd test` (25 test đạt).

Các test là unit test với repository mock; chúng xác nhận luồng điều phối của service, không kiểm chứng SQL native query với cơ sở dữ liệu thật. Không thay đổi API hoặc logic production trong đợt kiểm thử này.

## Điều chỉnh workflow tài liệu THPT và schema admin — 2026-10-14

- [x] V8 đổi `admin.extraction_jobs` thành `admin.extraction_log`, chuyển `app_users` và `refresh_sessions` về `public`, bỏ `proposed_school_location` và tái tạo key nhóm trường theo tên chuẩn hóa.
- [x] Ràng buộc nhóm môn cho phép 1–4 mã môn; service và worker tiếp tục kiểm tra mã hợp lệ, không trùng.
- [x] Gỡ endpoint duyệt riêng từng nháp. SUPER_ADMIN công bố toàn bộ bản nháp đã gửi duyệt của một trường/năm học trong transaction; backend khóa và so sánh đúng danh sách ID, từ chối batch stale, đang OCR, sai môn hoặc trùng mã.
- [x] Ghi outbox PostgreSQL trong transaction; API trả batch ID/trạng thái chờ, worker khác project sẽ đồng bộ Neo4j và retry.
- [x] Cập nhật Swagger, tài liệu vận hành và test service/controller/recommendation.
- [x] Kiểm tra focused backend tests; full suite `mvnw.cmd -q test`: 93 test, 0 lỗi/thất bại, 10 test integration được skip do không bật PostgreSQL/Memurai test.

Migration V8 được tạo trong source nhưng chưa áp dụng lên database nào trong phiên này. Backend unit tests không kết nối PostgreSQL thật; cần backend khởi động với database mục tiêu để Flyway apply V8 trước khi chạy các worker bản mới.

## Điều chỉnh quyền công bố nhóm môn THPT — 2026-10-14

- [x] Chỉ ADMIN được gửi bản nháp sang trạng thái `SUBMITTED`; SUPER_ADMIN có thể công bố trực tiếp các bản nháp `DRAFT` hoặc `SUBMITTED` sau khi xác nhận đã rà soát toàn bộ lô trường/năm học.
- [x] Giữ công bố PostgreSQL trong transaction và tạo outbox Neo4j như cũ; không thêm migration và không ghi nhóm THPT vào bảng tuyển sinh đại học.
- [x] Cập nhật mô tả endpoint và hướng dẫn vận hành; thêm regression test cho công bố trực tiếp và lô có trạng thái trộn.
- [x] Kiểm tra: `.\mvnw.cmd -Dtest=SchoolDocumentServiceTest,AdminDocumentControllerTest test` (25 đạt); `.\mvnw.cmd -q test` (95 test, 0 lỗi/thất bại, 10 test bỏ qua do cấu hình tích hợp mặc định).
- [x] Rebuild/recreate service backend; `GET /actuator/health` trả `UP`. Không gọi API công bố nên không thay đổi dữ liệu PostgreSQL.

## Năm học, địa điểm và mã trường THPT — 2026-10-14

- [x] B1. Thêm year picker dạng lịch năm cho form upload và sửa tài liệu; API mới lưu năm bắt đầu `YYYY` (1900–2199). Form sửa hiển thị năm bắt đầu của dữ liệu cũ dạng `YYYY-YYYY`; không tự ghi sửa các bản ghi hiện có.
- [x] B2. Tạo mã trường bằng chữ cái đầu của từng từ sau `THPT` (`Trường THPT Tôn Đức Thắng` → `TĐT`); cho phép nhập mã khác, kiểm tra mã đã dùng và kiểm tra trùng trường theo tên/tỉnh. Nếu đúng trường đã có, UI cho chọn lại trường hiện hữu.
- [x] B3. Thêm API proxy danh sách tỉnh, cache memory 24 giờ và cấu hình URL qua `PROVINCES_API_URL`; địa điểm/mã trường khai báo được lưu ở `admin.school_documents` trong thời gian chờ duyệt.
- [x] B4. Tạo Flyway V9 để lưu địa điểm/mã đề xuất và cập nhật generated `school_group_key` nhóm trường khai báo theo tên + tỉnh. Khi publish, dữ liệu dùng luồng hiện tại để ghi `public.high_schools` và tạo outbox đồng bộ Neo4j.
- [x] B5. Rà soát query: SQL trang quản trị nhóm theo generated `school_group_key` và lấy địa điểm từ trường đã đăng ký hoặc đề xuất; V9 cập nhật định nghĩa key cho trường mới. API tra cứu công khai và Neo4j tiếp tục so sánh chính xác `school_code`/`academic_year`, nên không cần đổi query/Cypher công khai; graph projection hiện có đọc `high_schools.location` sau khi publish.
- [x] B6. Cập nhật OpenAPI, hướng dẫn vận hành và report. Không sửa `data-scrawler` vì projection worker hiện tại đã đồng bộ trường/nhóm/năm qua outbox.
- [x] Kiểm tra focused: `SchoolCodeGeneratorTest`, `ProvinceCatalogServiceTest`, `SchoolDocumentServiceTest`, `AdminDocumentControllerTest`; full `mvnw.cmd -q test`: 105 test, 0 lỗi/thất bại, 10 test tích hợp bị skip theo cấu hình hiện tại.
- [ ] Triển khai migration V9 vào database mục tiêu: chưa thực hiện trong phiên này. Chưa có thay đổi dữ liệu PostgreSQL/Neo4j; bản ghi Tôn Đức Thắng được để nguyên theo yêu cầu.

## Quản trị trường THPT và bỏ cấu hình AI trên giao diện — 2026-10-16

- [x] Gỡ API cấu hình AI và truy vấn cấu hình runtime khỏi backend. Model chỉ nhận thiết lập từ `LLM_API_KEY`, `LLM_API_BASE`, `LLM_MODEL` trong `.env` của `data-scrawler`; bảng `admin.llm_configurations` được giữ lại nhưng không còn được đọc/ghi.
- [x] Thêm migration V10 cho xóa mềm trường/nhóm môn/bản nháp, lịch sử mã trường, outbox đồng bộ graph và index lọc dữ liệu đang hoạt động.
- [x] Thêm API CRUD `/api/v1/admin/high-schools`, cho ADMIN/SUPER_ADMIN; danh sách dùng keyset cursor, mã mới lấy chữ cái đầu sau `THPT`, mã nhập tay được phép, mã hiện tại/lịch sử/mã đề xuất không được tái sử dụng.
- [x] Xóa mềm trường đánh dấu nhóm môn, tài liệu và nháp liên quan, hủy job trích xuất còn hoạt động, ghi audit và xếp hàng dọn graph; object R2 và versions vẫn được giữ.
- [x] Ẩn trường/nhóm/bản nháp đã xóa khỏi các API và luồng công bố; cập nhật OpenAPI và hướng dẫn vận hành.
- [x] Unit tests: `AdminHighSchoolServiceTest`, regression public listing/recommendation, `OpenApiDocumentationTest`. `mvnw.cmd -q test`: 111 test, 0 lỗi/thất bại, 10 integration tests bị bỏ qua do cần dịch vụ ngoài.
- [ ] Chưa áp dụng migration V10 lên PostgreSQL; chưa ghi/xóa dữ liệu PostgreSQL, R2 hoặc Neo4j trong phiên này.

## S3-compatible document storage with Docker MinIO - 2026-10-16

- [x] Replace R2-specific storage wiring with one S3-compatible backend service selected through `OBJECT_STORAGE_PROVIDER`; keep upload, metadata, extraction-job, review and publication workflows unchanged. No database migration was added.
- [x] Separate the internal S3 endpoint from the browser-facing presigned URL endpoint; verify object presence before issuing a download URL and return HTTP 404 when absent from the active store.
- [x] Add a Compose overlay that builds a pinned MinIO OSS source tag, persists data in a named volume, and creates the private bucket idempotently. Local ignored `.env` files select MinIO; existing R2 variables were preserved but are no longer used by the active flow.
- [x] Verification: focused backend tests 34/34; full `.\mvnw.cmd -q test` reported 116 tests, 0 failures/errors, 10 skipped; Compose config valid with MinIO enabled and disabled; MinIO image source build succeeded.
- [ ] Containers were not started in this change. Upload/download and OCR against a running MinIO instance still need a local smoke test. Existing R2-only objects remain unavailable while MinIO is selected; no object copy was performed.
# Khôi phục tài liệu và trường đã xóa mềm — 2026-10-16

- [x] Restore document kiểm tra mọi object version trong bucket đang cấu hình, phục hồi PostgreSQL trong một transaction, ghi outbox graph và audit; request lặp lại phát lại job restore hiện có.
- [x] Kiểm tra provenance nhóm đã công bố trước unpublish/restore; thiếu, lệch hoặc không hoạt động trả HTTP 409 trước thay đổi PostgreSQL. Khôi phục draft chỉ theo cùng `deleted_at`/`deleted_by`, và chỉ requeue extraction bị hủy bởi lần xóa đó.
- [x] Thêm API SUPER_ADMIN để liệt kê và khôi phục trường đã xóa; trường và nhóm môn được khôi phục trước, tài liệu vẫn soft-deleted để khôi phục riêng.
- [x] Thêm migration V12 dạng data-only để sửa các source rows có ánh xạ school code lịch sử không xung đột; không thay đổi schema.
- [x] Kiểm thử: `mvn.cmd clean test` — 144 test, 0 lỗi/thất bại, 12 test integration bị skip do không có dịch vụ test tương ứng.
- [ ] Chưa áp dụng V12 hoặc chạy restore/đồng bộ với PostgreSQL, MinIO và Neo4j thật trong phiên này.

## Phân quyền xóa mềm và ngừng công bố tài liệu — 2026-10-17

> Mục này ghi trạng thái ban đầu. Cập nhật mới nhất trong phần “Hợp đồng API cho xóa node graph và khôi phục” và report data-scrawler đã thay đổi unpublish thành xóa node nhóm cùng cạnh trong Neo4j.

- [x] ADMIN gửi yêu cầu xóa và chờ SUPER_ADMIN duyệt; SUPER_ADMIN có thể xóa mềm trực tiếp. Cả hai đường xóa hủy job trích xuất còn hoạt động, soft delete draft liên quan và ghi audit.
- [x] Xóa mềm chỉ đổi trạng thái trong PostgreSQL, giữ nguyên quan hệ đã công bố trên Neo4j và giữ object nguồn để có thể khôi phục.
- [x] Chỉ SUPER_ADMIN được xem/khôi phục tài liệu đã xóa hoặc ngừng công bố. Ngừng công bố chỉ áp dụng cho tài liệu đã xóa; worker bỏ cạnh `OFFERS_GROUP` khi không còn nguồn tài liệu đã công bố, giữ node và cạnh `INCLUDES_SUBJECT` để khôi phục.
- [x] Worker áp dụng trạng thái cạnh theo revision đơn điệu để job cũ không ghi đè trạng thái mới; xác minh orphan không coi nhóm đã ngừng công bố là lỗi.
- [x] Kiểm thử: `mvn.cmd -o test` (145 test, 0 lỗi/thất bại, 12 integration test bị skip); `python -m unittest discover -s tests` (80 test đạt); test Angular (22/22 đạt); `npm.cmd run build` thành công.
- [ ] Chưa chạy end-to-end với PostgreSQL/Neo4j đang hoạt động; không có migration mới trong thay đổi này.

## Sửa lỗi JDBC khi khôi phục tài liệu — 2026-10-17

- [x] Escape ba toán tử JSONB `?` thành `??` trong truy vấn phục hồi draft để pgjdbc không coi chúng là bind parameter.
- [x] Thêm unit test kiểm tra SQL snapshot và số bind parameter; thêm kiểm thử PostgreSQL read-only cho toán tử JSONB qua JDBC.
- [x] Kiểm tra: repository unit test 3/3; PostgreSQL read-only test 3/3; `mvnw.cmd -o test` (147 test, 0 lỗi/thất bại, 13 integration test bị skip); `mvnw.cmd -o -DskipTests package` thành công; `git diff --check` không có lỗi whitespace.
- [ ] Chưa gọi API khôi phục tài liệu TĐT thật. Bản ghi vẫn giữ trạng thái trước request; cần smoke test có kiểm soát sau khi backend được build lại.

## Hợp đồng API cho xóa node graph và khôi phục — 2026-10-17

- [x] Cập nhật Swagger cho biết unpublish xóa node `SubjectGroup` cùng các quan hệ kèm theo khi không còn nguồn công bố; restore xếp hàng đồng bộ để dựng lại projection từ PostgreSQL.
- [x] Thêm unit test cho mô tả endpoint và thứ tự khôi phục PostgreSQL trước khi tạo graph-sync job.
- [x] Kiểm tra: focused backend tests 50/50; `mvnw.cmd -o -q test` — 148 test, 0 lỗi/thất bại, 13 integration test bị skip.
- [ ] Chưa chạy request thật với PostgreSQL, MinIO và Neo4j; chưa có migration schema mới.
