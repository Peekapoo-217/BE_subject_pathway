package vn.edu.thesis.BE_subject_pathway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupDto;
import vn.edu.thesis.BE_subject_pathway.entity.HighSchool;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.HighSchoolRepository;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectGroupRepository;
import vn.edu.thesis.BE_subject_pathway.repository.projection.SubjectGroupFlatProjection;
import vn.edu.thesis.BE_subject_pathway.service.impl.HighSchoolServiceImpl;

/**
 * Unit test cho business logic cua HighSchoolServiceImpl:
 * mapping DTO, gom nhom flat projection va error handling 404
 * (theo AGENTS.md: test cho error handling).
 */
@ExtendWith(MockitoExtension.class)
class HighSchoolServiceImplTest {

    @Mock
    private HighSchoolRepository highSchoolRepository;

    @Mock
    private SubjectGroupRepository subjectGroupRepository;

    private HighSchoolServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HighSchoolServiceImpl(
                highSchoolRepository, subjectGroupRepository);
    }

    private static SubjectGroupFlatProjection row(
            String groupCode, String groupName,
            String subjectCode, String subjectName) {
        return new SubjectGroupFlatProjection() {
            @Override
            public String getGroupCode() {
                return groupCode;
            }

            @Override
            public String getGroupName() {
                return groupName;
            }

            @Override
            public String getSubjectCode() {
                return subjectCode;
            }

            @Override
            public String getSubjectName() {
                return subjectName;
            }
        };
    }

    @Test
    @DisplayName("getAllHighSchools map entity sang DTO dung truong code va name")
    void getAllHighSchools_mapsToDto() {
        when(highSchoolRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(
                HighSchool.builder()
                        .code("THPT01")
                        .name("Truong THPT Chuyen")
                        .location("Ha Noi")
                        .build(),
                HighSchool.builder()
                        .code("THPT02")
                        .name("Truong THPT Nguyen Trai")
                        .location("Ha Noi")
                        .build()));

        List<HighSchoolDto> result = service.getAllHighSchools();

        assertEquals(2, result.size());
        assertEquals("THPT01", result.get(0).getCode());
        assertEquals("Truong THPT Chuyen", result.get(0).getName());
        assertEquals("THPT02", result.get(1).getCode());
        verifyNoInteractions(subjectGroupRepository);
    }

    @Test
    @DisplayName("getSubjectGroupsBySchool gom flat rows thanh nhom mon dung thu tu")
    void getSubjectGroupsBySchool_groupsFlatRows() {
        when(highSchoolRepository.existsById("THPT01")).thenReturn(true);
        when(subjectGroupRepository.findSubjectGroupsBySchool("THPT01"))
                .thenReturn(List.of(
                        row("KHTN", "Khoi Khoa hoc Tu nhien", "TOAN", "Toan"),
                        row("KHTN", "Khoi Khoa hoc Tu nhien", "LY", "Vat li"),
                        row("KHXH", "Khoi Khoa hoc Xa hoi", "VAN", "Ngu van")));

        List<SubjectGroupDto> result = service.getSubjectGroupsBySchool("THPT01");

        assertEquals(2, result.size());
        assertEquals("KHTN", result.get(0).getGroupCode());
        assertEquals("Khoi Khoa hoc Tu nhien", result.get(0).getGroupName());
        assertEquals(2, result.get(0).getSubjects().size());
        assertEquals("TOAN", result.get(0).getSubjects().get(0).getCode());
        assertEquals("Toan", result.get(0).getSubjects().get(0).getName());
        assertEquals("LY", result.get(0).getSubjects().get(1).getCode());
        assertEquals("KHXH", result.get(1).getGroupCode());
        assertEquals(1, result.get(1).getSubjects().size());
    }

    @Test
    @DisplayName("getSubjectGroupsBySchool nem ResourceNotFound khi ma truong khong ton tai")
    void getSubjectGroupsBySchool_throws_whenSchoolNotFound() {
        when(highSchoolRepository.existsById("KHONGCO")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSubjectGroupsBySchool("KHONGCO"));
        verifyNoInteractions(subjectGroupRepository);
    }

    @Test
    @DisplayName("getSubjectGroupsBySchool tra ve danh sach rong khi truong chua co nhom mon")
    void getSubjectGroupsBySchool_returnsEmpty_whenSchoolHasNoGroups() {
        when(highSchoolRepository.existsById("THPT02")).thenReturn(true);
        when(subjectGroupRepository.findSubjectGroupsBySchool("THPT02"))
                .thenReturn(List.of());

        List<SubjectGroupDto> result = service.getSubjectGroupsBySchool("THPT02");

        assertTrue(result.isEmpty());
    }
}
