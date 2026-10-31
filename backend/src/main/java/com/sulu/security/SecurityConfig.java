package com.sulu.security;

import com.sulu.common.RateLimitFilter;
import com.sulu.common.SuluProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    /** Role/status are read from the DB on every request, so blocking a user or changing a role takes effect immediately
     *  and a role claim can never be forged or stale. */
    static class DbBackedToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {
        DbBackedToken(Jwt jwt, CurrentUser user) {
            super(jwt, user, jwt, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
            setAuthenticated(true);
        }
        @Override public java.util.Map<String, Object> getTokenAttributes() { return getToken().getClaims(); }
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JdbcClient db, RateLimitFilter rateLimit, SuluProperties props, JwtService jwtService) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(cors(props)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(rateLimit, BasicAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/auth/**", "/api/payments/webhook/**", "/media/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories", "/api/search", "/api/availability",
                        "/api/availability/next", "/api/businesses", "/api/businesses/*", "/api/services", "/api/services/*",
                        "/api/professionals", "/api/professionals/*", "/api/reviews", "/api/config").permitAll()
                .requestMatchers("/api/admin/**").hasRole("PLATFORM_ADMIN")
                .requestMatchers("/api/biz/**", "/api/business/**").hasRole("BUSINESS_OWNER")
                .requestMatchers("/api/pro/**").hasRole("PROFESSIONAL")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j
                .decoder(jwtService.decoder())
                .jwtAuthenticationConverter(jwt -> {
                    long id;
                    try { id = Long.parseLong(jwt.getSubject()); } catch (Exception e) { throw new BadCredentialsException("bad subject"); }
                    var rows = db.sql("SELECT role, status FROM users WHERE id = :id").param("id", id).query().listOfRows();
                    if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).get("status"))) throw new BadCredentialsException("inactive");
                    return (AbstractAuthenticationToken) new DbBackedToken(jwt, new CurrentUser(id, (String) rows.get(0).get("role")));
                })))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401); res.setContentType("application/json");
                    res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403); res.setContentType("application/json");
                    res.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                }));
        return http.build();
    }

    private CorsConfigurationSource cors(SuluProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(props.corsOrigins().split(",")));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Accept-Language"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }
}
