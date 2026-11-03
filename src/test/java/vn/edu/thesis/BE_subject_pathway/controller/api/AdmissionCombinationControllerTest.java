package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.thesis.BE_subject_pathway.dto.response.CombinationResolveResponse;
import vn.edu.thesis.BE_subject_pathway.service.AdmissionCombinationLabelService;

@WebMvcTest(AdmissionCombinationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdmissionCombinationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdmissionCombinationLabelService service;

    @Test
    void resolveExactCompositionReturnsMatchAndScopedLabels() throws Exception {
        when(service.resolveExactSubjectSet(List.of("MATH", "PHYSICS", "ENGLISH")))
                .thenReturn(new CombinationResolveResponse(List.of(
                        new CombinationResolveResponse.Match("MATH_PHYSICS_ENGLISH", "Math, Physics, English", 3,
                                List.of(new CombinationResolveResponse.Component("SUBJECT", "MATH", "Math", "MATH")),
                                List.of(new CombinationResolveResponse.Label("A01", "Math, Physics, English",
                                        "https://example.test", "2026", null)))),
                        "Exact subject-only matches in the current catalogue."));

        mockMvc.perform(post("/api/v1/admissions/combinations/resolve-by-subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectCodes":["MATH","PHYSICS","ENGLISH"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matches[0].combinationId").value("MATH_PHYSICS_ENGLISH"))
                .andExpect(jsonPath("$.data.matches[0].labels[0].sourceUrl").value("https://example.test"))
                .andExpect(jsonPath("$.data.resultScope").value("Exact subject-only matches in the current catalogue."));
    }

    @Test
    void resolveExactCompositionValidatesComponentCountAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/combinations/resolve-by-subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectCodes":["MATH"]}
                                """))
                .andExpect(status().isBadRequest());
    }
}
