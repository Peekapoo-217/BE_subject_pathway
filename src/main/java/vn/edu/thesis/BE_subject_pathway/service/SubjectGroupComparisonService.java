package vn.edu.thesis.BE_subject_pathway.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.thesis.BE_subject_pathway.dto.request.StudentGradeInput;
import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectGroupComparisonRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationLabelDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupComparisonResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupComparisonResponse.AdmissionCombinationMatch;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupComparisonResponse.AdmissionPathway;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupComparisonResponse.ConfidenceSignal;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupComparisonResponse.GradeSignal;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupComparisonResponse.GroupComparison;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupComparisonResponse.StrengthSignal;
import vn.edu.thesis.BE_subject_pathway.entity.Subject;
import vn.edu.thesis.BE_subject_pathway.exception.InvalidRecommendationRequestException;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionCombinationRepository;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionOfferRepository;
import vn.edu.thesis.BE_subject_pathway.repository.HighSchoolRepository;
import vn.edu.thesis.BE_subject_pathway.repository.Neo4jSubjectGroupRepository;
import vn.edu.thesis.BE_subject_pathway.repository.Neo4jSubjectGroupRepository.SubjectGroupData;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository;
import vn.edu.thesis.BE_subject_pathway.repository.projection.AdmissionPathwayProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationComponentProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationProjection;

