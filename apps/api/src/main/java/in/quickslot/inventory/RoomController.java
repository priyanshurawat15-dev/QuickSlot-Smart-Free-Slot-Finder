package in.quickslot.inventory;

import in.quickslot.identity.AuthService;
import in.quickslot.shared.ApiException;
import in.quickslot.shared.AuditService;
import in.quickslot.shared.WriteLock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
  private final RoomRepository rooms;
  private final JdbcTemplate jdbc;
  private final AuthService auth;
  private final AuditService audit;
  private final WriteLock lock;

  public RoomController(
      RoomRepository rooms,
      JdbcTemplate jdbc,
      AuthService auth,
      AuditService audit,
      WriteLock lock) {
    this.rooms = rooms;
    this.jdbc = jdbc;
    this.auth = auth;
    this.audit = audit;
    this.lock = lock;
  }

  public record RoomInput(
      @NotBlank @Size(max = 80) String code,
      @Pattern(regexp = "CLASSROOM|LT") @NotNull String kind,
      @NotBlank @Size(max = 100) String building,
      @Min(1) @Max(5000) int capacity,
      @Size(max = 60) String floor) {}

  public record RoomDetailsInput(
      @NotNull @Size(max = 100) String building,
      @Min(1) @Max(5000) Integer capacity,
      @Size(max = 60) String floor) {}

  public static Map<String, ?> publicView(Room room) {
    var result = new LinkedHashMap<String, Object>();
    result.put("id", room.id());
    result.put("code", room.code());
    result.put("kind", room.kind());
    result.put("building", room.building());
    result.put("capacity", room.capacity());
    result.put("source_label", room.sourceLabel());
    result.put("floor", room.floor());
    return result;
  }

  @GetMapping
  public List<?> list() {
    return rooms.publicRooms().stream().map(RoomController::publicView).toList();
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @Transactional
  public Map<String, ?> create(@Valid @RequestBody RoomInput input, HttpServletRequest request) {
    var user = auth.admin(request);
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO rooms(id,code,kind,building,capacity,campus_id,department,floor) VALUES"
            + " (?,?,?,?,?,?,?,?)",
        id,
        input.code().strip(),
        input.kind(),
        input.building().strip(),
        input.capacity(),
        user.campusId(),
        user.department(),
        input.floor() == null ? null : input.floor().strip());
    audit.record(user, "ROOM_CREATED", id, Map.of());
    return publicView(rooms.get(id));
  }

  @PutMapping("/{id}/details")
  @Transactional
  public Map<String, ?> updateDetails(
      @PathVariable String id,
      @Valid @RequestBody RoomDetailsInput input,
      HttpServletRequest request) {
    var user = auth.admin(request);
    lock.acquire();
    var room = rooms.get(id);
    if (!room.active() || !room.campusId().equals(user.campusId()))
      throw new ApiException(404, "Room not found.");
    jdbc.update(
        "UPDATE rooms SET building=?,capacity=?,floor=? WHERE id=?",
        input.building().strip(),
        input.capacity(),
        input.floor() == null || input.floor().isBlank() ? null : input.floor().strip(),
        id);
    audit.record(user, "ROOM_DETAILS_VERIFIED", id, Map.of());
    return publicView(rooms.get(id));
  }
}
