package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class RedisSessionStoreIntegrationTest {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private RedisSessionStore sessions;
    private String prefix;
    private String sid;
    private String token;

    @BeforeEach void connect() {
        connection = new LettuceConnectionFactory("127.0.0.1", 6379);
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        prefix = "test:auth:" + UUID.randomUUID() + ":";
        sessions = new RedisSessionStore(redis, prefix, 7);
        sid = UUID.randomUUID().toString();
        token = sid + "." + "a".repeat(64);
        sessions.create(sid, "user-1", 3, token);
    }

    @AfterEach void close() {
        // This randomly generated namespace belongs only to this test.
        var keys = redis.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        connection.destroy();
    }

    @Test void storesOnlyHashAndSlidingTtlAndDetectsReplay() {
        var fields = redis.opsForHash().entries(prefix + "{" + sid + "}:session");
        assertFalse(fields.values().contains(token));
        assertEquals(64, fields.get("hash").toString().length());
        assertTrue(redis.getExpire(prefix + "{" + sid + "}:session") > 604790);
        redis.expire(prefix + "{" + sid + "}:session", Duration.ofSeconds(30));
        String replacement = sid + "." + "b".repeat(64);
        assertTrue(sessions.rotate(token, replacement));
        assertTrue(redis.getExpire(prefix + "{" + sid + "}:session") > 604790);
        assertTrue(sessions.authenticateRefresh(replacement).isPresent());
        assertTrue(sessions.authenticateRefresh(token).isEmpty());
        assertTrue(sessions.find(sid).isEmpty());
        assertTrue(sessions.authenticateRefresh(replacement).isEmpty());
    }

    @Test void forgedSecretCannotRevokeSomeoneElsesSession() {
        String forged = sid + "." + "c".repeat(64);
        assertTrue(sessions.authenticateRefresh(forged).isEmpty());
        sessions.logout(forged);
        assertTrue(sessions.find(sid).isPresent());
        sessions.logout(token);
        assertTrue(sessions.find(sid).isEmpty());
    }

    @Test void concurrentRotationAllowsAtMostOneSuccessAndRevokesReplayedFamily() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Boolean> rotate = () -> { start.await(); return sessions.rotate(token, sid + "." + "d".repeat(64)); };
            var first = executor.submit(rotate);
            var second = executor.submit(rotate);
            start.countDown();
            int successes = (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertTrue(sessions.find(sid).isEmpty());
        } finally { executor.shutdownNow(); }
    }

    @Test void logoutAffectsOnlyItsDeviceAndExpiredKeysCannotAuthenticate() throws Exception {
        String otherSid = UUID.randomUUID().toString();
        String otherToken = otherSid + "." + "e".repeat(64);
        sessions.create(otherSid, "user-1", 3, otherToken);
        sessions.logout(token);
        assertTrue(sessions.find(sid).isEmpty());
        assertTrue(sessions.find(otherSid).isPresent());
        redis.expire(prefix + "{" + otherSid + "}:session", Duration.ofMillis(1));
        Thread.sleep(20);
        assertTrue(sessions.authenticateRefresh(otherToken).isEmpty());
    }
}
