package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.thesis.BE_subject_pathway.dto.AuthSessionResponse;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AuthAccountRepository;

@Service
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AuthService {
    /** Internal result: controllers return response(), and put refreshToken only in Set-Cookie. */
    public record IssuedSession(AuthSessionResponse response, String refreshToken) {
        @Override public String toString() { return "IssuedSession[redacted]"; }
    }
    private final AuthAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final RedisSessionStore sessions;
    private final long accessTtlSeconds;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AuthAccountRepository accounts, PasswordEncoder passwords, TokenService tokens,
                       RedisSessionStore sessions,
                       @Value("${app.auth.access-ttl-seconds:900}") long accessTtlSeconds) {
        this.accounts = accounts; this.passwords = passwords; this.tokens = tokens;
        this.sessions = sessions; this.accessTtlSeconds = accessTtlSeconds;
    }

    @Transactional
    public IssuedSession register(String username, String password) {
        String id = UUID.randomUUID().toString();
        try {
            accounts.register(id, username.trim(), passwords.encode(password));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại");
        }
        IssuedSession result = createSession(id, username.trim(), "USER", 0);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { sessions.revoke(RedisSessionStore.sessionId(result.refreshToken())); }
                        catch (RuntimeException ignored) { /* TTL removes it; missing SQL user cannot authenticate. */ }
                    }
                }
            });
        }
        return result;
    }

    public IssuedSession login(String username, String password) {
        var rows = accounts.findActiveByUsername(username.trim());
        if (rows.isEmpty() || !passwords.matches(password, rows.get(0)[2])) {
            throw new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng");
        }
        return createSession(rows.get(0)[0], rows.get(0)[1], rows.get(0)[3], Integer.parseInt(rows.get(0)[4]));
    }

    public IssuedSession refresh(String refreshToken) {
        RedisSessionStore.Session session = sessions.authenticateRefresh(refreshToken).orElseThrow(AuthService::expired);
        String sid = RedisSessionStore.sessionId(refreshToken);
        var rows = accounts.findActiveForRefresh(session.userId());
        if (rows.isEmpty() || Integer.parseInt(rows.get(0)[2]) != session.tokenVersion()) {
            sessions.revoke(sid);
            throw expired();
        }
        String replacement = newRefreshToken(sid);
        var response = response(session.userId(), rows.get(0)[0], rows.get(0)[1], session.tokenVersion(), sid);
        if (!sessions.rotate(refreshToken, replacement)) throw expired();
        return new IssuedSession(response, replacement);
    }

    public void logout(String refreshToken) { sessions.logout(refreshToken); }

    public Map<String, Object> currentUser(String id) {
        return accounts.currentUser(id);
    }

    private IssuedSession createSession(String id, String username, String role, int version) {
        String sid = UUID.randomUUID().toString();
        String refresh = newRefreshToken(sid);
        AuthSessionResponse response = response(id, username, role, version, sid);
        sessions.create(sid, id, version, refresh);
        return new IssuedSession(response, refresh);
    }

    private AuthSessionResponse response(String id, String username, String role, int version, String sid) {
        return new AuthSessionResponse(tokens.issue(id, role, version, sid), "Bearer", accessTtlSeconds,
                new AuthSessionResponse.User(id, username, role));
    }

    private String newRefreshToken(String sid) {
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        return sid + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static IllegalArgumentException expired() {
        return new IllegalArgumentException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }
}
