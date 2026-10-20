package in.quickslot.timetables;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.quickslot.identity.AuthService;
import in.quickslot.shared.ApiException;
import in.quickslot.shared.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/timetables")
public class TimetableController {
  private final TimetableService service;
  private final AuthService auth;
  private final ObjectMapper json;

  public TimetableController(TimetableService service, AuthService auth, ObjectMapper json) {
    this.service = service;
    this.auth = auth;
    this.json = json;
  }

  public record FacultyInput(String id, String name) {}

  public record PublishInput(Integer expectedRevision) {}

  public record RetireInput(Integer expectedRevision, LocalDate effectiveFrom) {}

  @GetMapping
  public Object list(HttpServletRequest request) {
    return service.list(auth.admin(request));
  }

  @PostMapping
  public ResponseEntity<?> upload(
      HttpServletRequest request,
      @RequestParam MultipartFile file,
      @RequestParam("scope_key") String scopeKey,
      @RequestParam("valid_from") LocalDate from,
      @RequestParam(value = "valid_until", required = false) LocalDate until,
      @RequestParam(value = "coverage_room_ids", defaultValue = "[]") String coverage)
      throws IOException {
    var user = auth.admin(request);
    if (file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(413, "Timetable upload exceeds 10 MiB");
    List<String> rooms;
    try {
      rooms = json.readValue(coverage, new TypeReference<List<String>>() {});
    } catch (IOException error) {
      throw new ApiException(422, "Coverage must be an array of room IDs");
    }
    return ResponseEntity.status(201)
        .body(
            service.upload(
                user, file.getOriginalFilename(), file.getBytes(), scopeKey, from, until, rooms));
  }

  @GetMapping("/faculty")
  public Object faculty(HttpServletRequest request) {
    return service.teachers(auth.admin(request));
  }

  @PostMapping("/faculty")
  public ResponseEntity<?> addFaculty(
      @RequestBody FacultyInput payload, HttpServletRequest request) {
    return ResponseEntity.status(201)
        .body(service.addFaculty(payload.id(), payload.name(), auth.admin(request)));
  }

  @GetMapping("/teachers")
  public Object teachers(HttpServletRequest request) {
    return service.teachers(staff(request));
  }

  @GetMapping("/teachers/{id}/schedule")
  public Object teacher(
      @PathVariable String id, @RequestParam LocalDate date, HttpServletRequest request) {
    return service.teacherSchedule(id, date, staff(request));
  }

  private AuthenticatedUser staff(HttpServletRequest request) {
    var user = auth.require(request);
    if (!Set.of("FACULTY", "COORDINATOR", "HOD", "ADMIN").contains(user.role()))
      throw new ApiException(403, "Faculty access is restricted to university staff.");
    return user;
  }

  @GetMapping("/{id}")
  public Object detail(@PathVariable String id, HttpServletRequest request) {
    return service.detail(id, auth.admin(request));
  }

  @PutMapping("/{id}/review")
  public Object review(
      @PathVariable String id,
      @RequestBody TimetableService.Review payload,
      HttpServletRequest request) {
    return service.review(id, payload, auth.admin(request));
  }

  @PostMapping("/{id}/publish")
  public Object publish(
      @PathVariable String id, @RequestBody PublishInput payload, HttpServletRequest request) {
    var user = auth.admin(request);
    if (payload.expectedRevision() == null || payload.expectedRevision() < 0)
      throw new ApiException(422, "Provide expected_revision");
    return service.publish(id, payload.expectedRevision(), user);
  }

  @PostMapping("/{id}/retire")
  public Object retire(
      @PathVariable String id, @RequestBody RetireInput payload, HttpServletRequest request) {
    var user = auth.admin(request);
    if (payload.expectedRevision() == null || payload.expectedRevision() < 0)
      throw new ApiException(422, "Provide expected_revision");
    return service.retire(id, payload.expectedRevision(), payload.effectiveFrom(), user);
  }

  @GetMapping("/{id}/source")
  public ResponseEntity<byte[]> source(@PathVariable String id, HttpServletRequest request) {
    var source = service.source(id, auth.admin(request));
    String type =
        source.filename().toLowerCase().endsWith(".pdf")
            ? "application/pdf"
            : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, type)
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename*=UTF-8''"
                + URLEncoder.encode(source.filename(), StandardCharsets.UTF_8).replace("+", "%20"))
        .body(source.content());
  }
}
