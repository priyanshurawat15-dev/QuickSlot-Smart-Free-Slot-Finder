package in.quickslot.requests;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.quickslot.availability.AvailabilityService;
import in.quickslot.identity.AuthService;
import in.quickslot.inventory.Room;
import in.quickslot.inventory.RoomRepository;
import in.quickslot.shared.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestService {
  private final JdbcTemplate jdbc;
  private final RequestRepository requests;
  private final RoomRepository rooms;
  private final AvailabilityService availability;
  private final AuditService audit;
  private final WriteLock lock;
  private final ObjectMapper json;

  public RequestService(
      JdbcTemplate jdbc,
      RequestRepository requests,
      RoomRepository rooms,
      AvailabilityService availability,
      AuditService audit,
      WriteLock lock,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.requests = requests;
    this.rooms = rooms;
    this.availability = availability;
    this.audit = audit;
    this.lock = lock;
    this.json = json;
  }

  public boolean canDecide(AuthenticatedUser user, Room room) {
    if (!user.campusId().equals(room.campusId())) return false;
    if (user.role().equals("HOD")) return user.department().equals(room.department());
    if (!user.role().equals("COORDINATOR")) return false;
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM allocations WHERE user_id=? AND room_id=?)",
            Boolean.class,
            user.id(),
            room.id()));
  }

  public List<Map<String, ?>> list(AuthenticatedUser user) {
    return requests.all().stream()
        .filter(
            item ->
                item.requesterId().equals(user.id())
                    || canDecide(user, rooms.get(item.roomId()))
                    || (user.role().equals("ADMIN")
                        && user.campusId().equals(rooms.get(item.roomId()).campusId())))
        .map(this::view)
        .toList();
  }

  public Map<String, ?> view(BookingRequest item) {
    var output = new LinkedHashMap<String, Object>();
    output.put("id", item.id());
    output.put("requester_id", item.requesterId());
    output.put(
        "requester_name",
        jdbc.queryForObject(
            "SELECT full_name FROM users WHERE id=?", String.class, item.requesterId()));
    output.put("room_id", item.roomId());
    output.put("room_code", rooms.get(item.roomId()).code());
    output.put("date", item.date());
    output.put("start_time", item.startTime());
    output.put("end_time", item.endTime());
    output.put("purpose", item.purpose());
    output.put("attendance", item.attendance());
    output.put("status", item.status());
    output.put(
        "history",
        jdbc.query(
            "SELECT d.action,d.reason,d.created_at,u.full_name FROM decisions d JOIN users u ON"
                + " u.id=d.actor_id WHERE request_id=? ORDER BY d.created_at,d.id",
            (row, index) ->
                Map.of(
                    "action",
                    row.getString("action"),
                    "reason",
                    row.getString("reason"),
                    "created_at",
                    row.getObject("created_at", OffsetDateTime.class),
                    "actor_name",
                    row.getString("full_name")),
            item.id()));
    return output;
  }

  private List<AuthenticatedUser> reviewers(Room room, String except) {
    return jdbc
        .query(
            "SELECT * FROM users WHERE active AND campus_id=? AND id<>?",
            AuthService::user,
            room.campusId(),
            except)
        .stream()
        .filter(user -> canDecide(user, room))
        .toList();
  }

  private String fingerprint(Object value) {
    try {
      return AuthService.hash(json.writeValueAsString(value));
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException(error);
    }
  }

  private Optional<String> repeated(AuthenticatedUser user, String key, String fingerprint) {
    if (key == null) return Optional.empty();
    if (key.length() > 150 || key.isBlank())
      throw new ApiException(422, "Invalid idempotency key.");
    var entries =
        jdbc.queryForList(
            "SELECT * FROM idempotency_records WHERE user_id=? AND key=?", user.id(), key);
    if (entries.isEmpty()) return Optional.empty();
    var entry = entries.getFirst();
    if (!entry.get("payload_hash").equals(fingerprint))
      throw new ApiException(409, "Idempotency key was used for different input.");
    return Optional.of(entry.get("request_id").toString());
  }

  private void remember(AuthenticatedUser user, String key, String fingerprint, String id) {
    if (key != null)
      jdbc.update(
          "INSERT INTO idempotency_records(user_id,key,payload_hash,request_id) VALUES (?,?,?,?)",
          user.id(),
          key,
          fingerprint,
          id);
  }

  private void event(AuthenticatedUser user, String id, String action, String reason) {
    jdbc.update(
        "INSERT INTO decisions(id,request_id,actor_id,action,reason) VALUES (?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        user.id(),
        action,
        reason);
    audit.record(user, "REQUEST_" + action, id, Map.of("reason", reason));
  }

  private void notify(String userId, String requestId, String message) {
    jdbc.update(
        "INSERT INTO notifications(id,user_id,request_id,message) VALUES (?,?,?,?)",
        UUID.randomUUID().toString(),
        userId,
        requestId,
        message);
  }

  @Transactional
  public Map<String, ?> submit(AuthenticatedUser user, RequestInput input, String rawKey) {
    if (!Set.of("FACULTY", "CLUB_HEAD", "COORDINATOR", "HOD").contains(user.role()))
      throw new ApiException(403, "Only faculty and verified club heads may request rooms.");
    if (input.purpose().strip().length() < 3)
      throw new ApiException(422, "Describe the purpose in at least three characters.");
    AvailabilityService.validateWindow(input.startTime(), input.endTime());
    if (input.startTime().getMinute() != 0
        || input.endTime().getMinute() != 0
        || input.startTime().getSecond() != 0
        || input.endTime().getSecond() != 0
        || input.startTime().getNano() != 0
        || input.endTime().getNano() != 0) {
      throw new ApiException(422, "Bookings must start and end on the hour.");
    }
    if (input.startTime().isBefore(LocalTime.of(8, 0))
        || input.endTime().isAfter(LocalTime.of(18, 0)))
      throw new ApiException(422, "Booking hours are 08:00–18:00.");
    Room room = rooms.get(input.roomId());
    if (!room.active() || !room.campusId().equals(user.campusId()))
      throw new ApiException(404, "Room not found.");
    if (room.capacity() != null && input.attendance() > room.capacity())
      throw new ApiException(422, "Attendance exceeds room capacity.");
    lock.acquire();
    String key = rawKey == null ? null : "submit:" + rawKey;
    String fingerprint = fingerprint(input);
    var repeated = repeated(user, key, fingerprint);
    if (repeated.isPresent()) return view(requests.get(repeated.get(), false));
    if (!input
        .date()
        .atTime(input.startTime())
        .atZone(AvailabilityService.INDIA)
        .isAfter(ZonedDateTime.now(AvailabilityService.INDIA)))
      throw new ApiException(422, "Choose a future time.");
    var reviewers = reviewers(room, user.id());
    if (reviewers.isEmpty()) throw new ApiException(409, "No reviewer is assigned to this room.");
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " booking_requests(id,requester_id,room_id,date,start_time,end_time,purpose,attendance)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        id,
        user.id(),
        room.id(),
        input.date(),
        input.startTime(),
        input.endTime(),
        input.purpose().strip(),
        input.attendance());
    event(user, id, "SUBMITTED", "");
    reviewers.forEach(reviewer -> notify(reviewer.id(), id, "New room request for " + room.code()));
    remember(user, key, fingerprint, id);
    return view(requests.get(id, false));
  }

  @Transactional
  public Map<String, ?> decide(
      AuthenticatedUser user, String id, String action, String reason, String rawKey) {
    if (!Set.of("approve", "reject", "forward", "withdraw", "cancel").contains(action))
      throw new ApiException(404, "Unknown action.");
    lock.acquire();
    BookingRequest item = requests.get(id, true);
    Room room = rooms.get(item.roomId());
    boolean owner = item.requesterId().equals(user.id());
    boolean authority = canDecide(user, room);
    String key = rawKey == null ? null : "decision:" + rawKey;
    String fingerprint = fingerprint(List.of(id, action, reason));
    var repeated = repeated(user, key, fingerprint);
    if (repeated.isPresent()) return view(requests.get(repeated.get(), false));

    if (Set.of("approve", "reject", "forward").contains(action)) {
      if (!authority || owner)
        throw new ApiException(403, "An independent authorized reviewer must decide this request.");
      if (!Set.of("PENDING", "FORWARDED").contains(item.status()))
        throw new ApiException(409, "Request has already been decided.");
      if (item.status().equals("FORWARDED") && !user.role().equals("HOD"))
        throw new ApiException(403, "Forwarded requests require HOD decision.");
      if (action.equals("forward") && !user.role().equals("COORDINATOR"))
        throw new ApiException(403, "Only the responsible coordinator may forward.");
      if (Set.of("reject", "forward").contains(action) && reason.isBlank())
        throw new ApiException(422, "Provide a reason.");
    } else if (action.equals("withdraw")) {
      if (!owner) throw new ApiException(403, "Only the requester may withdraw.");
      if (!Set.of("PENDING", "FORWARDED").contains(item.status()))
        throw new ApiException(409, "Only pending requests may be withdrawn.");
    } else {
      if (!owner && !authority)
        throw new ApiException(403, "Not authorized to cancel this booking.");
      if (!item.status().equals("APPROVED"))
        throw new ApiException(409, "Only approved bookings may be cancelled.");
      if (reason.isBlank()) throw new ApiException(422, "Provide a cancellation reason.");
    }

    if (action.equals("approve")) {
      if (room.capacity() == null)
        throw new ApiException(409, "Room capacity must be verified before approval.");
      if (!room.active() || item.attendance() > room.capacity())
        throw new ApiException(409, "Room is inactive or capacity has changed.");
      if (!item.date()
          .atTime(item.startTime())
          .atZone(AvailabilityService.INDIA)
          .isAfter(ZonedDateTime.now(AvailabilityService.INDIA)))
        throw new ApiException(409, "Requested time has passed.");
      String state = availability.state(room.id(), item.date(), item.startTime(), item.endTime());
      if (!state.equals("AVAILABLE"))
        throw new ApiException(
            409,
            state.equals("UNKNOWN")
                ? "Timetable coverage is incomplete."
                : "The room is occupied.");
      jdbc.update(
          "INSERT INTO occupancies(id,room_id,starts_at,ends_at,kind,source_id) VALUES"
              + " (?,?,?,?,?,?)",
          UUID.randomUUID().toString(),
          room.id(),
          item.date().atTime(item.startTime()).atZone(AvailabilityService.INDIA).toOffsetDateTime(),
          item.date().atTime(item.endTime()).atZone(AvailabilityService.INDIA).toOffsetDateTime(),
          "BOOKING",
          id);
    }
    if (action.equals("forward")) {
      var hods =
          reviewers(room, item.requesterId()).stream()
              .filter(reviewer -> reviewer.role().equals("HOD"))
              .toList();
      if (hods.isEmpty()) throw new ApiException(409, "No HOD reviewer is configured.");
      hods.forEach(hod -> notify(hod.id(), id, "Request forwarded for HOD decision: " + reason));
    }
    if (action.equals("cancel"))
      jdbc.update("UPDATE occupancies SET active=false WHERE source_id=?", id);
    String status =
        switch (action) {
          case "approve" -> "APPROVED";
          case "reject" -> "REJECTED";
          case "forward" -> "FORWARDED";
          case "withdraw" -> "WITHDRAWN";
          default -> "CANCELLED";
        };
    jdbc.update("UPDATE booking_requests SET status=? WHERE id=?", status, id);
    event(user, id, status, reason);
    notify(
        item.requesterId(),
        id,
        "Request " + status.toLowerCase(Locale.ROOT) + (reason.isBlank() ? "" : ": " + reason));
    remember(user, key, fingerprint, id);
    return view(requests.get(id, false));
  }
}
