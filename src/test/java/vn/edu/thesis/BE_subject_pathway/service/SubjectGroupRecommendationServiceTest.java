package vn.edu.thesis.BE_subject_pathway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectGroupRecommendationRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto;
import vn.edu.thesis.BE_subject_pathway.repository.HighSchoolRepository;
import vn.edu.thesis.BE_subject_pathway.repository.Neo4jSubjectGroupRepository;
import vn.edu.thesis.BE_subject_pathway.repository.Neo4jSubjectGroupRepository.SubjectGroupData;

class SubjectGroupRecommendationServiceTest {
    private final HighSchoolRepository schools = mock(HighSchoolRepository.class);
    private final Neo4jSubjectGroupRepository graph = mock(Neo4jSubjectGroupRepository.class);
    private final SubjectGroupRecommendationService service = new SubjectGroupRecommendationService(schools, graph);

    @Test
    void includesAOneSubjectSchoolGroupInRecommendations() {
        when(schools.existsById("SCHOOL")).thenReturn(true);
        when(graph.findAcademicYears("SCHOOL")).thenReturn(List.of("2026-2027"));
        when(graph.findSubjectGroups("SCHOOL", "2026-2027")).thenReturn(List.of(
                new SubjectGroupData("A01", "Nhóm A01", "2026-2027",
                        List.of(new SubjectDto("MATH", "Toán")))));
        SubjectGroupRecommendationRequest request = new SubjectGroupRecommendationRequest();
        request.setAcademicYear("2026-2027");
        request.setPreferredSubjectCodes(List.of("MATH"));

        var result = service.recommend("SCHOOL", request);

        assertEquals(1, result.getEligibleGroupCount());
        assertEquals(1, result.getRecommendations().size());
        assertEquals(1, result.getRecommendations().get(0).getSubjects().size());
        assertEquals("A01", result.getRecommendations().get(0).getGroupCode());
    }
}
