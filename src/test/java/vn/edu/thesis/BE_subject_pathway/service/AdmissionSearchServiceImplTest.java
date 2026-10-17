package vn.edu.thesis.BE_subject_pathway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.thesis.BE_subject_pathway.dto.request.SubjectSearchRequest;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectSearchResponse;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionCombinationRepository;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionOfferRepository;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationLabelProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.MajorProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.SearchStatsProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.UniversityProjection;
import vn.edu.thesis.BE_subject_pathway.service.impl.AdmissionSearchServiceImpl;

/**
 * Unit test cho business logic cua AdmissionSearchServiceImpl
 * (theo AGENTS.md: test cho ranking va error handling).
 */
@ExtendWith(MockitoExtension.class)
class AdmissionSearchServiceImplTest {

    @Mock
    private AdmissionCombinationRepository combinationRepository;

    @Mock
    private AdmissionOfferRepository offerRepository;

    @Mock
    private vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository subjectRepository;

    @Mock
    private vn.edu.thesis.BE_subject_pathway.repository.HighSchoolRepository highSchoolRepository;

    @Mock
    private vn.edu.thesis.BE_subject_pathway.repository.SubjectGroupRepository subjectGroupRepository;

    private AdmissionSearchServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AdmissionSearchServiceImpl(
                combinationRepository, offerRepository, subjectRepository, highSchoolRepository, subjectGroupRepository);
    }

    private SubjectSearchRequest schoolRequest() {
        return new SubjectSearchRequest(List.of("PHYSICS"), null, "TCV", "2026", "G01");
    }

    private void publishedSchoolGroup() {
        when(highSchoolRepository.existsByCodeAndDeletedAtIsNull("TCV")).thenReturn(true);
        var group = mock(vn.edu.thesis.BE_subject_pathway.repository.projection.SubjectGroupFlatProjection.class);
        when(group.getGroupCode()).thenReturn("G01");
        when(group.getSubjectCode()).thenReturn("PHYSICS");
        when(subjectGroupRepository.findSubjectGroupsBySchool("TCV", "2026")).thenReturn(List.of(group));
    }

    @Test
    void validatesSchoolYearAndGroupBeforeSearchingCombinations() {
        publishedSchoolGroup();
        var combination = mock(CombinationProjection.class);
        when(combination.getCombinationId()).thenReturn("A00");
        when(combinationRepository.findValidCombinations(List.of("PHYSICS"))).thenReturn(List.of(combination));
        when(offerRepository.countMajorsAndUniversities(List.of("A00"), "100")).thenReturn(stats(0, 0));

        assertEquals(1, service.searchBySubjects(schoolRequest()).getTotalCombinations());
        verify(subjectGroupRepository).findSubjectGroupsBySchool("TCV", "2026");
    }

    @Test
    void rejectsSubjectsFromAnotherAcademicYear() {
        publishedSchoolGroup();
        var request = schoolRequest();
        request.setSubjectCodes(List.of("HISTORY"));

        assertThrows(IllegalArgumentException.class, () -> service.searchBySubjects(request));
        verifyNoInteractions(combinationRepository, offerRepository, subjectRepository);
    }

    @Test
    void rejectsMissingOrUnpublishedGroupForSelectedYear() {
        when(highSchoolRepository.existsByCodeAndDeletedAtIsNull("TCV")).thenReturn(true);
        assertThrows(ResourceNotFoundException.class, () -> service.searchBySubjects(schoolRequest()));
        verify(subjectGroupRepository).findSubjectGroupsBySchool("TCV", "2026");
        verifyNoInteractions(combinationRepository, offerRepository, subjectRepository);
    }

    @Test
    void rejectsDeletedSchoolBeforeLookingUpGroups() {
        assertThrows(ResourceNotFoundException.class, () -> service.searchBySubjects(schoolRequest()));
        verifyNoInteractions(subjectGroupRepository, combinationRepository, offerRepository, subjectRepository);
    }

    @Test
    void rejectsIncompleteSchoolYearContext() {
        var request = schoolRequest();
        request.setGroupCode(null);
        assertThrows(IllegalArgumentException.class, () -> service.searchBySubjects(request));
        verifyNoInteractions(highSchoolRepository, subjectGroupRepository, combinationRepository);
    }

    private static SearchStatsProjection stats(long majors, long universities) {
        return new SearchStatsProjection() {
            @Override
            public Long getTotalMajors() {
                return majors;
            }

            @Override
            public Long getTotalUniversities() {
                return universities;
            }
        };
    }

    @Test
    @DisplayName("searchBySubjects tra ve dung thong ke khi co to hop phu hop")
    void searchBySubjects_returnsStats() {
        SubjectSearchRequest request = new SubjectSearchRequest(List.of("Toan", "Ly", "Hoa"));
        when(combinationRepository.findValidCombinations(List.of("Toan", "Ly", "Hoa")))
                .thenReturn(List.of(new CombinationProjection() {
                    @Override
                    public String getCombinationId() {
                        return "A00";
                    }

                    @Override
                    public String getCombinationDisplay() {
                        return "A00 - Khoi A";
                    }

                    @Override
                    public Short getComponentCount() {
                        return (short) 3;
                    }
                }));
        when(offerRepository.countMajorsAndUniversities(List.of("A00"), "100"))
                .thenReturn(stats(12L, 5L));
        when(combinationRepository.findLabelsByCombinationIds(List.of("A00")))
                .thenReturn(List.of(new CombinationLabelProjection() {
                    public String getCombinationId() { return "A00"; }
                    public String getCode() { return "A00"; }
                    public String getSubjectDetails() { return "Toán, Vật lí, Hóa học"; }
                }));

        SubjectSearchResponse response = service.searchBySubjects(request);

        assertNotNull(response);
        assertEquals(1, response.getTotalCombinations());
        assertEquals("A00", response.getPossibleCombinations().get(0).getCombinationId());
        assertEquals("A00", response.getPossibleCombinations().get(0).getLabels().get(0).getCode());
        assertEquals(12L, response.getTotalMajors());
        assertEquals(5L, response.getTotalUniversities());
        assertEquals(List.of(), response.getMajors());
        verify(offerRepository).countMajorsAndUniversities(List.of("A00"), "100");
        verify(offerRepository).findUniversitiesByCombinations(List.of("A00"), "100");
    }

    @Test
    @DisplayName("searchBySubjects chi tra nganh cua truong dai hoc da chon")
    void searchBySubjects_filtersMajorsByUniversity() {
        SubjectSearchRequest request = new SubjectSearchRequest(List.of("Toan"), "UNI1");
        when(combinationRepository.findValidCombinations(List.of("Toan")))
                .thenReturn(List.of(new CombinationProjection() {
                    @Override
                    public String getCombinationId() {
                        return "A00";
                    }

                    @Override
                    public String getCombinationDisplay() {
                        return "A00 - Khoi A";
                    }

                    @Override
                    public Short getComponentCount() {
                        return (short) 3;
                    }
                }));
        when(offerRepository.countMajorsAndUniversities(List.of("A00"), "100"))
                .thenReturn(stats(12L, 5L));
        when(combinationRepository.findLabelsByCombinationIds(List.of("A00")))
                .thenReturn(List.of());
        when(offerRepository.findUniversitiesByCombinations(List.of("A00"), "100"))
                .thenReturn(List.of(new UniversityProjection() {
                    @Override
                    public String getUniversityCode() {
                        return "UNI1";
                    }

                    @Override
                    public String getUniversityName() {
                        return "Truong 1";
                    }
                }));
        when(offerRepository.findMajorsByCombinationsAndUniversity(List.of("A00"), "100", "UNI1"))
                .thenReturn(List.of(new MajorProjection() {
                    @Override
                    public String getProgramCode() {
                        return "7480201";
                    }

                    @Override
                    public String getProgramName() {
                        return "Cong nghe thong tin";
                    }
                }));

        SubjectSearchResponse response = service.searchBySubjects(request);

        assertEquals("UNI1", response.getUniversities().get(0).getUniversityCode());
        assertEquals(1, response.getMajors().size());
        assertEquals("7480201", response.getMajors().get(0).getProgramCode());
        verify(offerRepository).findMajorsByCombinationsAndUniversity(List.of("A00"), "100", "UNI1");
    }

    @Test
    @DisplayName("searchBySubjects nem ResourceNotFound khi khong co to hop nao khop")
    void searchBySubjects_throws_whenNoCombination() {
        SubjectSearchRequest request = new SubjectSearchRequest(List.of("Toan"));
        when(combinationRepository.findValidCombinations(List.of("Toan")))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.searchBySubjects(request));
        verifyNoInteractions(offerRepository);
    }

    @Test
    @DisplayName("searchBySubjects trim khoang trang trong ma mon truoc khi truy van")
    void searchBySubjects_trimsSubjectCodes() {
        SubjectSearchRequest request = new SubjectSearchRequest(List.of(" Toan ", "Ly"));
        when(combinationRepository.findValidCombinations(List.of("Toan", "Ly")))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.searchBySubjects(request));

        verify(combinationRepository).findValidCombinations(List.of("Toan", "Ly"));
        verifyNoInteractions(offerRepository);
    }

    @Test
    @DisplayName("Nhom mon THPT duoc gop voi mon bat buoc truoc khi tim to hop")
    void searchBySubjects_addsMandatorySubjectsWithoutDuplicates() {
        SubjectSearchRequest request = new SubjectSearchRequest(
                List.of(" PHYSICS ", "CHEMISTRY", "MATH", "BIOLOGY"));
        when(subjectRepository.findCodesByIsMandatoryTrue())
                .thenReturn(List.of(" MATH ", "LITERATURE"));
        List<String> effectiveCodes = List.of(
                "PHYSICS", "CHEMISTRY", "MATH", "BIOLOGY", "LITERATURE");
        CombinationProjection matched = mock(CombinationProjection.class);
        when(matched.getCombinationId()).thenReturn("CHEMISTRY_MATH_PHYSICS");
        when(matched.getCombinationDisplay()).thenReturn("Toan - Ly - Hoa");
        when(matched.getComponentCount()).thenReturn((short) 3);
        when(combinationRepository.findValidCombinations(effectiveCodes))
                .thenReturn(List.of(matched));
        when(offerRepository.countMajorsAndUniversities(
                List.of("CHEMISTRY_MATH_PHYSICS"), "100"))
                .thenReturn(stats(0L, 0L));

        SubjectSearchResponse response = service.searchBySubjects(request);

        assertEquals(1, response.getTotalCombinations());
        assertEquals("CHEMISTRY_MATH_PHYSICS",
                response.getPossibleCombinations().get(0).getCombinationId());
        assertEquals(List.of(), response.getUniversities());
        assertEquals(List.of(), response.getMajors());
        verify(combinationRepository).findValidCombinations(effectiveCodes);
        verify(offerRepository).findUniversitiesByCombinations(
                List.of("CHEMISTRY_MATH_PHYSICS"), "100");
    }

    @Test
    @DisplayName("Khong tim thay to hop thi khong truy van offer")
    void searchBySubjects_withMandatoryCodesStopsBeforeOfferLookupWhenNoMatch() {
        SubjectSearchRequest request = new SubjectSearchRequest(List.of("INFORMATICS"));
        when(subjectRepository.findCodesByIsMandatoryTrue())
                .thenReturn(List.of("MATH", "LITERATURE"));
        when(combinationRepository.findValidCombinations(
                List.of("INFORMATICS", "MATH", "LITERATURE")))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.searchBySubjects(request));

        verifyNoInteractions(offerRepository);
    }
}
