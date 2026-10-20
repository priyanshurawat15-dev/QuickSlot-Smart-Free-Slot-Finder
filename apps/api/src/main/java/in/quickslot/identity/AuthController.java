package in.quickslot.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  public record Credentials(
      @NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 72) String password) {}

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody Credentials input, HttpServletRequest request) {
    var result = auth.login(input.email(), input.password(), request);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, result.cookie())
        .body(Map.of("user", result.user(), "csrf_token", result.csrfToken()));
  }

  @GetMapping("/me")
  public Map<String, ?> me(HttpServletRequest request) {
    var user = auth.require(request);
    return Map.of("user", user, "csrf_token", request.getAttribute("csrf_token"));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request) {
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, auth.logout(request))
        .body(Map.of("message", "Logged out."));
  }
}
