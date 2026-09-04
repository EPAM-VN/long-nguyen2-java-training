package epam.training.demo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// Basic Auth (Step 9.2) was only ever a bridge to prove UserDetailsService/
// AuthenticationManager wiring worked before JwtService existed - leaving
// it enabled alongside JWT would mean two independently-valid ways to
// authenticate the same API, which silently masks bugs in whichever
// mechanism isn't the one currently being tested. STATELESS: no HttpSession
// is created or consulted, every request re-proves identity via the
// Authorization header - that's the point of a token-based API. CSRF stays
// disabled for the reason it always was: there's no session to ride.
//
// Step 9.8: JwtAuthFilter (hand-rolled, Step 9.5/9.6) is gone. Step 9.7
// proved Spring's own oauth2ResourceServer support - wired to the exact
// same JwtDecoder - agreed with the hand-rolled filter on every case, which
// made the filter pure duplication once proven. Authority mapping (the
// JWT's "roles" claim -> GrantedAuthority) is now Spring's job via
// JwtAuthenticationConverter/JwtGrantedAuthoritiesConverter below, not a
// manually-built UsernamePasswordAuthenticationToken.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // No real frontend yet - localhost:5173 is a plausible placeholder (Vite's
    // default dev server port). Explicitly NOT "*": a wildcard origin would
    // accept requests from any site at all, defeating the point of CORS as
    // a same-origin-policy relaxation you actually control.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        // Roles were already stored as full authority strings ("ROLE_USER")
        // when the token was minted (AuthService.login pulls them straight
        // off Authentication.getAuthorities()), so no prefix should be
        // added here - that would double it up into "ROLE_ROLE_USER".
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                     JwtAuthenticationConverter jwtAuthenticationConverter,
                                                     CorsConfigurationSource corsConfigurationSource,
                                                     ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                                     ProblemDetailAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                // Must be configured on HttpSecurity itself (not left to a
                // separate WebMvcConfigurer bean) so Spring Security's own
                // CorsFilter is spliced into the chain ahead of
                // authorizeHttpRequests - that's what makes a preflight
                // OPTIONS request get answered before the authorization
                // rules ever see it, rather than being rejected as an
                // unauthenticated request to a protected endpoint.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Deliberately two rules, most-specific first: health
                        // must be reachable with no token at all (that's the
                        // point of a liveness/readiness probe - it runs
                        // before anything else can prove it's up), while the
                        // rest of the actuator surface (info, metrics) is
                        // gated to ADMIN. show-details on health (see
                        // application.yaml) is a second, independent gate on
                        // top of this - reachability here is not the same as
                        // seeing component details.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Both give a filter-chain-level denial the same
                // application/problem+json shape GlobalExceptionHandler
                // already gives controller-layer errors - a client can't
                // tell which layer rejected the request just from the body
                // format, only from status/detail.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
