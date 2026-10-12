package vn.edu.thesis.BE_subject_pathway.controller.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import vn.edu.thesis.BE_subject_pathway.dto.ApiResponse;
import vn.edu.thesis.BE_subject_pathway.dto.AuthSessionResponse;
import vn.edu.thesis.BE_subject_pathway.service.admin.AuthService;

@RestController
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/auth")
public class AuthController {
    public static final String REFRESH_COOKIE = "sp_refresh";
    private final AuthService auth;
    private final boolean secureCookie;
    private final Duration refreshTtl;

    public AuthController(AuthService auth, @Value("${app.auth.cookie-secure:true}") boolean secureCookie,
                          @Value("${app.auth.refresh-ttl-days:7}") long refreshDays) {
        this.auth = auth; this.secureCookie = secureCookie; this.refreshTtl = Duration.ofDays(refreshDays);
    }

    public record LoginCredentials(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập")
            @Size(max = 80, message = "Tên đăng nhập quá dài") String username,
            @NotBlank(message = "Vui lòng nhập mật khẩu") String password) {}
    public record RegistrationCredentials(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập")
            @Size(max = 80, message = "Tên đăng nhập quá dài") String username,
            @NotBlank(message = "Vui lòng nhập mật khẩu")
            @Size(min = 6, max = 200, message = "Mật khẩu phải có từ 6 đến 200 ký tự") String password) {}
    public record CsrfResponse(String token, String headerName) {}

    @GetMapping("/csrf")
    @Operation(summary = "Lấy CSRF token trước các POST auth", description = "Giữ cookie CSRF và gửi token trong X-XSRF-TOKEN. Không cache response.")
    public ApiResponse<CsrfResponse> csrf(CsrfToken token, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(new CsrfResponse(token.getToken(), token.getHeaderName()));
    }

    @PostMapping("/register")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Đăng ký USER và đặt refresh cookie HttpOnly")
    public ApiResponse<AuthSessionResponse> register(@Valid @RequestBody RegistrationCredentials request,
                                                     HttpServletResponse response) {
        try { return issued(auth.register(request.username(), request.password()), response, true); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage()); }
    }

    @PostMapping("/login")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Đăng nhập, trả access token và đặt refresh cookie HttpOnly")
    public ApiResponse<AuthSessionResponse> login(@Valid @RequestBody LoginCredentials request, HttpServletResponse response) {
        return unauthorized(() -> auth.login(request.username(), request.password()), response, true);
    }

    @PostMapping("/refresh")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Xoay refresh cookie và cấp access token", description = "Không nhận refresh token trong JSON. Yêu cầu cookie sp_refresh và header CSRF.")
    public ApiResponse<AuthSessionResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh,
                                                    HttpServletResponse response) {
        return unauthorized(() -> auth.refresh(refresh), response, false);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Thu hồi ngay phiên hiện tại và xóa refresh cookie", description = "Không cần Bearer token. Yêu cầu CSRF; không ảnh hưởng thiết bị khác.")
    public ApiResponse<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh,
                                    HttpServletResponse response) {
        auth.logout(refresh);
        setRefreshCookie(response, "", Duration.ZERO);
        clearCsrf(response);
        noStore(response);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Map<String, Object>> me(Principal principal, HttpServletResponse response) {
        noStore(response);
        return ApiResponse.ok(auth.currentUser(principal.getName()));
    }

    private ApiResponse<AuthSessionResponse> unauthorized(java.util.function.Supplier<AuthService.IssuedSession> action,
                                                          HttpServletResponse response, boolean clearCsrf) {
        try { return issued(action.get(), response, clearCsrf); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, exception.getMessage());
        }
    }

    private ApiResponse<AuthSessionResponse> issued(AuthService.IssuedSession session, HttpServletResponse response, boolean clearCsrf) {
        setRefreshCookie(response, session.refreshToken(), refreshTtl);
        if (clearCsrf) clearCsrf(response);
        noStore(response);
        return ApiResponse.ok(session.response());
    }

    private void setRefreshCookie(HttpServletResponse response, String value, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/v1/auth").maxAge(ttl).build().toString());
    }

    private void clearCsrf(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("XSRF-TOKEN", "")
                .secure(secureCookie).sameSite("Lax").path("/").maxAge(Duration.ZERO).build().toString());
    }

    private void noStore(HttpServletResponse response) { response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"); }
}
