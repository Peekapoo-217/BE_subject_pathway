package vn.edu.thesis.BE_subject_pathway.dto.response;

public record AdminDraftGroupDto(
        String groupKey,
        String schoolName,
        String schoolCode,
        String location,
        String academicYear,
        long draftCount,
        long sourceDocumentCount) {}
