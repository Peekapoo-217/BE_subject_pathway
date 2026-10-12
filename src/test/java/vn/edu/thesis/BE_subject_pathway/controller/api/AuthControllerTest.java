package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import vn.edu.thesis.BE_subject_pathway.dto.AuthSessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.thesis.BE_subject_pathway.service.admin.AuthService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginAcceptsSevenCharacterBootstrapPassword() throws Exception {
        when(authService.login("adminsp", "adminsp")).thenReturn(new AuthService.IssuedSession(new AuthSessionResponse("token", "Bearer", 900, new AuthSessionResponse.User("id", "student", "USER")), "refresh-secret"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"adminsp\",\"password\":\"adminsp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).login("adminsp", "adminsp");
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorizedRatherThanValidationError() throws Exception {
        when(authService.login("adminsp", "short6"))
                .thenThrow(new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"adminsp\",\"password\":\"short6\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không đúng"));

        verify(authService).login("adminsp", "short6");
    }

    @Test
    void registrationAcceptsSixCharacterPassword() throws Exception {
        when(authService.register("student", "sixsix"))
                .thenReturn(new AuthService.IssuedSession(new AuthSessionResponse("token", "Bearer", 900, new AuthSessionResponse.User("id", "student", "USER")), "refresh-secret"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"sixsix\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).register("student", "sixsix");
    }

    @Test
    void registrationRejectsFiveCharacterPasswordWithFriendlyMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"five5\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Mật khẩu phải có từ 6 đến 200 ký tự")));
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        when(authService.register("student", "seven77"))
                .thenThrow(new IllegalArgumentException("Tên đăng nhập đã tồn tại"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"seven77\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập đã tồn tại"));
    }

    @Test
    void unexpectedLoginFailureDoesNotExposeInternalDetails() throws Exception {
        when(authService.login("adminsp", "seven77"))
                .thenThrow(new IllegalStateException("internal database host and SQL details"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"adminsp\",\"password\":\"seven77\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Hệ thống đang gặp sự cố. Vui lòng thử lại sau."));
    }
}
