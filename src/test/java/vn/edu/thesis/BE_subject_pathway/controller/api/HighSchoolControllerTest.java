package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.thesis.BE_subject_pathway.dto.response.HighSchoolDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectGroupDto;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.service.HighSchoolService;

/**
 * Test slice WebMvc cho HighSchoolController: dinh dang ApiResponse
 * va error handling qua GlobalExceptionHandler.
 * addFilters = false: slice test nghiep vu, khong ap SecurityFilterChain
 * (CORS duoc verify rieng o BeSubjectPathwayApplicationTests).
 */
@WebMvcTest(HighSchoolController.class)
@AutoConfigureMockMvc(addFilters = false)
class HighSchoolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HighSchoolService highSchoolService;

    @Test
    @DisplayName("GET /high-schools tra ve 200 va ApiResponse danh sach truong")
    void getAllHighSchools_ok() throws Exception {
        when(highSchoolService.getAllHighSchools()).thenReturn(List.of(
                new HighSchoolDto("THPT01", "Truong THPT Chuyen")));

        mockMvc.perform(get("/api/v1/high-schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("THPT01"))
                .andExpect(jsonPath("$.data[0].name").value("Truong THPT Chuyen"));
    }

    @Test
    @DisplayName("GET subject-groups tra ve 200 va nhom mon kem danh sach mon")
    void getSubjectGroups_ok() throws Exception {
        when(highSchoolService.getSubjectGroupsBySchool("THPT01"))
                .thenReturn(List.of(new SubjectGroupDto(
                        "KHTN",
                        "Khoi Khoa hoc Tu nhien",
                        List.of(new SubjectDto("TOAN", "Toan")))));

        mockMvc.perform(get("/api/v1/high-schools/THPT01/subject-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].groupCode").value("KHTN"))
                .andExpect(jsonPath("$.data[0].groupName")
                        .value("Khoi Khoa hoc Tu nhien"))
                .andExpect(jsonPath("$.data[0].subjects[0].code").value("TOAN"));
    }

    @Test
    @DisplayName("GET subject-groups tra ve 404 khi ma truong khong ton tai")
    void getSubjectGroups_notFound() throws Exception {
        when(highSchoolService.getSubjectGroupsBySchool("KHONGCO"))
                .thenThrow(new ResourceNotFoundException(
                        "Truong THPT khong ton tai voi ma: KHONGCO"));

        mockMvc.perform(get("/api/v1/high-schools/KHONGCO/subject-groups"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
