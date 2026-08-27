package epam.training.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

// Replaces the inline 9.6 lambda (response.sendError(SC_UNAUTHORIZED), empty
// body). This runs inside the servlet filter chain, before Spring MVC's
// @RestControllerAdvice machinery ever gets a request to dispatch to - there
// is no controller method to return a ProblemDetail from, so the JSON has
// to be built and written to the response by hand. Using the app's real
// ObjectMapper bean (not a bare `new ObjectMapper()`) matters: it's the one
// Spring Boot already configured with ProblemDetail's Jackson support, so
// this serializes identically to a ProblemDetail returned from
// GlobalExceptionHandler - same field set, same application/problem+json.
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Full authentication is required to access this resource.");
        // GlobalExceptionHandler's ProblemDetail bodies get `instance`
        // populated automatically by Spring MVC's own handling - that
        // machinery isn't involved here, so it has to be set explicitly to
        // keep the shape identical.
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(problemDetail));
    }
}
