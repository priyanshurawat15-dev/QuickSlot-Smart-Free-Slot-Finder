package in.quickslot.clubs;

import in.quickslot.identity.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {
  private final ClubService service;
  private final AuthService auth;

  public ClubController(ClubService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  public record Review(@Size(max = 500) String reason) {}

  @GetMapping
  public List<?> list() {
    return service.list();
  }

  @GetMapping("/{slug}")
  public Map<String, Object> profile(@PathVariable String slug) {
    return service.profile(slug);
  }

  @GetMapping("/{slug}/membership")
  public Map<String, Object> membership(@PathVariable String slug, HttpServletRequest request) {
    return service.membership(auth.require(request), slug);
  }

  @PostMapping("/{slug}/join")
  public Map<String, Object> join(@PathVariable String slug, HttpServletRequest request) {
    return service.join(auth.require(request), slug);
  }

  @GetMapping("/{slug}/memberships")
  public List<?> memberships(@PathVariable String slug, HttpServletRequest request) {
    return service.memberships(auth.require(request), slug);
  }

  @PostMapping("/{slug}/memberships/{id}/{action}")
  public Map<String, Object> decide(
      @PathVariable String slug,
      @PathVariable String id,
      @PathVariable String action,
      @Valid @RequestBody(required = false) Review input,
      HttpServletRequest request) {
    String reason = input == null || input.reason() == null ? "" : input.reason().strip();
    return service.decide(auth.require(request), slug, id, action, reason);
  }
}
