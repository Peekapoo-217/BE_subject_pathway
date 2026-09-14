package vn.edu.thesis.BE_subject_pathway.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectSearchRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectSearchResponse;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionCombinationRepository;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionOfferRepository;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository;
import vn.edu.thesis.BE_subject_pathway.repository.projection.SearchStatsProjection;
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

    @Override
    public SubjectSearchResponse searchBySubjects(SubjectSearchRequest request) {
        List<String> requestedCodes = request.getSubjectCodes().stream()
                .map(String::trim)
                .toList();

        List<String> mandatoryCodes = subjectRepository.findCodesByIsMandatoryTrue().stream()
                .map(String::trim)
                .toList();

        // Union mon client chon + mon bat buoc (hoc sinh luon co mon bat buoc
        // ngoai cac mon tu chon); LinkedHashSet loai trung lap va giu thu tu.
        Set<String> effectiveCodes = new LinkedHashSet<>(requestedCodes);
        effectiveCodes.addAll(mandatoryCodes);

        List<CombinationDto> combinations = combinationRepository
                .findValidCombinations(new ArrayList<>(effectiveCodes)).stream()
                .map(p -> new CombinationDto(
                        p.getCombinationId(),
                        p.getCombinationDisplay(),
                        p.getComponentCount()))
                .toList();

        if (combinations.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Khong tim thay to hop xet tuyen nao phu hop voi cac mon hoc da chon");
        }

        List<String> combinationIds = combinations.stream()
                .map(CombinationDto::getCombinationId)
                .toList();

        SearchStatsProjection stats =
                offerRepository.countMajorsAndUniversities(
                        combinationIds, METHOD_CODE_THPT);

        return new SubjectSearchResponse(
                combinations.size(),
                combinations,
                stats.getTotalMajors(),
                stats.getTotalUniversities());
    }
}
