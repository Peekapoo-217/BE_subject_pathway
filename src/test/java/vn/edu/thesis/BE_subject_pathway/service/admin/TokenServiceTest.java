package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenServiceTest {
    private final TokenService tokens = new TokenService("01234567890123456789012345678901", 900);

    @Test
    void issuesSignedShortLivedAccessToken() {
        String token = tokens.issue("user-id", "ADMIN", 0, "session-id");

        assertEquals("user-id", tokens.verify(token).get("sub"));
        assertEquals("ADMIN", tokens.verify(token).get("role"));
        assertTrue(((Number) tokens.verify(token).get("exp")).longValue()
                - ((Number) tokens.verify(token).get("iat")).longValue() <= 900);
    }

    @Test
    void rejectsModifiedToken() {
        String token = tokens.issue("user-id", "USER", 0, "session-id");
        String modified = (token.startsWith("a") ? "b" : "a") + token.substring(1);

        assertTrue(tokens.verify(modified).isEmpty());
    }
}
