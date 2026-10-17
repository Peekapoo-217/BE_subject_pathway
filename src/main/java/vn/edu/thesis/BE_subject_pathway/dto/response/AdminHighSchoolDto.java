package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.time.Instant;

public record AdminHighSchoolDto(String code, String name, String location, Instant createdAt, Instant updatedAt,
        String graphSyncStatus, String graphSyncJobId) {
    public AdminHighSchoolDto(String code, String name, String location, Instant createdAt, Instant updatedAt) {
        this(code, name, location, createdAt, updatedAt, "SYNCED", null);
    }
}
