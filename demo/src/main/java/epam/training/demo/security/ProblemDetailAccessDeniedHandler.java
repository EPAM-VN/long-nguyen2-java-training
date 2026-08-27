package epam.training.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

// Same reasoning as ProblemDetailAuthenticationEntryPoint, for the 403 case.
// Worth knowing: this fires only for an AccessDeniedException thrown from
// the filter chain itself (e.g. a future URL-pattern rule like
// .requestMatchers(...).hasRole(...)) - it is NOT what currently produces
// the 403 for a denied @PreAuthorize check, since that throws
// AuthorizationDeniedException from inside the controller method
// invocation, which GlobalExceptionHandler's own AccessDeniedException
// handler (Step 9.9) intercepts first. Both are correct and both stay:
// this app has no URL-pattern-based role rule today, but the moment one
// exists, this is what answers it - and it's meant to shape that response
// exactly like every other error this API returns, same as the 401 case.
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource.");
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(problemDetail));
    }
}
