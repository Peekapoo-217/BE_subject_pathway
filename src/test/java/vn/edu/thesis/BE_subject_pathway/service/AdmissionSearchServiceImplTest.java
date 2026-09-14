package vn.edu.thesis.BE_subject_pathway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import vn.edu.thesis.BE_subject_pathway.repository.projection.SearchStatsProjection;
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

    private AdmissionSearchServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AdmissionSearchServiceImpl(
                combinationRepository, offerRepository, subjectRepository);
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

        SubjectSearchResponse response = service.searchBySubjects(request);

        assertNotNull(response);
        assertEquals(1, response.getTotalCombinations());
        assertEquals("A00", response.getPossibleCombinations().get(0).getCombinationId());
        assertEquals(12L, response.getTotalMajors());
        assertEquals(5L, response.getTotalUniversities());
        verify(offerRepository).countMajorsAndUniversities(List.of("A00"), "100");
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
}
