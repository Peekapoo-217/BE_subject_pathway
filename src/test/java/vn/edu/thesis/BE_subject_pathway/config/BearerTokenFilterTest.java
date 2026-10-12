package vn.edu.thesis.BE_subject_pathway.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.edu.thesis.BE_subject_pathway.service.admin.*;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AuthAccountRepository;

class BearerTokenFilterTest {
    private final AuthAccountRepository accounts = mock(AuthAccountRepository.class);
    private final RedisSessionStore sessions = mock(RedisSessionStore.class);
    private final TokenService tokens = new TokenService("test-secret-with-at-least-32-bytes", 900);
    private final String sid = UUID.randomUUID().toString();
    private final String user = UUID.randomUUID().toString();
    private final BearerTokenFilter filter = new BearerTokenFilter(tokens, accounts, sessions);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private MockHttpServletResponse call() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer " + tokens.issue(user, "ADMIN", 1, sid));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response;
    }

    @Test void revokedSessionRejectsStillUnexpiredAccessToken() throws Exception {
        when(accounts.isCurrentAccount(user, "ADMIN", 1)).thenReturn(true);
        when(sessions.find(sid)).thenReturn(Optional.of(new RedisSessionStore.Session(user, 1)));
        call();
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        when(sessions.find(sid)).thenReturn(Optional.empty());
        call();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void accountVersionChangeRejectsAllDevices() throws Exception {
        when(accounts.isCurrentAccount(user, "ADMIN", 1)).thenReturn(false);
        call();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(sessions);
    }

    @Test void redisFailureFailsClosedWith503() throws Exception {
        when(accounts.isCurrentAccount(user, "ADMIN", 1)).thenReturn(true);
        when(sessions.find(sid)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(503, call().getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
