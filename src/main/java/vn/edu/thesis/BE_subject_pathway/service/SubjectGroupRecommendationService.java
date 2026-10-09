package vn.edu.thesis.BE_subject_pathway.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectGroupRecommendationRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupRecommendationDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupRecommendationsResponse;
import vn.edu.thesis.BE_subject_pathway.exception.InvalidRecommendationRequestException;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.HighSchoolRepository;
import vn.edu.thesis.BE_subject_pathway.repository.Neo4jSubjectGroupRepository;
import vn.edu.thesis.BE_subject_pathway.repository.Neo4jSubjectGroupRepository.SubjectGroupData;

/** Xep hang minh bach cac nhom 4 mon theo so thich va tu tin tu khai bao. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectGroupRecommendationService {

    private static final double PREFERENCE_WEIGHT = 0.60;
    private static final double CONFIDENCE_WEIGHT = 0.40;
    private static final int NEUTRAL_CONFIDENCE = 3;

    private final HighSchoolRepository highSchoolRepository;
    private final Neo4jSubjectGroupRepository graphRepository;

    public SubjectGroupRecommendationsResponse recommend(
            String schoolCode,
            SubjectGroupRecommendationRequest request) {
        String normalizedSchoolCode = schoolCode == null ? "" : schoolCode.trim();
        String academicYear = request.getAcademicYear().trim();

        if (!highSchoolRepository.existsById(normalizedSchoolCode)) {
            throw new ResourceNotFoundException("Truong THPT khong ton tai voi ma: " + normalizedSchoolCode);
        }

        List<String> academicYears = graphRepository.findAcademicYears(normalizedSchoolCode);
        if (!academicYears.contains(academicYear)) {
            throw new ResourceNotFoundException("Khong co du lieu nhom mon cho nam hoc: " + academicYear);
        }

        List<SubjectGroupData> groups = graphRepository.findFourSubjectGroups(normalizedSchoolCode, academicYear);
        if (groups.isEmpty()) {
            return new SubjectGroupRecommendationsResponse(
                    normalizedSchoolCode, academicYear, request.getTopK(), 0, List.of());
        }

        Set<String> availableCodes = groups.stream()
                .flatMap(group -> group.subjects().stream())
                .map(SubjectDto::getCode)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> preferredCodes = normalizeCodes(request.getPreferredSubjectCodes());
        Set<String> confidenceCodes = normalizeCodes(request.getConfidenceBySubjectCode().keySet());
        Map<String, Integer> normalizedConfidence = request.getConfidenceBySubjectCode().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().trim().toUpperCase(Locale.ROOT),
                        Map.Entry::getValue, (first, second) -> second));

        validateCodes("Mon yeu thich", preferredCodes, availableCodes);
        validateCodes("Mon duoc danh gia", confidenceCodes, availableCodes);
        if (preferredCodes.isEmpty() && confidenceCodes.isEmpty()) {
            throw new InvalidRecommendationRequestException(
                    "Hay chon it nhat mot mon yeu thich hoac danh gia muc tu tin.");
        }

        List<SubjectGroupRecommendationDto> ranked = groups.stream()
                .map(group -> score(group, preferredCodes, normalizedConfidence))
                .sorted(Comparator.comparingDouble(SubjectGroupRecommendationDto::getScore).reversed()
                        .thenComparing((SubjectGroupRecommendationDto result) -> result.getMatchedPreferredSubjects().size(),
                                Comparator.reverseOrder())
                        .thenComparing(SubjectGroupRecommendationDto::getGroupCode))
                .limit(request.getTopK())
                .toList();

        return new SubjectGroupRecommendationsResponse(
                normalizedSchoolCode, academicYear, request.getTopK(), groups.size(), ranked);
    }

    private SubjectGroupRecommendationDto score(
            SubjectGroupData group,
            Set<String> preferredCodes,
            Map<String, Integer> confidenceByCode) {
        List<SubjectDto> matchedPreferred = group.subjects().stream()
                .filter(subject -> preferredCodes.contains(subject.getCode().toUpperCase(Locale.ROOT)))
                .toList();
        List<SubjectDto> lowConfidence = group.subjects().stream()
                .filter(subject -> confidenceByCode.getOrDefault(subject.getCode().toUpperCase(Locale.ROOT), NEUTRAL_CONFIDENCE) <= 2)
                .toList();

        double preferenceScore = preferredCodes.isEmpty()
                ? 0.0
                : (double) matchedPreferred.size() / preferredCodes.size();
        double confidenceScore = confidenceByCode.isEmpty()
                ? 0.0
                : group.subjects().stream()
                        .mapToDouble(subject -> (confidenceByCode.getOrDefault(
                                subject.getCode().toUpperCase(Locale.ROOT), NEUTRAL_CONFIDENCE) - 1) / 4.0)
                        .average()
                        .orElse(0.5);

        double weightedScore;
        if (!preferredCodes.isEmpty() && !confidenceByCode.isEmpty()) {
            weightedScore = PREFERENCE_WEIGHT * preferenceScore + CONFIDENCE_WEIGHT * confidenceScore;
        } else {
            weightedScore = preferredCodes.isEmpty() ? confidenceScore : preferenceScore;
        }

        List<String> explanations = new ArrayList<>();
        if (!preferredCodes.isEmpty()) {
            explanations.add("Khớp " + matchedPreferred.size() + "/" + preferredCodes.size()
                    + " môn em yêu thích.");
        }
        if (!confidenceByCode.isEmpty()) {
            double averageConfidence = group.subjects().stream()
                    .mapToInt(subject -> confidenceByCode.getOrDefault(
                            subject.getCode().toUpperCase(Locale.ROOT), NEUTRAL_CONFIDENCE))
                    .average()
                    .orElse(NEUTRAL_CONFIDENCE);
                    explanations.add("Mức tự tin trung bình với nhóm: " + String.format(Locale.ROOT, "%.1f/5", averageConfidence)
                    + "; môn chưa đánh giá được tính trung tính 3/5.");
        }
        if (!lowConfidence.isEmpty()) {
            String names = lowConfidence.stream().map(SubjectDto::getName).collect(Collectors.joining(", "));
            explanations.add("Em có thể muốn tìm hiểu thêm về: " + names + ".");
        }

        return new SubjectGroupRecommendationDto(
                group.groupCode(),
                group.groupName(),
                group.academicYear(),
                group.subjects(),
                Math.round(weightedScore * 1000.0) / 10.0,
                matchedPreferred,
                lowConfidence,
                explanations);
    }

    private Set<String> normalizeCodes(Iterable<String> codes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (codes != null) {
            for (String code : codes) {
                if (code != null && !code.isBlank()) {
                    normalized.add(code.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return normalized;
    }

    private void validateCodes(String label, Set<String> requestedCodes, Set<String> availableCodes) {
        Set<String> unknown = new LinkedHashSet<>(requestedCodes);
        unknown.removeAll(availableCodes);
        if (!unknown.isEmpty()) {
            throw new InvalidRecommendationRequestException(
                    label + " khong co trong danh muc cua truong/nam hoc da chon: " + String.join(", ", unknown));
        }
    }
}
