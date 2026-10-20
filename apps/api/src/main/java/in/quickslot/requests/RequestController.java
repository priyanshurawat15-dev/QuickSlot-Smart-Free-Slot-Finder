package in.quickslot.requests;

import in.quickslot.identity.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RequestController {
  private final AuthService auth;
  private final RequestService service;
  private final JdbcTemplate jdbc;

  public RequestController(AuthService auth, RequestService service, JdbcTemplate jdbc) {
    this.auth = auth;
    this.service = service;
    this.jdbc = jdbc;
  }

  public record Reason(@Size(max = 500) String reason) {}

  @GetMapping("/requests")
  public List<?> list(HttpServletRequest request) {
    return service.list(auth.require(request));
  }

  @GetMapping("/requests/eligibility")
  public Map<String, ?> eligibility(HttpServletRequest request) {
    return service.eligibility(auth.require(request));
  }

  @PostMapping("/requests")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, ?> submit(
      @Valid @RequestBody RequestInput input,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      HttpServletRequest request) {
    return service.submit(auth.require(request), input, key);
  }

  @PostMapping("/requests/{id}/{action}")
  public Map<String, ?> decide(
      @PathVariable String id,
      @PathVariable String action,
      @Valid @RequestBody(required = false) Reason input,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      HttpServletRequest request) {
    return service.decide(
        auth.require(request),
        id,
        action,
        input == null || input.reason() == null ? "" : input.reason().strip(),
        key);
  }

  @GetMapping("/notifications")
  public List<?> notifications(HttpServletRequest request) {
    var user = auth.require(request);
    return jdbc.query(
        "SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC",
        (row, index) ->
            Map.of(
                "id",
                row.getString("id"),
                "request_id",
                row.getString("request_id"),
                "message",
                row.getString("message"),
                "created_at",
                row.getObject("created_at", OffsetDateTime.class)),
        user.id());
  }
}
