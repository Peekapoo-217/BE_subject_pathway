package vn.edu.thesis.BE_subject_pathway.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import vn.edu.thesis.BE_subject_pathway.service.admin.RedisSessionStore;
import vn.edu.thesis.BE_subject_pathway.service.admin.TokenService;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AuthAccountRepository;

/**
 * cau hinh Spring Security cho REST API public:
 * - CORS bat qua filter chain cua Security, origin doc tu env
 *   CORS_ALLOWED_ORIGINS (application.yml: app.cors.allowed-origins),
 *   ho nhieu gia tri phan cach bang dau phay.
 * - Cookie auth endpoints require CSRF; other APIs authenticate only via Bearer tokens.
 * - Default-deny: moi path khac /api/** va /actuator/** yeu cau auth.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${app.cors.allowed-origins:http://localhost:4200}")
            String allowedOriginsProperty) {
        this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<BearerTokenFilter> tokenFilter,
            @Value("${app.auth.cookie-secure:true}") boolean secureCookie) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secureCookie));
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .requireCsrfProtectionMatcher(request -> request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/v1/auth/")
                                && !List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"message\":\"Vui lòng đăng nhập để tiếp tục.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"message\":\"Yêu cầu không được phép. Vui lòng tải lại trang và thử lại.\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/admin/documents/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/high-schools/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/admissions/**", "/api/v1/high-schools/**", "/api/v1/subjects/**").permitAll()
                        .requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated());
        if (tokenFilter.getIfAvailable() != null) {
            http.addFilterBefore(tokenFilter.getIfAvailable(), UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
    BearerTokenFilter bearerTokenFilter(TokenService tokenService, AuthAccountRepository accounts,
                                       RedisSessionStore sessions) { return new BearerTokenFilter(tokenService, accounts, sessions); }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
