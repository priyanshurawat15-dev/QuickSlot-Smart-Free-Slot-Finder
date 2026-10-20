package in.quickslot.shared;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<?> application(ApiException error) {
    return ResponseEntity.status(error.status()).body(Map.of("detail", error.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> validation(MethodArgumentNotValidException error) {
    var details =
        error.getBindingResult().getFieldErrors().stream()
            .map(field -> field.getField() + ": " + field.getDefaultMessage())
            .toList();
    return ResponseEntity.unprocessableEntity().body(Map.of("detail", details));
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<?> malformed(Exception error) {
    return ResponseEntity.badRequest().body(Map.of("detail", "Invalid request format."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict(DataIntegrityViolationException error) {
    return ResponseEntity.status(409)
        .body(Map.of("detail", "A conflicting record already exists. Refresh and try again."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<?> tooLarge(MaxUploadSizeExceededException error) {
    return ResponseEntity.status(413).body(Map.of("detail", "Upload exceeds 20 MB."));
  }

  @ExceptionHandler(TransientDataAccessException.class)
  public ResponseEntity<?> retryable(TransientDataAccessException error) {
    return ResponseEntity.status(503)
        .header("Retry-After", "1")
        .body(Map.of("detail", "Another scheduling change is being processed. Try again."));
  }
}
