package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AdminSubjectGroupDraftDto(
        String id,
        String documentId,
        String documentVersionId,
        int documentVersionNumber,
        String documentTitle,
        String documentFilename,
        String schoolName,
        String schoolCode,
        String academicYear,
        String documentStatus,
        String groupCode,
        String groupName,
        List<String> subjectCodes,
        List<Integer> sourcePages,
        String status,
        BigDecimal confidence,
        String createdAt) {}
