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

All application endpoints use the common `ApiResponse<T>` envelope. Validation failures return
HTTP `400`, missing business data returns HTTP `404`, and unexpected errors return HTTP `500`.
