package epam.training.demo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Real exception/BindingResult instances throughout, not mocks: every
// handler's job is to read specific fields off these objects (getMessage(),
// getBindingResult().getFieldErrors(), etc.) - a mock would just be told
// what to return, proving nothing about whether the handler reads the right
// thing. A BeanPropertyBindingResult with real FieldErrors is barely more
// code than mocking BindingResult and is the only way to exercise the merge
// branch (two FieldErrors on one field) at all.
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NotFoundException -> 404 with the exception's own message as detail")
    void handleNotFound() {
        ProblemDetail problemDetail = handler.handleNotFound(new NotFoundException("Project 42 not found"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problemDetail.getDetail()).isEqualTo("Project 42 not found");
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException -> 409 with the fixed conflict message")
    void handleOptimisticLocking() {
        ProblemDetail problemDetail = handler.handleOptimisticLocking(
                new ObjectOptimisticLockingFailureException(Object.class, 42L));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getDetail())
                .isEqualTo("The resource was modified by someone else - reload it and try again.");
    }

    @Test
    @DisplayName("AuthenticationException (via BadCredentialsException subclass) -> 401")
    void handleAuthentication() {
        ProblemDetail problemDetail = handler.handleAuthentication(new BadCredentialsException("Bad credentials"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getDetail()).isEqualTo("Invalid username or password.");
    }

    @Test
    @DisplayName("DataIntegrityViolationException -> 409")
    void handleDataIntegrityViolation() {
        ProblemDetail problemDetail = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getDetail()).isEqualTo("A record with the same unique value already exists.");
    }

    @Test
    @DisplayName("AccessDeniedException (via AuthorizationDeniedException subclass) -> 403")
    void handleAccessDenied() {
        // AuthorizationDeniedException's (String, AuthorizationResult) constructor
        // needs a concrete AuthorizationResult - AuthorizationDecision is the
        // simplest one, just wrapping the granted/denied boolean.
        ProblemDetail problemDetail = handler.handleAccessDenied(
                new AuthorizationDeniedException("Denied", new AuthorizationDecision(false)));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problemDetail.getDetail()).isEqualTo("Access is denied.");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException with one FieldError -> single message in the errors map")
    void handleValidation_singleFieldError() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        ProblemDetail problemDetail = handler.handleValidation(toException(bindingResult));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getProperties()).containsEntry("errors",
                Map.of("name", "must not be blank"));
    }

    @Test
    @DisplayName("two FieldErrors on the same field merge as 'first; second'")
    void handleValidation_mergesTwoFieldErrorsOnSameField() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));
        bindingResult.addError(new FieldError("request", "email", "must be a well-formed email address"));

        ProblemDetail problemDetail = handler.handleValidation(toException(bindingResult));

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problemDetail.getProperties().get("errors");
        assertThat(errors).containsEntry("email", "must not be blank; must be a well-formed email address");
    }

    @Test
    @DisplayName("a FieldError with a null default message falls back to 'invalid value'")
    void handleValidation_nullDefaultMessageFallsBackToInvalidValue() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", null, false, null, null, null));

        ProblemDetail problemDetail = handler.handleValidation(toException(bindingResult));

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problemDetail.getProperties().get("errors");
        assertThat(errors).containsEntry("name", "invalid value");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException (e.g. ?status=BANANA) -> 400, not the catch-all 500")
    void handleTypeMismatch() throws NoSuchMethodException {
        // Found via TaskControllerTest's ?status=BANANA case: without this
        // handler, the catch-all @ExceptionHandler(Exception.class) caught
        // this first and turned a client input error into a generic 500 -
        // same shadowing pattern as AuthorizationDeniedException.
        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "BANANA", String.class, "status", methodParameter, null);

        ProblemDetail problemDetail = handler.handleTypeMismatch(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getDetail()).isEqualTo("Invalid value for parameter 'status'.");
    }

    @Test
    @DisplayName("catch-all Exception -> 500 with a generic detail, nothing exception-specific leaked")
    void handleUnexpected() {
        ProblemDetail problemDetail = handler.handleUnexpected(
                new RuntimeException("db password is hunter2, connection string leaked here"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problemDetail.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problemDetail.getDetail()).doesNotContain("hunter2");
    }

    // A MethodParameter is only used by the exception to build a debug
    // message that this handler never reads - any real (method, index) pair
    // satisfies the constructor, so this method exists purely to give
    // reflection something to point at.
    private void dummyMethod(String arg) {
    }

    private MethodArgumentNotValidException toException(BindingResult bindingResult) {
        try {
            MethodParameter methodParameter = new MethodParameter(
                    GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
            return new MethodArgumentNotValidException(methodParameter, bindingResult);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
