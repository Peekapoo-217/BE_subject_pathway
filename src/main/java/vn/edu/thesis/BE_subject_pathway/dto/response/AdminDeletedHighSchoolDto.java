package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.time.Instant;

public record AdminDeletedHighSchoolDto(String code, String name, String location, Instant deletedAt,
        String graphSyncStatus, String graphSyncJobId) {}
