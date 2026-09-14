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
    public List<SubjectGroupDto> getSubjectGroupsBySchool(String schoolCode) {
        String normalizedCode = schoolCode == null ? "" : schoolCode.trim();

        if (!highSchoolRepository.existsById(normalizedCode)) {
            throw new ResourceNotFoundException(
                    "Truong THPT khong ton tai voi ma: " + normalizedCode);
        }

        Map<String, List<SubjectGroupFlatProjection>> groupedByCode =
                subjectGroupRepository.findSubjectGroupsBySchool(normalizedCode).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                SubjectGroupFlatProjection::getGroupCode,
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));

        return groupedByCode.entrySet().stream()
                .map(entry -> {
                    List<SubjectGroupFlatProjection> rows = entry.getValue();
                    List<SubjectDto> subjects = rows.stream()
                            .map(row -> new SubjectDto(
                                    row.getSubjectCode(), row.getSubjectName()))
                            .toList();
                    String groupName = rows.get(0).getGroupName();
                    return new SubjectGroupDto(entry.getKey(), groupName, subjects);
                })
                .toList();
    }
}