/** Compares each published group using explicit student signals and verified admission paths. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectGroupComparisonService {

    private final HighSchoolRepository highSchoolRepository;
    private final Neo4jSubjectGroupRepository graphRepository;
    private final SubjectRepository subjectRepository;
    private final AdmissionCombinationRepository combinationRepository;
    private final AdmissionOfferRepository offerRepository;

    public SubjectGroupComparisonResponse compare(String schoolCode, SubjectGroupComparisonRequest request) {
        String normalizedSchoolCode = schoolCode == null ? "" : schoolCode.trim();
        if (!highSchoolRepository.existsByCodeAndDeletedAtIsNull(normalizedSchoolCode)) {
            throw new ResourceNotFoundException("Truong THPT khong ton tai");
        }

        List<String> years = graphRepository.findAcademicYears(normalizedSchoolCode);
        String year = request.getAcademicYear() == null || request.getAcademicYear().isBlank()
                ? years.stream().findFirst().orElseThrow(() ->
                        new ResourceNotFoundException("Truong chua co du lieu nhom mon dang cong bo"))
                : request.getAcademicYear().trim();
        if (!years.contains(year)) {
            throw new ResourceNotFoundException("Khong co du lieu nhom mon dang cong bo cho nam hoc: " + year);
        }

        List<SubjectGroupData> groups = graphRepository.findSubjectGroups(normalizedSchoolCode, year);
        if (groups.isEmpty()) {
            throw new ResourceNotFoundException("Khong co nhom mon dang cong bo cho truong va nam hoc da chon");
        }

        Map<String, Subject> subjectsByCode = subjectRepository.findAll().stream()
                .collect(Collectors.toMap(s -> key(s.getCode()), Function.identity(), (first, second) -> first));
        Map<String, String> canonicalByCode = new HashMap<>();
        for (Subject subject : subjectsByCode.values()) {
            canonicalByCode.put(key(subject.getCode()), canonicalCode(subject, subjectsByCode));
        }

        Map<String, String> aliases = validateAndNormalizeSignals(request, subjectsByCode, canonicalByCode);
        Map<String, StudentGradeInput> grades = normalizeMap(request.getGradesBySubjectCode(), aliases);
        Map<String, String> strengths = normalizeMap(request.getSelfReportedStrengthBySubjectCode(), aliases);
        Map<String, Integer> confidence = normalizeMap(request.getConfidenceBySubjectCode(), aliases);
        Set<String> preferred = normalizeSet(request.getPreferredSubjectCodes(), aliases);
        Set<String> disliked = normalizeSet(request.getDislikedSubjectCodes(), aliases);
        Set<String> mandatory = subjectsByCode.values().stream()
                .filter(subject -> Boolean.TRUE.equals(subject.getIsMandatory()))
                .map(subject -> canonicalByCode.get(key(subject.getCode())))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Set<String>> effectiveSubjectsByGroup = groups.stream().map(group -> {
            Set<String> codes = group.subjects().stream()
                    .map(subject -> canonicalByCode.get(key(subject.getCode())))
                    .filter(code -> code != null)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            codes.addAll(mandatory);
            return codes;
        }).toList();

        List<String> combinationSearchCodes = effectiveSubjectsByGroup.stream()
                .flatMap(Set::stream).distinct().sorted().toList();
        List<CombinationProjection> candidateCombinations = combinationSearchCodes.isEmpty()
                ? List.of() : combinationRepository.findValidCombinations(combinationSearchCodes);
        List<String> candidateIds = candidateCombinations.stream()
                .map(CombinationProjection::getCombinationId).distinct().toList();
        List<CombinationComponentProjection> components = candidateIds.isEmpty()
                ? List.of() : combinationRepository.findComponentsByCombinationIds(candidateIds);
        Map<String, List<CombinationComponentProjection>> componentsByCombination = components.stream()
                .collect(Collectors.groupingBy(CombinationComponentProjection::getCombinationId));
        Map<String, List<CombinationLabelDto>> labelsByCombination = candidateIds.isEmpty()
                ? Map.of()
                : combinationRepository.findLabelsByCombinationIds(candidateIds).stream()
                        .collect(Collectors.groupingBy(
                                label -> label.getCombinationId(),
                                Collectors.mapping(label -> new CombinationLabelDto(
                                        label.getCode(), label.getSubjectDetails()), Collectors.toList())));

        Map<String, CombinationProjection> combinationById = candidateCombinations.stream()
                .collect(Collectors.toMap(CombinationProjection::getCombinationId, Function.identity()));
        List<List<String>> matchesByGroup = new ArrayList<>();
        for (Set<String> effectiveCodes : effectiveSubjectsByGroup) {
            List<String> matches = candidateIds.stream().filter(id -> {
                List<CombinationComponentProjection> parts = componentsByCombination.getOrDefault(id, List.of());
                // Aptitude requirements are separate admission components and cannot be inferred from a school group.
                return !parts.isEmpty()
                        && parts.stream().allMatch(part -> "SUBJECT".equals(part.getComponentType())
                                && part.getSubjectCode() != null
                                && effectiveCodes.contains(canonicalByCode.get(key(part.getSubjectCode()))));
            }).toList();
            matchesByGroup.add(matches);
        }

        String desiredProgram = trimToNull(request.getDesiredProgramName());
        String universityCode = trimToNull(request.getUniversityCode());
        String universityName = trimToNull(request.getUniversityName());
        Integer admissionYear = request.getAdmissionYear() == null || request.getAdmissionYear().isBlank()
                ? null : Integer.valueOf(request.getAdmissionYear().trim());
        String methodCode = trimToNull(request.getMethodCode());
        List<String> matchedCombinationIds = desiredProgram == null ? List.of()
                : matchesByGroup.stream().flatMap(List::stream).distinct().toList();
        Map<String, List<AdmissionPathway>> pathwaysByCombination = matchedCombinationIds.isEmpty()
                ? Map.of()
                : offerRepository.findLatestPathwaysByCombinationAndProgram(
                         matchedCombinationIds, desiredProgram, universityCode, universityName,
                         admissionYear, methodCode)
                        .stream().collect(Collectors.groupingBy(
                                AdmissionPathwayProjection::getCombinationId,
                                Collectors.mapping(SubjectGroupComparisonService::toPathway, Collectors.toList())));

        List<GroupComparison> comparisons = new ArrayList<>();
        for (int index = 0; index < groups.size(); index++) {
            SubjectGroupData group = groups.get(index);
            Set<String> groupCodes = effectiveSubjectsByGroup.get(index);
            List<SubjectDto> effectiveDtos = new ArrayList<>(group.subjects());
            Set<String> displayed = group.subjects().stream().map(s -> key(s.getCode())).collect(Collectors.toSet());
            subjectsByCode.values().stream()
                    .filter(subject -> Boolean.TRUE.equals(subject.getIsMandatory()))
                    .filter(subject -> displayed.add(key(subject.getCode())))
                    .map(subject -> new SubjectDto(subject.getCode(), subject.getName()))
                    .forEach(effectiveDtos::add);

            List<SubjectDto> preferredMatches = matches(preferred, groupCodes, effectiveDtos, canonicalByCode);
            List<SubjectDto> dislikedMatches = matches(disliked, groupCodes, effectiveDtos, canonicalByCode);
            List<GradeSignal> gradeSignals = effectiveDtos.stream()
                    .map(subject -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                            canonicalByCode.get(key(subject.getCode())), subject))
                    .filter(entry -> entry.getKey() != null && grades.containsKey(entry.getKey()))
                    .map(entry -> {
                        StudentGradeInput grade = grades.get(entry.getKey());
                        return new GradeSignal(entry.getValue().getCode(), entry.getValue().getName(),
                                grade.getValue(), grade.getScale(), trimToNull(grade.getAssessmentType()));
                    }).toList();
            List<ConfidenceSignal> confidenceSignals = effectiveDtos.stream()
                    .map(subject -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                            canonicalByCode.get(key(subject.getCode())), subject))
                    .filter(entry -> entry.getKey() != null && confidence.containsKey(entry.getKey()))
                    .map(entry -> new ConfidenceSignal(entry.getValue().getCode(), entry.getValue().getName(),
                            confidence.get(entry.getKey())))
                    .toList();
            List<StrengthSignal> strengthSignals = effectiveDtos.stream()
                    .map(subject -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                            canonicalByCode.get(key(subject.getCode())), subject))
                    .filter(entry -> entry.getKey() != null && strengths.containsKey(entry.getKey()))
                    .map(entry -> new StrengthSignal(entry.getValue().getCode(), entry.getValue().getName(),
                            strengths.get(entry.getKey())))
                    .toList();

            List<AdmissionCombinationMatch> combinationMatches = matchesByGroup.get(index).stream()
                    .map(id -> {
                        CombinationProjection combination = combinationById.get(id);
                        return new AdmissionCombinationMatch(id, combination.getCombinationDisplay(),
                                labelsByCombination.getOrDefault(id, List.of()),
                                pathwaysByCombination.getOrDefault(id, List.of()));
                    }).toList();
            List<SubjectDto> mandatoryDtos = subjectsByCode.values().stream()
                    .filter(subject -> Boolean.TRUE.equals(subject.getIsMandatory()))
                    .map(subject -> new SubjectDto(subject.getCode(), subject.getName()))
                    .sorted(Comparator.comparing(SubjectDto::getCode, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            comparisons.add(new GroupComparison(group.groupCode(), group.groupName(), effectiveDtos,
                    group.subjects(), mandatoryDtos,
                    preferredMatches, dislikedMatches, gradeSignals, strengthSignals, confidenceSignals,
                    combinationMatches));
        }
        comparisons.sort(Comparator.comparing(GroupComparison::groupCode, String.CASE_INSENSITIVE_ORDER));
        return new SubjectGroupComparisonResponse(normalizedSchoolCode, year, desiredProgram, comparisons);
    }

    private Map<String, String> validateAndNormalizeSignals(
            SubjectGroupComparisonRequest request,
            Map<String, Subject> subjectsByCode,
            Map<String, String> canonicalByCode) {
        Set<String> supplied = new LinkedHashSet<>();
        request.getPreferredSubjectCodes().forEach(code -> supplied.add(key(code)));
        request.getDislikedSubjectCodes().forEach(code -> supplied.add(key(code)));
        request.getGradesBySubjectCode().keySet().forEach(code -> supplied.add(key(code)));
        request.getSelfReportedStrengthBySubjectCode().keySet().forEach(code -> supplied.add(key(code)));
        request.getConfidenceBySubjectCode().keySet().forEach(code -> supplied.add(key(code)));
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String code : supplied) {
            String canonical = canonicalByCode.get(code);
            if (canonical == null || !subjectsByCode.containsKey(code)) {
                throw new InvalidRecommendationRequestException("Ma mon hoc khong ton tai trong danh muc: " + code);
            }
            normalized.put(code, canonical);
        }
        request.getGradesBySubjectCode().forEach((code, grade) -> {
            if (grade.getValue().compareTo(grade.getScale()) > 0) {
                throw new InvalidRecommendationRequestException("Diem hoc tap vuot qua thang diem cua mon: " + code);
            }
        });
        return normalized;
    }

    private static List<SubjectDto> matches(
            Set<String> requested,
            Set<String> groupCodes,
            List<SubjectDto> effectiveSubjects,
            Map<String, String> canonicalByCode) {
        Set<String> matched = requested.stream().filter(groupCodes::contains).collect(Collectors.toSet());
        return effectiveSubjects.stream()
                .filter(subject -> matched.contains(canonicalByCode.get(key(subject.getCode()))))
                .toList();
    }

    private static <T> Map<String, T> normalizeMap(Map<String, T> input, Map<String, String> aliases) {
        Map<String, T> result = new LinkedHashMap<>();
        input.forEach((code, value) -> result.put(aliases.get(key(code)), value));
        return result;
    }

    private static Set<String> normalizeSet(List<String> input, Map<String, String> aliases) {
        return input.stream().map(code -> aliases.get(key(code)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String canonicalCode(Subject subject, Map<String, Subject> subjectsByCode) {
        String code = subject.getCode();
        Set<String> visited = new HashSet<>();
        while (subject.getCanonicalSubjectCode() != null && visited.add(key(code))) {
            code = subject.getCanonicalSubjectCode();
            subject = subjectsByCode.get(key(code));
            if (subject == null) break;
        }
        return code;
    }

    private static AdmissionPathway toPathway(AdmissionPathwayProjection pathway) {
        return new AdmissionPathway(pathway.getUniversityCode(), pathway.getUniversityName(),
                pathway.getProgramCode(), pathway.getProgramName(), pathway.getAdmissionYear(),
                pathway.getMethodCodes(), pathway.getSourceUrl());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
