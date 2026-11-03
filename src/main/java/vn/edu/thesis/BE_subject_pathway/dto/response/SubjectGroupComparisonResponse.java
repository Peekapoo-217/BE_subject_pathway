package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Evidence-based comparison. The response deliberately contains no aggregate score or rank. */
public record SubjectGroupComparisonResponse(
        String schoolCode,
        String academicYear,
        String desiredProgramQuery,
        List<GroupComparison> groups) {

    public record GroupComparison(
            String groupCode,
            String groupName,
            /** Backward-compatible effective subject set including mandatory subjects. */
            List<SubjectDto> subjects,
            /** Subjects explicitly attached to this high-school group. */
            List<SubjectDto> groupSubjects,
            /** Mandatory/common subjects, separated from group membership. */
            List<SubjectDto> mandatorySubjects,
            List<SubjectDto> matchedPreferredSubjects,
            List<SubjectDto> matchedDislikedSubjects,
            List<GradeSignal> gradeSignals,
            List<StrengthSignal> strengthSignals,
            List<ConfidenceSignal> confidenceSignals,
            List<AdmissionCombinationMatch> matchingAdmissionCombinations) {
    }

    public record GradeSignal(
            String subjectCode,
            String subjectName,
            BigDecimal value,
            BigDecimal scale,
            String assessmentType) {
    }

    public record ConfidenceSignal(
            String subjectCode,
            String subjectName,
            int value) {
    }

    public record StrengthSignal(
            String subjectCode,
            String subjectName,
            String description) {
    }

    public record AdmissionCombinationMatch(
            String combinationId,
            String combinationDisplay,
            List<CombinationLabelDto> publicLabels,
            List<AdmissionPathway> pathwaysForRequestedProgram) {
    }

    public record AdmissionPathway(
            String universityCode,
            String universityName,
            String programCode,
            String programName,
            short admissionYear,
            String methodCodes,
            String sourceUrl) {
    }
}
