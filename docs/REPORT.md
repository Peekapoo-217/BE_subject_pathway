# REPORT - BE_subject_pathway

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
