package vn.edu.thesis.BE_subject_pathway.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectClassificationResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto;
import vn.edu.thesis.BE_subject_pathway.entity.Subject;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository;
import vn.edu.thesis.BE_subject_pathway.service.SubjectService;

/**
 * Trien khai business logic tra cuu va phan loai mon hoc.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;

    @Override
    public SubjectClassificationResponse getSubjectClassifications() {
        List<Subject> rootSubjects = subjectRepository.findRootSubjects();

        Map<Boolean, List<SubjectDto>> partitioned = rootSubjects.stream()
                .collect(Collectors.partitioningBy(
                        s -> Boolean.TRUE.equals(s.getIsMandatory()),
                        Collectors.mapping(
                                s -> new SubjectDto(s.getCode(), s.getName()),
                                Collectors.toList()
                        )
                ));

        List<SubjectDto> mandatorySubjects = partitioned.getOrDefault(true, List.of());
        List<SubjectDto> electiveSubjects = partitioned.getOrDefault(false, List.of());

        return new SubjectClassificationResponse(mandatorySubjects, electiveSubjects);
    }
}
