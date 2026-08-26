package epam.training.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Not a @Component: registering this via component scanning would let
// Spring Security auto-detect and place it wherever it likes, which is not
// necessarily where a filter reading raw Authorization headers needs to
// sit. It gets wired explicitly with addFilterBefore(...) in Step 9.6,
// anchored to a specific position in the chain printed back in Step 9.1.
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parseToken(token);

            // The roles claim round-trips through JSON as a raw List (of
            // Strings, in practice, since that's all JwtService ever puts
            // in it) - not a List<GrantedAuthority>, so each element has to
            // be wrapped explicitly rather than cast.
            List<?> rawRoles = claims.get("roles", List.class);
            List<GrantedAuthority> authorities = rawRoles.stream()
                    .map(String::valueOf)
                    .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            // Expired/malformed/bad-signature - leave the context empty and
            // let the chain continue. Whatever runs after this filter
            // (AuthorizationFilter) rejects the request as unauthenticated;
            // this filter's job is only to populate the context when it
            // genuinely can, never to decide what an invalid token means
            // for the response.
        }

        filterChain.doFilter(request, response);
    }
}
