package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Stores only digests, never the browser's refresh secret. All key mutations are atomic. */
@Service
public class RedisSessionStore {
    public record Session(String userId, int tokenVersion) {}
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'version', ARGV[2], 'hash', ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'hash')
            if not current then return 0 end
            if current ~= ARGV[1] then
              if redis.call('EXISTS', KEYS[2]) == 1 then redis.call('DEL', KEYS[1]) end
              return 0
            end
            redis.call('SET', KEYS[2], 'used', 'EX', ARGV[3])
            redis.call('HSET', KEYS[1], 'hash', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'hash')
            if not current then return 0 end
            if current == ARGV[1] then return 1 end
            if redis.call('EXISTS', KEYS[2]) == 1 then redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> LOGOUT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'hash')
            if current == ARGV[1] or redis.call('EXISTS', KEYS[2]) == 1 then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final String prefix;
    private final long ttlSeconds;

    public RedisSessionStore(StringRedisTemplate redis,
                             @Value("${app.auth.redis-key-prefix:subject-pathway:auth:}") String prefix,
                             @Value("${app.auth.refresh-ttl-days:7}") long refreshDays) {
        if (refreshDays <= 0 || prefix.isBlank()) throw new IllegalArgumentException("Invalid session configuration");
        this.redis = redis;
        this.prefix = prefix;
        this.ttlSeconds = Duration.ofDays(refreshDays).toSeconds();
    }

    public void create(String sid, String userId, int version, String refreshToken) {
        Long result = execute(() -> redis.execute(CREATE, List.of(key(sid)), userId,
                Integer.toString(version), digest(refreshToken), Long.toString(ttlSeconds)));
        if (!Long.valueOf(1).equals(result)) throw unavailable();
    }

    public Optional<Session> find(String sid) {
        if (!validSid(sid)) return Optional.empty();
        Map<Object, Object> values = execute(() -> redis.opsForHash().entries(key(sid)));
        if (values.isEmpty()) return Optional.empty();
        return Optional.of(new Session((String) values.get("userId"), Integer.parseInt((String) values.get("version"))));
    }

    public Optional<Session> authenticateRefresh(String token) {
        String sid = sessionId(token);
        if (sid == null) return Optional.empty();
        String hash = digest(token);
        Long valid = execute(() -> redis.execute(VERIFY, List.of(key(sid), usedKey(sid, hash)), hash));
        return Long.valueOf(1).equals(valid) ? find(sid) : Optional.empty();
    }

    public boolean rotate(String previous, String replacement) {
        String sid = sessionId(previous);
        if (sid == null || !sid.equals(sessionId(replacement))) return false;
        String hash = digest(previous);
        return Long.valueOf(1).equals(execute(() -> redis.execute(ROTATE,
                List.of(key(sid), usedKey(sid, hash)), hash, digest(replacement), Long.toString(ttlSeconds))));
    }

    public void logout(String token) {
        String sid = sessionId(token);
        if (sid == null) return;
        String hash = digest(token);
        execute(() -> redis.execute(LOGOUT, List.of(key(sid), usedKey(sid, hash)), hash));
    }

    public void revoke(String sid) {
        if (validSid(sid)) execute(() -> redis.delete(key(sid)));
    }

    public static String sessionId(String token) {
        if (token == null || !token.matches("[a-f0-9-]{36}\\.[A-Za-z0-9_-]{64}")) return null;
        String sid = token.substring(0, 36);
        return validSid(sid) ? sid : null;
    }

    private static boolean validSid(String sid) {
        return sid != null && sid.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    }

    private String key(String sid) { return prefix + "{" + sid + "}:session"; }
    private String usedKey(String sid, String hash) { return prefix + "{" + sid + "}:used:" + hash; }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private <T> T execute(Supplier<T> action) {
        try { return action.get(); }
        catch (DataAccessException exception) { throw unavailable(); }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Tạm thời không thể xác thực phiên. Vui lòng thử lại sau.");
    }
}
