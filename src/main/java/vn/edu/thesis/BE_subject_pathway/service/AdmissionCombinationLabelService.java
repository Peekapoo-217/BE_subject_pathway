package vn.edu.thesis.BE_subject_pathway.service;

import java.util.ArrayList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationResolveResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationLabelsResponse;
import vn.edu.thesis.BE_subject_pathway.entity.Subject;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionCombinationRepository;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationComponentProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationProjection;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionCombinationLabelService {
    private final AdmissionCombinationRepository repository;
    private final SubjectRepository subjectRepository;

    public CombinationLabelsResponse findLatestLabels(String code) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedCode.isBlank() || normalizedCode.length() > 100
                || !normalizedCode.matches("[\\p{L}\\p{N}_-]+")) {
            throw new IllegalArgumentException("Ma to hop khong hop le");
        }
        var labels = repository.findLatestLabelsByCode(normalizedCode).stream()
                .map(row -> new CombinationLabelsResponse.Label(
                        row.getCode(), row.getSubjectDetails(), row.getCanonicalCombinationId(),
                        row.getSourceUrl(), row.getSourcePeriod(), toUtc(row.getRetrievedAt())))
                .toList();
        if (labels.isEmpty()) {
            throw new ResourceNotFoundException("Khong tim thay ma to hop trong danh muc");
        }
        return new CombinationLabelsResponse(normalizedCode, labels);
    }

    public CombinationResolveResponse resolveExactSubjectSet(List<String> subjectCodes) {
        if (subjectCodes == null || subjectCodes.size() < 2 || subjectCodes.size() > 4) {
            throw new IllegalArgumentException("Can cung cap tu 2 den 4 ma mon hoc");
        }
        Map<String, Subject> subjectsByCode = subjectRepository.findAll().stream()
                .collect(Collectors.toMap(subject -> key(subject.getCode()), Function.identity(), (a, b) -> a));
        Map<String, String> canonicalByCode = canonicalCodes(subjectsByCode);
        Set<String> requestedCanonicalCodes = new LinkedHashSet<>();
        for (String rawCode : subjectCodes) {
            String code = key(rawCode);
            String canonical = canonicalByCode.get(code);
            if (code.isBlank() || canonical == null) {
                throw new IllegalArgumentException("Ma mon hoc khong ton tai trong danh muc: " + code);
            }
            if (!requestedCanonicalCodes.add(canonical)) {
                throw new IllegalArgumentException("Danh sach mon hoc co ma trung sau khi chuan hoa");
            }
        }
        List<String> acceptedAliases = canonicalByCode.entrySet().stream()
                .filter(entry -> requestedCanonicalCodes.contains(entry.getValue()))
                .map(Map.Entry::getKey).sorted().toList();
        List<CombinationProjection> candidates = repository.findExactSubjectCombinations(
                acceptedAliases, requestedCanonicalCodes.size());
        if (candidates.isEmpty()) {
            return new CombinationResolveResponse(List.of(),
                    "Exact subject-only matches in the current catalogue; an empty result does not prove global non-existence.");
        }
        List<String> candidateIds = candidates.stream().map(CombinationProjection::getCombinationId).distinct().toList();
        Map<String, List<CombinationComponentProjection>> componentsById = repository
                .findComponentsByCombinationIds(candidateIds).stream()
                .collect(Collectors.groupingBy(CombinationComponentProjection::getCombinationId));
        List<CombinationProjection> exact = candidates.stream()
                .filter(candidate -> exactSubjectSet(
                        componentsById.getOrDefault(candidate.getCombinationId(), List.of()),
                        requestedCanonicalCodes, canonicalByCode))
                .toList();
        if (exact.isEmpty()) {
            return new CombinationResolveResponse(List.of(),
                    "Exact subject-only matches in the current catalogue; an empty result does not prove global non-existence.");
        }
        List<String> exactIds = exact.stream().map(CombinationProjection::getCombinationId).toList();
        Map<String, List<CombinationResolveResponse.Label>> labelsById = repository
                .findLatestCatalogueLabelsByCombinationIds(exactIds).stream()
                .collect(Collectors.groupingBy(
                        row -> row.getCanonicalCombinationId(),
                        LinkedHashMap::new,
                        Collectors.mapping(row -> new CombinationResolveResponse.Label(
                                row.getCode(), row.getSubjectDetails(), row.getSourceUrl(),
                                row.getSourcePeriod(), toUtc(row.getRetrievedAt())), Collectors.toList())));
        List<CombinationResolveResponse.Match> matches = exact.stream().map(candidate -> {
            List<CombinationResolveResponse.Component> components = componentsById
                    .getOrDefault(candidate.getCombinationId(), List.of()).stream()
                    .map(component -> new CombinationResolveResponse.Component(
                            component.getComponentType(), component.getComponentCode(),
                            component.getComponentName(), component.getSubjectCode()))
                    .toList();
            return new CombinationResolveResponse.Match(candidate.getCombinationId(),
                    candidate.getCombinationDisplay(), candidate.getComponentCount(), components,
                    labelsById.getOrDefault(candidate.getCombinationId(), List.of()));
        }).toList();
        return new CombinationResolveResponse(matches,
                "Exact subject-only matches in the current catalogue; an empty result does not prove global non-existence.");
    }

    private static Map<String, String> canonicalCodes(Map<String, Subject> subjectsByCode) {
        Map<String, String> result = new HashMap<>();
        for (Subject subject : subjectsByCode.values()) {
            String current = key(subject.getCode());
            Set<String> visited = new HashSet<>();
            while (visited.add(current)) {
                Subject row = subjectsByCode.get(current);
                String next = row == null ? null : key(row.getCanonicalSubjectCode());
                if (next == null || next.isBlank() || !subjectsByCode.containsKey(next)) break;
                current = next;
            }
            result.put(key(subject.getCode()), current);
        }
        return result;
    }

    private static boolean exactSubjectSet(
            List<CombinationComponentProjection> components,
            Set<String> requestedCanonicalCodes,
            Map<String, String> canonicalByCode) {
        if (components.size() != requestedCanonicalCodes.size()) return false;
        Set<String> actual = new LinkedHashSet<>();
        for (CombinationComponentProjection component : components) {
            if (!"SUBJECT".equals(component.getComponentType()) || component.getSubjectCode() == null) return false;
            String canonical = canonicalByCode.get(key(component.getSubjectCode()));
            if (canonical == null || !actual.add(canonical)) return false;
        }
        return actual.equals(requestedCanonicalCodes);
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static OffsetDateTime toUtc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
