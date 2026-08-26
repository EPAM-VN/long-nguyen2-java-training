package epam.training.demo.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Real config now: JWT is the only auth mechanism. Basic Auth (Step 9.2)
// was only ever a bridge to prove UserDetailsService/AuthenticationManager
// wiring worked before JwtService existed - leaving it enabled alongside
// JWT would mean two independently-valid ways to authenticate the same API,
// which silently masks bugs in whichever mechanism isn't the one currently
// being tested. STATELESS: no HttpSession is created or consulted, every
// request re-proves identity via the Authorization header - that's the
// point of a token-based API. CSRF stays disabled for the reason it always
// was: there's no session to ride.
@Configuration
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtService);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                // Without httpBasic()/formLogin() registering an
                // AuthenticationEntryPoint, Spring Security falls back to
                // Http403ForbiddenEntryPoint for unauthenticated requests -
                // wrong for a token API, where "you're not authenticated"
                // (401) and "you're authenticated but not allowed" (403)
                // are different things worth distinguishing.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
