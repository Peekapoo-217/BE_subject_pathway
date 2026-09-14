package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationDto;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectSearchResponse;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.service.AdmissionSearchService;

/**
 * Test slice WebMvc cho AdmissionSearchController: validation @Valid,
 * dinh dang ApiResponse va xu ly loi qua GlobalExceptionHandler
 * (theo AGENTS.md: test validation va error handling).
 * addFilters = false: slice test nghiep vu, khong ap SecurityFilterChain.
 */
@WebMvcTest(AdmissionSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdmissionSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdmissionSearchService admissionSearchService;

    @Test
    @DisplayName("POST search-by-subjects tra ve 200 va ApiResponse khi hop le")
    void searchBySubjects_ok() throws Exception {
        SubjectSearchResponse response = new SubjectSearchResponse(
                1,
                List.of(new CombinationDto("A00", "A00 - Khoi A", (short) 3)),
                12L, 5L);
        when(admissionSearchService.searchBySubjects(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admissions/search-by-subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCodes\":[\"Toan\",\"Ly\",\"Hoa\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCombinations").value(1))
                .andExpect(jsonPath("$.data.possibleCombinations[0].combinationId")
                        .value("A00"))
                .andExpect(jsonPath("$.data.totalMajors").value(12))
                .andExpect(jsonPath("$.data.totalUniversities").value(5));
    }

    @Test
    @DisplayName("POST tra ve 400 khi danh sach mon rong")
    void searchBySubjects_badRequest_whenEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/search-by-subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCodes\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST tra ve 400 khi vuot 4 mon")
    void searchBySubjects_badRequest_whenMoreThanFour() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/search-by-subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCodes\":"
                                + "[\"Toan\",\"Ly\",\"Hoa\",\"Van\",\"Su\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST tra ve 400 khi co ma mon de trong")
    void searchBySubjects_badRequest_whenBlankCode() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/search-by-subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCodes\":[\"Toan\",\" \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST tra ve 404 khi service nem ResourceNotFound")
    void searchBySubjects_notFound() throws Exception {
        when(admissionSearchService.searchBySubjects(any()))
                .thenThrow(new ResourceNotFoundException(
                        "Khong tim thay to hop xet tuyen nao phu hop"));

        mockMvc.perform(post("/api/v1/admissions/search-by-subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCodes\":[\"Toan\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
