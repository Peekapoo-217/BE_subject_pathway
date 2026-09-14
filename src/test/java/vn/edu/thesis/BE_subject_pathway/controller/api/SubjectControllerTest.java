package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.mockito.Mockito.verify;
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
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectClassificationResponse;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto;
import vn.edu.thesis.BE_subject_pathway.service.SubjectService;

/**
 * Test slice WebMvc cho SubjectController:
 * kiem tra endpoint GET /api/v1/subjects/classifications tra ve 200 voi dung cau truc ApiResponse.
 */
@WebMvcTest(SubjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubjectService subjectService;

    @Test
    @DisplayName("GET /api/v1/subjects/classifications tra ve 200 va danh sach phan loai mon hoc")
    void getSubjectClassifications_ok() throws Exception {
        SubjectClassificationResponse response = new SubjectClassificationResponse(
                List.of(new SubjectDto("MATH", "Toán")),
                List.of(new SubjectDto("PHYSICS", "Vật lí"))
        );
        when(subjectService.getSubjectClassifications()).thenReturn(response);

        mockMvc.perform(get("/api/v1/subjects/classifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mandatorySubjects[0].code").value("MATH"))
                .andExpect(jsonPath("$.data.mandatorySubjects[0].name").value("Toán"))
                .andExpect(jsonPath("$.data.electiveSubjects[0].code").value("PHYSICS"))
                .andExpect(jsonPath("$.data.electiveSubjects[0].name").value("Vật lí"));

        verify(subjectService).getSubjectClassifications();
    }
}
