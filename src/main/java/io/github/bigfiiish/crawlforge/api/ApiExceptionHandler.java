package io.github.bigfiiish.crawlforge.api;

import io.github.bigfiiish.crawlforge.crawl.UnsafeUrlException;
import io.github.bigfiiish.crawlforge.service.CrawlNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CrawlNotFoundException.class)
    public ResponseEntity<ApiError> notFound(CrawlNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, UnsafeUrlException.class})
    public ResponseEntity<ApiError> badRequest(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Request validation failed", fields);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fields));
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            Map<String, String> fields) {}
}
