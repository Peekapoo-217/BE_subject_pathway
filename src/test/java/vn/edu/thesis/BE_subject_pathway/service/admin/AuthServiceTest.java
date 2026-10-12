package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AuthAccountRepository;

class AuthServiceTest {
    private final AuthAccountRepository accounts = mock(AuthAccountRepository.class);
    private final RedisSessionStore sessions = mock(RedisSessionStore.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final TokenService tokens = new TokenService("unit-test-secret-with-more-than-32-bytes", 900);
    private final AuthService auth = new AuthService(accounts, passwords, tokens, sessions, 900);
    private final String sid = UUID.randomUUID().toString();
    private final String refresh = sid + "." + "x".repeat(64);

    @Test void versionChangePreventsRefreshAndRevokesStaleSession() {
        when(sessions.authenticateRefresh(refresh)).thenReturn(Optional.of(new RedisSessionStore.Session("user", 1)));
        when(accounts.findActiveForRefresh("user"))
                .thenReturn(List.<String[]>of(new String[]{"name", "USER", "2"}));
        assertThrows(IllegalArgumentException.class, () -> auth.refresh(refresh));
        verify(sessions).revoke(sid);
        verify(sessions, never()).rotate(anyString(), anyString());
    }

    @Test void disabledOrDeletedUserCannotRefresh() {
        when(sessions.authenticateRefresh(refresh)).thenReturn(Optional.of(new RedisSessionStore.Session("user", 1)));
        when(accounts.findActiveForRefresh("user")).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> auth.refresh(refresh));
        verify(sessions).revoke(sid);
    }

    @Test void refreshPreservesSessionIdAndRotatesSecret() {
        when(sessions.authenticateRefresh(refresh)).thenReturn(Optional.of(new RedisSessionStore.Session("user", 1)));
        when(accounts.findActiveForRefresh("user"))
                .thenReturn(List.<String[]>of(new String[]{"name", "ADMIN", "1"}));
        when(sessions.rotate(eq(refresh), anyString())).thenReturn(true);
        var result = auth.refresh(refresh);
        assertEquals(sid, tokens.verify(result.response().accessToken()).get("sid"));
        assertNotEquals(refresh, result.refreshToken());
        assertEquals(sid, RedisSessionStore.sessionId(result.refreshToken()));
        assertFalse(result.toString().contains(result.refreshToken()));
    }

    @Test void redisFailureDoesNotIssueSessionOrFallbackToSqlRefreshTable() {
        when(sessions.authenticateRefresh(refresh)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> auth.refresh(refresh)).getStatusCode().value());
        verifyNoInteractions(accounts);
    }
}
