# REPORT - BE_subject_pathway

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
