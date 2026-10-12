package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.thesis.BE_subject_pathway.service.admin.AdminManagementService;

@WebMvcTest(AdminManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminPasswordValidationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminManagementService management;

    @Test
    void creatingUserAcceptsSixCharacterPassword() throws Exception {
        when(management.createUser("student", "sixsix", "USER"))
                .thenReturn(Map.of("id", "user-id"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"sixsix\",\"role\":\"USER\"}"))
                .andExpect(status().isOk());

        verify(management).createUser("student", "sixsix", "USER");
    }

    @Test
    void creatingUserRejectsFiveCharacterPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"five5\",\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(management);
    }

    @Test
    void resettingPasswordAcceptsSixCharacters() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/user-id/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"sixsix\"}"))
                .andExpect(status().isOk());

        verify(management).resetPassword("user-id", "sixsix");
    }

    @Test
    void resettingPasswordRejectsFiveCharacters() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/user-id/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"five5\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(management);
    }
}
