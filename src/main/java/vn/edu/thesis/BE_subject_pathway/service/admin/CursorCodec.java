package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CursorCodec {
    private final String secret;
    public CursorCodec(@Value("${app.auth.jwt-secret:}") String secret) { this.secret = secret; }

    public String encode(String value) {
        byte[] payload = value.getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    public String decode(String cursor) {
        try {
            String[] parts = cursor.split("\\.", 2);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(signature, sign(payload))) throw new IllegalArgumentException();
            return new String(payload, StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalArgumentException("Cursor không hợp lệ"); }
    }

    private byte[] sign(byte[] value) {
        try {
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception e) { throw new IllegalStateException("Could not sign cursor", e); }
    }
}
