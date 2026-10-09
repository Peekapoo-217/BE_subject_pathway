package vn.edu.thesis.BE_subject_pathway.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupDto;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.HighSchoolRepository;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectGroupRepository;
import vn.edu.thesis.BE_subject_pathway.repository.projection.SubjectGroupFlatProjection;
import vn.edu.thesis.BE_subject_pathway.service.HighSchoolService;

/**
 * Business logic tra cuu truong THPT va nhom mon hoc.
 * Gom nhom du lieu flat tu native query thuc hien tai Service
 * bang mot lan duyet Stream duy nhat (rule layered-architecture).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HighSchoolServiceImpl implements HighSchoolService {

    private final HighSchoolRepository highSchoolRepository;
    private final SubjectGroupRepository subjectGroupRepository;

    @Override
    public List<HighSchoolDto> getAllHighSchools() {
        return highSchoolRepository.findAllByOrderByCodeAsc().stream()
                .map(school -> new HighSchoolDto(school.getCode(), school.getName()))
                .toList();
    }

    @Override
    public List<String> getAcademicYearsBySchool(String schoolCode) {
        String normalizedCode = normalizeSchoolCode(schoolCode);
        ensureSchoolExists(normalizedCode);
        return subjectGroupRepository.findAcademicYearsBySchool(normalizedCode);
    }

    @Override
    public List<SubjectGroupDto> getSubjectGroupsBySchool(String schoolCode) {
        String normalizedCode = normalizeSchoolCode(schoolCode);
        ensureSchoolExists(normalizedCode);
        List<String> years = subjectGroupRepository.findAcademicYearsBySchool(normalizedCode);
        if (years.isEmpty()) {
            // Compatibility fallback for legacy repository implementations; real catalog rows
            // always yield at least one year from the same PostgreSQL table.
            return toSubjectGroups(subjectGroupRepository.findSubjectGroupsBySchool(normalizedCode));
        }
        return getSubjectGroupsBySchool(normalizedCode, years.get(0));
    }

    @Override
    public List<SubjectGroupDto> getSubjectGroupsBySchool(String schoolCode, String academicYear) {
        String normalizedCode = normalizeSchoolCode(schoolCode);
        String normalizedYear = academicYear == null ? "" : academicYear.trim();
        ensureSchoolExists(normalizedCode);
        if (normalizedYear.isBlank()) {
            throw new ResourceNotFoundException("Nam hoc khong duoc de trong");
        }

        return toSubjectGroups(subjectGroupRepository.findSubjectGroupsBySchool(normalizedCode, normalizedYear));
    }

    private List<SubjectGroupDto> toSubjectGroups(List<SubjectGroupFlatProjection> rows) {
        Map<String, List<SubjectGroupFlatProjection>> groupedByYearAndCode = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        row -> String.valueOf(row.getAcademicYear()) + "|" + row.getGroupCode(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        return groupedByYearAndCode.values().stream()
                .map(groupRows -> {
                    List<SubjectDto> subjects = groupRows.stream()
                            .map(row -> new SubjectDto(
                                    row.getSubjectCode(), row.getSubjectName()))
                            .toList();
                    String groupName = groupRows.get(0).getGroupName();
                    return new SubjectGroupDto(
                            groupRows.get(0).getAcademicYear(), groupRows.get(0).getGroupCode(), groupName, subjects);
                })
                .toList();
    }

    private String normalizeSchoolCode(String schoolCode) {
        return schoolCode == null ? "" : schoolCode.trim();
    }

    private void ensureSchoolExists(String schoolCode) {
        if (!highSchoolRepository.existsById(schoolCode)) {
            throw new ResourceNotFoundException(
                    "Truong THPT khong ton tai voi ma: " + schoolCode);
        }
    }
}
