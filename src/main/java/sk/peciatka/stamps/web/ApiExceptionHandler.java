package sk.peciatka.stamps.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import sk.peciatka.stamps.domain.DomainException;
import sk.peciatka.stamps.service.NotFoundException;

/** Errors as RFC 9457 problem details with a machine-readable "code" the frontend can translate. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, e.code());
        e.details().forEach(pd::setProperty);
        return pd;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** A non-UUID id in the URL is "not found", not a server error. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail badId(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.NOT_FOUND, "not_found");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation");
        pd.setProperty("fields", e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList());
        return pd;
    }

    /** Two simultaneous sign-ups with the same phone: the unique index wins, the second gets 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "duplicate");
    }

    private static ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(code);
        pd.setProperty("code", code);
        return pd;
    }
}
