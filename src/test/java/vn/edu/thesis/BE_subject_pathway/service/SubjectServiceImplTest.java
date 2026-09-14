package vn.edu.thesis.BE_subject_pathway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectClassificationResponse;
import vn.edu.thesis.BE_subject_pathway.entity.Subject;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository;
import vn.edu.thesis.BE_subject_pathway.service.impl.SubjectServiceImpl;

/**
 * Unit test cho business logic SubjectServiceImpl:
 * kiem tra phan chia danh sach thanh mandatorySubjects va electiveSubjects.
 */
@ExtendWith(MockitoExtension.class)
class SubjectServiceImplTest {

    @Mock
    private SubjectRepository subjectRepository;

    private SubjectServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubjectServiceImpl(subjectRepository);
    }

    @Test
    @DisplayName("getSubjectClassifications phan tach dung mon bat buoc va mon tu chon")
    void getSubjectClassifications_success() {
        Subject math = Subject.builder()
                .code("MATH")
                .name("Toán")
                .subjectType("ACADEMIC")
                .isMandatory(true)
                .canonicalSubjectCode(null)
                .build();

        Subject literature = Subject.builder()
                .code("LITERATURE")
                .name("Ngữ văn")
                .subjectType("ACADEMIC")
                .isMandatory(true)
                .canonicalSubjectCode(null)
                .build();

        Subject physics = Subject.builder()
                .code("PHYSICS")
                .name("Vật lí")
                .subjectType("ACADEMIC")
                .isMandatory(false)
                .isElective(true)
                .canonicalSubjectCode(null)
                .build();

        Subject technology = Subject.builder()
                .code("TECHNOLOGY")
                .name("Công nghệ")
                .subjectType("ACADEMIC")
                .isMandatory(false)
                .isElective(true)
                .canonicalSubjectCode(null)
                .build();

        Subject localEdu = Subject.builder()
                .code("LOCAL_EDU")
                .name("Giáo dục địa phương")
                .subjectType("LOCAL")
                .isMandatory(false)
                .isElective(false)
                .canonicalSubjectCode(null)
                .build();

        when(subjectRepository.findRootSubjects()).thenReturn(List.of(math, literature, physics, technology, localEdu));

        SubjectClassificationResponse response = service.getSubjectClassifications();

        assertNotNull(response);
        assertEquals(2, response.getMandatorySubjects().size());
        assertEquals("MATH", response.getMandatorySubjects().get(0).getCode());
        assertEquals("LITERATURE", response.getMandatorySubjects().get(1).getCode());

        assertEquals(2, response.getElectiveSubjects().size());
        assertEquals("PHYSICS", response.getElectiveSubjects().get(0).getCode());
        assertEquals("TECHNOLOGY", response.getElectiveSubjects().get(1).getCode());

        verify(subjectRepository).findRootSubjects();
    }

    @Test
    @DisplayName("getSubjectClassifications tra ve danh sach rong khi repository khong co mon")
    void getSubjectClassifications_empty() {
        when(subjectRepository.findRootSubjects()).thenReturn(List.of());

        SubjectClassificationResponse response = service.getSubjectClassifications();

        assertNotNull(response);
        assertTrue(response.getMandatorySubjects().isEmpty());
        assertTrue(response.getElectiveSubjects().isEmpty());
    }
}
