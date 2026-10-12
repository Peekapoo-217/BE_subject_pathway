package vn.edu.thesis.BE_subject_pathway.dto;

public record AuthSessionResponse(String accessToken, String tokenType, long expiresIn, User user) {
    public record User(String id, String username, String role) {}
    @Override public String toString() { return "AuthSessionResponse[redacted]"; }
}
