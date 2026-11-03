package vn.edu.thesis.BE_subject_pathway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.thesis.BE_subject_pathway.dto.request.StudentGradeInput;
import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectGroupComparisonRequest;
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
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationLabelProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationProjection;

class SubjectGroupComparisonServiceTest {

    private final HighSchoolRepository schools = mock(HighSchoolRepository.class);
    private final Neo4jSubjectGroupRepository graph = mock(Neo4jSubjectGroupRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final AdmissionCombinationRepository combinations = mock(AdmissionCombinationRepository.class);
    private final AdmissionOfferRepository offers = mock(AdmissionOfferRepository.class);
    private final SubjectGroupComparisonService service =
            new SubjectGroupComparisonService(schools, graph, subjects, combinations, offers);

    @Test
    void comparesAllPublishedGroupsWithoutAnAggregateScoreAndKeepsGradeAndPreferenceSeparate() {
        arrangePublishedGroups();
        List<CombinationProjection> candidateRows = List.of(
                combination("MATH_PHYSICS_LITERATURE", "Toán, Vật lí, Ngữ văn"),
                combination("MATH_CHEMISTRY_LITERATURE", "Toán, Hóa học, Ngữ văn"));
        List<CombinationComponentProjection> componentRows = List.of(
                component("MATH_PHYSICS_LITERATURE", "MATH"),
                component("MATH_PHYSICS_LITERATURE", "PHYSICS"),
                component("MATH_PHYSICS_LITERATURE", "LITERATURE"),
                component("MATH_CHEMISTRY_LITERATURE", "MATH"),
                component("MATH_CHEMISTRY_LITERATURE", "CHEMISTRY"),
                component("MATH_CHEMISTRY_LITERATURE", "LITERATURE"));
        List<CombinationLabelProjection> labelRows = List.of(
                label("MATH_PHYSICS_LITERATURE", "A01"),
                label("MATH_PHYSICS_LITERATURE", "X26"));
        AdmissionPathwayProjection path = pathway("MATH_PHYSICS_LITERATURE", 2025);
        when(combinations.findValidCombinations(anyList())).thenReturn(candidateRows);
        when(combinations.findComponentsByCombinationIds(anyList())).thenReturn(componentRows);
        when(combinations.findLabelsByCombinationIds(anyList())).thenReturn(labelRows);
        when(offers.findLatestPathwaysByCombinationAndProgram(
                anyList(), eq("Computer Science"), eq(null), eq(null), eq(2025), eq("100")))
                .thenReturn(List.of(path));

        SubjectGroupComparisonRequest request = new SubjectGroupComparisonRequest();
        request.setPreferredSubjectCodes(List.of("LITERATURE"));
        request.setDislikedSubjectCodes(List.of("CHEMISTRY"));
        request.setGradesBySubjectCode(Map.of("MATH", grade("9", "10", "THCS")));
        request.setDesiredProgramName("Computer Science");
        request.setAdmissionYear("2025");
        request.setMethodCode("100");

        var response = service.compare("SCHOOL", request);

        assertEquals("2026-2027", response.academicYear());
        assertEquals(2, response.groups().size());
        var physicsGroup = response.groups().get(1);
        assertEquals("G02", physicsGroup.groupCode());
        assertEquals(List.of("LITERATURE"), physicsGroup.matchedPreferredSubjects().stream()
                .map(subject -> subject.getCode()).toList());
        assertEquals(List.of("CHEMISTRY"), physicsGroup.matchedDislikedSubjects().stream()
                .map(subject -> subject.getCode()).toList());
        assertEquals(new BigDecimal("9"), physicsGroup.gradeSignals().get(0).value());

        var scienceGroup = response.groups().get(0);
        assertEquals("G01", scienceGroup.groupCode());
        assertEquals(List.of("PHYSICS"), scienceGroup.groupSubjects().stream()
                .map(subject -> subject.getCode()).toList());
        assertEquals(List.of("LITERATURE", "MATH"), scienceGroup.mandatorySubjects().stream()
                .map(subject -> subject.getCode()).toList());
        assertEquals(List.of("LITERATURE", "MATH", "PHYSICS"), scienceGroup.subjects().stream()
                .map(subject -> subject.getCode()).sorted().toList());
        assertEquals(1, scienceGroup.matchingAdmissionCombinations().size());
        assertEquals(List.of("A01", "X26"), scienceGroup.matchingAdmissionCombinations().get(0)
                .publicLabels().stream().map(label -> label.getCode()).toList());
        assertEquals(1, scienceGroup.matchingAdmissionCombinations().get(0).pathwaysForRequestedProgram().size());
        assertTrue(response.desiredProgramQuery().contains("Computer Science"));
    }

    @Test
    void doesNotClaimAnAdmissionPathWhenNoRealOfferExists() {
        arrangePublishedGroups();
        when(combinations.findValidCombinations(anyList())).thenReturn(List.of());

        var response = service.compare("SCHOOL", new SubjectGroupComparisonRequest());

        assertTrue(response.groups().stream().allMatch(group -> group.matchingAdmissionCombinations().isEmpty()));
        verify(offers, never()).findLatestPathwaysByCombinationAndProgram(
                anyList(), eq(""), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void rejectsAReportedGradeOutsideItsScale() {
        arrangePublishedGroups();
        SubjectGroupComparisonRequest request = new SubjectGroupComparisonRequest();
        request.setGradesBySubjectCode(Map.of("MATH", grade("11", "10", "THCS")));

        assertThrows(InvalidRecommendationRequestException.class, () -> service.compare("SCHOOL", request));
    }

    @Test
    void rejectsDeletedSchoolBeforeReadingGraphData() {
        when(schools.existsByCodeAndDeletedAtIsNull("SCHOOL")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.compare("SCHOOL", new SubjectGroupComparisonRequest()));
        verify(graph, never()).findAcademicYears("SCHOOL");
    }

    private void arrangePublishedGroups() {
        when(schools.existsByCodeAndDeletedAtIsNull("SCHOOL")).thenReturn(true);
        when(graph.findAcademicYears("SCHOOL")).thenReturn(List.of("2026-2027", "2025-2026"));
        when(graph.findSubjectGroups("SCHOOL", "2026-2027")).thenReturn(List.of(
                new SubjectGroupData("G01", "Nhóm 1", "2026-2027", List.of(
                        new vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto("PHYSICS", "Vật lí"))),
                new SubjectGroupData("G02", "Nhóm 2", "2026-2027", List.of(
                        new vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto("CHEMISTRY", "Hóa học")))));
        when(subjects.findAll()).thenReturn(List.of(
                subject("MATH", "Toán", true), subject("LITERATURE", "Ngữ văn", true),
                subject("PHYSICS", "Vật lí", false), subject("CHEMISTRY", "Hóa học", false)));
    }

    private static Subject subject(String code, String name, boolean mandatory) {
        return Subject.builder().code(code).name(name).isMandatory(mandatory).build();
    }

    private static StudentGradeInput grade(String value, String scale, String type) {
        StudentGradeInput input = new StudentGradeInput();
        input.setValue(new BigDecimal(value));
        input.setScale(new BigDecimal(scale));
        input.setAssessmentType(type);
        return input;
    }

    private static CombinationProjection combination(String id, String display) {
        CombinationProjection result = mock(CombinationProjection.class);
        when(result.getCombinationId()).thenReturn(id);
        when(result.getCombinationDisplay()).thenReturn(display);
        when(result.getComponentCount()).thenReturn((short) 3);
        return result;
    }

    private static CombinationComponentProjection component(String id, String code) {
        CombinationComponentProjection result = mock(CombinationComponentProjection.class);
        when(result.getCombinationId()).thenReturn(id);
        when(result.getComponentType()).thenReturn("SUBJECT");
        when(result.getComponentCode()).thenReturn(code);
        when(result.getComponentName()).thenReturn(code);
        when(result.getSubjectCode()).thenReturn(code);
        return result;
    }

    private static CombinationLabelProjection label(String id, String code) {
        CombinationLabelProjection result = mock(CombinationLabelProjection.class);
        when(result.getCombinationId()).thenReturn(id);
        when(result.getCode()).thenReturn(code);
        when(result.getSubjectDetails()).thenReturn("Toán, môn tự chọn");
        return result;
    }

    private static AdmissionPathwayProjection pathway(String combinationId, int year) {
        AdmissionPathwayProjection result = mock(AdmissionPathwayProjection.class);
        when(result.getCombinationId()).thenReturn(combinationId);
        when(result.getUniversityCode()).thenReturn("U1");
        when(result.getUniversityName()).thenReturn("Đại học 1");
        when(result.getProgramCode()).thenReturn("CS");
        when(result.getProgramName()).thenReturn("Computer Science");
        when(result.getAdmissionYear()).thenReturn((short) year);
        when(result.getMethodCodes()).thenReturn("100");
        when(result.getSourceUrl()).thenReturn("https://example.test/admissions");
        return result;
    }
}
