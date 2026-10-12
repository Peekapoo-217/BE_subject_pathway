package vn.edu.thesis.BE_subject_pathway.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final String secret;
    private final long accessTtlSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenService(@Value("${app.auth.jwt-secret:}") String secret,
                        @Value("${app.auth.access-ttl-seconds:900}") long accessTtlSeconds) {
        this.secret = secret;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String issue(String userId, String role, int tokenVersion, String sessionId) {
        requireConfiguredSecret();
        try {
            String header = ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            long now = Instant.now().getEpochSecond();
            String payload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(
                    Map.of("sub", userId, "role", role, "ver", tokenVersion, "sid", sessionId, "iat", now, "exp", now + accessTtlSeconds)));
            String unsigned = header + "." + payload;
            return unsigned + "." + ENCODER.encodeToString(sign(unsigned));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not issue access token", exception);
        }
    }

    public Map<String, Object> verify(String token) {
        try {
            requireConfiguredSecret();
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Map.of();
            Map<String, Object> header = objectMapper.readValue(DECODER.decode(parts[0]), new TypeReference<>() {});
            if (!"HS256".equals(header.get("alg"))) return Map.of();
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, DECODER.decode(parts[2]))) return Map.of();
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<>() {});
            Object exp = claims.get("exp");
            if (!(exp instanceof Number number) || number.longValue() <= Instant.now().getEpochSecond()) return Map.of();
            if (!(claims.get("sub") instanceof String) || !(claims.get("role") instanceof String)
                    || !(claims.get("ver") instanceof Number) || !(claims.get("sid") instanceof String)) return Map.of();
            return claims;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private void requireConfiguredSecret() {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
    }
}
