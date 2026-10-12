package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AuthAccountRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.thesis.BE_subject_pathway.config.SecurityConfig;
import vn.edu.thesis.BE_subject_pathway.dto.AuthSessionResponse;
import vn.edu.thesis.BE_subject_pathway.service.admin.*;

@WebMvcTest(controllers = AuthController.class, properties = "app.auth.cookie-secure=true")
@Import(SecurityConfig.class)
class AuthSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean TokenService tokens;
    @MockitoBean RedisSessionStore sessions;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean AuthAccountRepository accounts;

    private AuthService.IssuedSession issued() {
        return new AuthService.IssuedSession(new AuthSessionResponse("access", "Bearer", 900,
                new AuthSessionResponse.User("id", "student", "USER")), "refresh-secret");
    }

    @Test void csrfIsPublicAndNoStore() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN=")));
    }

    @Test void loginWithoutCsrfIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"sixsix\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auth);
    }

    @Test void cookieIsHttpOnlySecureScopedAndRefreshNeverAppearsInJson() throws Exception {
        when(auth.login("student", "sixsix")).thenReturn(issued());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"sixsix\"}"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("sp_refresh="), containsString("HttpOnly"), containsString("Secure"),
                        containsString("SameSite=Lax"), containsString("Path=/api/v1/auth"), containsString("Max-Age=604800")))))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test void refreshReadsOnlyCookieAndLogoutNeedsNoBearer() throws Exception {
        when(auth.refresh("cookie-secret")).thenReturn(issued());
        mvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(new Cookie("sp_refresh", "cookie-secret")))
                .andExpect(status().isOk());
        verify(auth).refresh("cookie-secret");
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(new Cookie("sp_refresh", "cookie-secret")))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(containsString("sp_refresh="), containsString("Max-Age=0")))));
        verify(auth).logout("cookie-secret");
    }

    @Test void badCsrfAndUntrustedOriginAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/auth/login").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auth);
    }

    @Test void developmentCorsAllowsCredentialsAndUnauthenticatedMeReturns401() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void failedLogoutDoesNotClearCookieOrReportSuccess() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Temporarily unavailable"))
                .when(auth).logout("cookie-secret");
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(new Cookie("sp_refresh", "cookie-secret")))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Set-Cookie"));
    }
}
