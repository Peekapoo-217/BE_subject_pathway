package vn.edu.thesis.BE_subject_pathway.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectSearchRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationLabelDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.MajorDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectSearchResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.UniversityDto;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionCombinationRepository;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionOfferRepository;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository;
import vn.edu.thesis.BE_subject_pathway.repository.HighSchoolRepository;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectGroupRepository;
import vn.edu.thesis.BE_subject_pathway.repository.projection.SearchStatsProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationProjection;
import vn.edu.thesis.BE_subject_pathway.service.AdmissionSearchService;

/**
 * Business logic tra cuu xet tuyen theo mon hoc.
 * Toan bo tinh toan (dem, group, filter) thuc hien trong DB qua
 * native query; tang Java chi dieu phoi va map DTO.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionSearchServiceImpl implements AdmissionSearchService {

    /** Ma phuong thuc xet tuyen theo thpt (method mac dinh can tra cuu). */
    private static final String METHOD_CODE_THPT = "100";

    private final AdmissionCombinationRepository combinationRepository;
    private final AdmissionOfferRepository offerRepository;
    private final SubjectRepository subjectRepository;
    private final HighSchoolRepository highSchoolRepository;
    private final SubjectGroupRepository subjectGroupRepository;

    @Override
    public SubjectSearchResponse searchBySubjects(SubjectSearchRequest request) {
        List<String> requestedCodes = request.getSubjectCodes().stream()
                .map(String::trim)
                .toList();

        validateSchoolGroup(request, requestedCodes);

        List<String> mandatoryCodes = subjectRepository.findCodesByIsMandatoryTrue().stream()
                .map(String::trim)
                .toList();

        // Union mon client chon + mon bat buoc (hoc sinh luon co mon bat buoc
        // ngoai cac mon tu chon); LinkedHashSet loai trung lap va giu thu tu.
        Set<String> effectiveCodes = new LinkedHashSet<>(requestedCodes);
        effectiveCodes.addAll(mandatoryCodes);

        List<CombinationProjection> matchedCombinations = combinationRepository
                .findValidCombinations(new ArrayList<>(effectiveCodes));

        if (matchedCombinations.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Khong tim thay to hop xet tuyen nao phu hop voi cac mon hoc da chon");
        }

        List<String> combinationIds = matchedCombinations.stream()
                .map(CombinationProjection::getCombinationId)
                .toList();

        Map<String, List<CombinationLabelDto>> labelsByCombination = combinationRepository
                .findLabelsByCombinationIds(combinationIds).stream()
                .collect(Collectors.groupingBy(
                        label -> label.getCombinationId(),
                        Collectors.mapping(
                                label -> new CombinationLabelDto(
                                        label.getCode(), label.getSubjectDetails()),
                                Collectors.toList())));
        List<CombinationDto> combinations = matchedCombinations.stream()
                .map(combination -> new CombinationDto(
                        combination.getCombinationId(),
                        combination.getCombinationDisplay(),
                        combination.getComponentCount(),
                        labelsByCombination.getOrDefault(
                                combination.getCombinationId(), List.of())))
                .toList();

        SearchStatsProjection stats =
                offerRepository.countMajorsAndUniversities(
                        combinationIds, METHOD_CODE_THPT);
        List<UniversityDto> universities = offerRepository.findUniversitiesByCombinations(
                        combinationIds, METHOD_CODE_THPT).stream()
                .map(university -> new UniversityDto(
                        university.getUniversityCode(), university.getUniversityName()))
                .toList();
        String universityCode = request.getUniversityCode();
        List<MajorDto> majors = universityCode == null ? List.of() :
                offerRepository.findMajorsByCombinationsAndUniversity(
                        combinationIds, METHOD_CODE_THPT, universityCode.trim()).stream()
                .map(major -> new MajorDto(major.getProgramCode(), major.getProgramName()))
                .toList();

        return new SubjectSearchResponse(
                combinations.size(),
                combinations,
                stats.getTotalMajors(),
                stats.getTotalUniversities(),
                universities,
                majors);
    }

    private void validateSchoolGroup(SubjectSearchRequest request, List<String> requestedCodes) {
        if (!request.isSchoolGroupContextComplete()) {
            throw new IllegalArgumentException("Can gui day du schoolCode, academicYear va groupCode");
        }
        if (request.getSchoolCode() == null) return;
        if (!highSchoolRepository.existsByCodeAndDeletedAtIsNull(request.getSchoolCode())) {
            throw new ResourceNotFoundException("Truong THPT khong ton tai");
        }
        Set<String> groupSubjects = subjectGroupRepository
                .findSubjectGroupsBySchool(request.getSchoolCode(), request.getAcademicYear()).stream()
                .filter(row -> request.getGroupCode().equals(row.getGroupCode()))
                .map(row -> row.getSubjectCode().trim())
                .collect(Collectors.toSet());
        if (groupSubjects.isEmpty()) {
            throw new ResourceNotFoundException("Khong co nhom mon dang cong bo cho truong va nam hoc da chon");
        }
        if (!groupSubjects.equals(new LinkedHashSet<>(requestedCodes))) {
            throw new IllegalArgumentException("Cac mon khong khop nhom mon trong nam hoc da chon; hay tai lai nhom mon");
        }
    }
}
