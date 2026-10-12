package vn.edu.thesis.BE_subject_pathway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.thesis.BE_subject_pathway.service.admin.TokenService;
import vn.edu.thesis.BE_subject_pathway.service.admin.RedisSessionStore;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AuthAccountRepository;
import org.springframework.web.server.ResponseStatusException;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final AuthAccountRepository accounts;
    private final RedisSessionStore sessions;

    public BearerTokenFilter(TokenService tokenService, AuthAccountRepository accounts, RedisSessionStore sessions) {
        this.tokenService = tokenService; this.accounts = accounts; this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return List.of("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh",
                "/api/v1/auth/logout", "/api/v1/auth/csrf").contains(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            Map<String, Object> claims = tokenService.verify(authorization.substring(7));
            String userId = (String) claims.get("sub");
            String role = (String) claims.get("role");
            Number tokenVersion = (Number) claims.get("ver");
            if (userId != null && List.of("SUPER_ADMIN", "ADMIN", "USER").contains(role)
                    && accounts.isCurrentAccount(userId, role, tokenVersion.intValue())) {
                try {
                    var session = sessions.find((String) claims.get("sid"));
                    if (session.isEmpty() || !session.get().userId().equals(userId)
                            || session.get().tokenVersion() != tokenVersion.intValue()) {
                        chain.doFilter(request, response);
                        return;
                    }
                } catch (ResponseStatusException exception) {
                    response.setStatus(503);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"message\":\"Tạm thời không thể xác thực phiên. Vui lòng thử lại sau.\"}");
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

}
