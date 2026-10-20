package in.quickslot.timetables;

import static in.quickslot.timetables.ImportData.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.quickslot.availability.BusyInterval;
import in.quickslot.availability.TimetableLookup;
import in.quickslot.shared.ApiException;
import in.quickslot.shared.AuditService;
import in.quickslot.shared.AuthenticatedUser;
import in.quickslot.shared.WriteLock;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimetableService implements TimetableLookup {
  private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final WriteLock writeLock;
  private final AuditService audit;

  public TimetableService(
      JdbcTemplate jdbc, ObjectMapper json, WriteLock writeLock, AuditService audit) {
    this.jdbc = jdbc;
    this.json = json;
    this.writeLock = writeLock;
    this.audit = audit;
  }

  public record Revision(
      String id,
      String scopeId,
      String scopeKey,
      String campusId,
      String filename,
      String sha256,
      String status,
      int version,
      LocalDate validFrom,
      LocalDate validUntil,
      List<Session> sessions,
      List<Issue> issues,
      List<String> coverageRoomIds,
      boolean acknowledged,
      OffsetDateTime publishedAt) {}

  public record Review(
      Integer expectedRevision,
      List<Session> sessions,
      List<String> coverageRoomIds,
      boolean acknowledgeSource) {}

  public record Window(
      String id, String scopeId, String revisionId, LocalDate validFrom, LocalDate validUntil) {}

  public record Source(String filename, byte[] content) {}

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (IOException error) {
      throw new IllegalArgumentException("Timetable data cannot be serialized", error);
    }
  }

  private <T> T decode(String value, TypeReference<T> type) {
    try {
      return json.readValue(value, type);
    } catch (IOException error) {
      throw new IllegalStateException("Stored timetable data is invalid", error);
    }
  }

  private List<Revision> query(String suffix, Object... args) {
    return jdbc.query(
        "SELECT r.*,s.scope_key,s.campus_id FROM timetable_revisions r JOIN timetable_scopes s ON"
            + " s.id=r.scope_id "
            + suffix,
        (row, number) ->
            new Revision(
                row.getString("id"),
                row.getString("scope_id"),
                row.getString("scope_key"),
                row.getString("campus_id"),
                row.getString("filename"),
                row.getString("sha256"),
                row.getString("status"),
                row.getInt("version"),
                row.getObject("valid_from", LocalDate.class),
                row.getObject("valid_until", LocalDate.class),
                decode(row.getString("sessions"), new TypeReference<List<Session>>() {}),
                decode(row.getString("issues"), new TypeReference<List<Issue>>() {}),
                decode(row.getString("coverage_room_ids"), new TypeReference<List<String>>() {}),
                row.getBoolean("acknowledged"),
                row.getObject("published_at", OffsetDateTime.class)),
        args);
  }

  private Revision find(String revisionId, String campus, boolean lock) {
    var found =
        query(
            "WHERE r.id=? AND s.campus_id=?" + (lock ? " FOR UPDATE OF r" : ""),
            revisionId,
            campus);
    if (found.isEmpty()) throw new ApiException(404, "Timetable revision not found");
    return found.getFirst();
  }

  public Map<String, Object> detail(String revisionId, AuthenticatedUser user) {
    return detail(find(revisionId, user.campusId(), false));
  }

  private Map<String, Object> detail(Revision revision) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", revision.id());
    result.put("scope_key", revision.scopeKey());
    result.put("filename", revision.filename());
    result.put("sha256", revision.sha256());
    result.put("status", revision.status());
    result.put("version", revision.version());
    result.put("valid_from", revision.validFrom());
    result.put("valid_until", revision.validUntil());
    result.put("sessions", revision.sessions());
    result.put("issues", revision.issues());
    result.put("coverage_room_ids", revision.coverageRoomIds());
    result.put("acknowledged", revision.acknowledged());
    result.put("published_at", revision.publishedAt());
    result.put(
        "publication_windows",
        windows("WHERE revision_id=?", revision.id()).stream()
            .map(
                window ->
                    Map.of("valid_from", window.validFrom(), "valid_until", window.validUntil()))
            .toList());
    return result;
  }

  public List<Map<String, Object>> list(AuthenticatedUser user) {
    return query("WHERE s.campus_id=? ORDER BY r.created_at DESC", user.campusId()).stream()
        .map(this::detail)
        .toList();
  }

  public Source source(String revisionId, AuthenticatedUser user) {
    var record = find(revisionId, user.campusId(), false);
    return new Source(
        record.filename(),
        jdbc.queryForObject(
            "SELECT content FROM timetable_revisions WHERE id=?", byte[].class, revisionId));
  }

  @Transactional
  public Map<String, Object> upload(
      AuthenticatedUser user,
      String filename,
      byte[] content,
      String scopeKey,
      LocalDate from,
      LocalDate until,
      List<String> coverage) {
    if (content.length > 10 * 1024 * 1024)
      throw new ApiException(413, "Timetable upload exceeds 10 MiB");
    if (scopeKey == null || scopeKey.isBlank() || scopeKey.length() > 160)
      throw new ApiException(422, "Provide a scope key up to 160 characters");
    if (from == null || until == null || until.isBefore(from) || until.isAfter(from.plusDays(370)))
      throw new ApiException(422, "Term must span between 1 and 371 days");
    if (coverage == null || coverage.size() > 500 || coverage.stream().anyMatch(Objects::isNull))
      throw new ApiException(422, "Coverage must contain at most 500 room IDs");
    String safeName = filename == null ? "" : filename.replace('\\', '/');
    safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
    if (safeName.length() > 200) safeName = safeName.substring(0, 200);
    Extraction extracted;
    try {
      if (safeName.toLowerCase().endsWith(".pdf")
          && content.length > 5
          && new String(content, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
        extracted = new PdfTimetableAdapter().extract(content);
      else if (safeName.toLowerCase().endsWith(".docx"))
        extracted = new DocxTimetableAdapter().extract(content);
      else throw new IllegalArgumentException("Upload a supported PDF or DOCX file");
    } catch (IOException | RuntimeException error) {
      throw new ApiException(422, "Timetable extraction failed: " + error.getMessage());
    }
    var issues = new ArrayList<>(extracted.issues());
    if (!extracted.metadata().isEmpty()) {
      if (!user.campusId().equals(extracted.metadata().get("Campus ID")))
        issues.add(
            issue(
                "CAMPUS_MISMATCH",
                "Confirm campus metadata against this revision",
                Map.of(),
                null));
      if (!from.toString().equals(extracted.metadata().get("Valid from"))
          || !until.toString().equals(extracted.metadata().get("Valid until")))
        issues.add(
            issue(
                "DATES_MISMATCH", "Confirm document dates against revision dates", Map.of(), null));
    }
    if (extracted.sessions().isEmpty())
      issues.add(issue("NO_SESSIONS", "Provide a completed class timetable", Map.of(), null));
    String scope = id();
    jdbc.update(
        "INSERT INTO timetable_scopes(id,campus_id,scope_key) VALUES (?,?,?) ON"
            + " CONFLICT(campus_id,scope_key) DO NOTHING",
        scope,
        user.campusId(),
        scopeKey.strip());
    scope =
        jdbc.queryForObject(
            "SELECT id FROM timetable_scopes WHERE campus_id=? AND scope_key=?",
            String.class,
            user.campusId(),
            scopeKey.strip());
    String revisionId = id();
    String hash;
    try {
      hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
    jdbc.update(
        "INSERT INTO"
            + " timetable_revisions(id,scope_id,filename,sha256,content,valid_from,valid_until,sessions,issues,coverage_room_ids,created_by)"
            + " VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?)",
        revisionId,
        scope,
        safeName,
        hash,
        content,
        from,
        until,
        encode(extracted.sessions()),
        encode(issues),
        encode(coverage),
        user.id());
    audit.record(
        user, "timetable.uploaded", revisionId, Map.of("sha256", hash, "scope_key", scopeKey));
    return detail(revisionId, user);
  }

  @Transactional
  public Map<String, Object> review(String revisionId, Review payload, AuthenticatedUser user) {
    var revision = find(revisionId, user.campusId(), true);
    if (payload.expectedRevision() == null || payload.expectedRevision() < 0)
      throw new ApiException(422, "Provide expected_revision");
    if (revision.version() != payload.expectedRevision())
      throw new ApiException(409, "Revision changed; reload before reviewing");
    if (revision.status().equals("PUBLISHED"))
      throw new ApiException(409, "Published revisions are immutable; upload a replacement");
    if (!payload.acknowledgeSource())
      throw new ApiException(422, "Explicitly acknowledge source mapping and whole-room coverage");
    validate(payload.sessions(), payload.coverageRoomIds(), user.campusId());
    Map<String, Map<String, Object>> sources = new HashMap<>();
    revision.sessions().forEach(entry -> sources.put(entry.id(), entry.source()));
    var sessions =
        payload.sessions().stream()
            .map(
                entry ->
                    new Session(
                        entry.id() == null ? id() : entry.id(),
                        entry.weekday(),
                        entry.startTime(),
                        entry.endTime(),
                        entry.roomId(),
                        entry.subject(),
                        entry.facultyIds(),
                        sources.getOrDefault(
                            entry.id(), Map.of("manual", true, "reviewer_id", user.id()))))
            .toList();
    jdbc.update(
        "UPDATE timetable_revisions SET"
            + " sessions=?::jsonb,issues='[]',coverage_room_ids=?::jsonb,acknowledged=true,status='REVIEWED',version=version+1"
            + " WHERE id=?",
        encode(sessions),
        encode(payload.coverageRoomIds().stream().distinct().sorted().toList()),
        revisionId);
    audit.record(
        user,
        "timetable.reviewed",
        revisionId,
        Map.of("session_count", sessions.size(), "coverage_room_ids", payload.coverageRoomIds()));
    return detail(revisionId, user);
  }

  private void validate(List<Session> sessions, List<String> coverage, String campus) {
    if (sessions == null
        || sessions.isEmpty()
        || sessions.size() > 3000
        || coverage == null
        || coverage.size() > 500)
      throw new ApiException(422, "Provide 1 to 3000 sessions and at most 500 coverage room IDs");
    var rooms =
        jdbc.queryForList(
            "SELECT id FROM rooms WHERE campus_id=? AND active", String.class, campus);
    var faculty =
        jdbc.queryForList(
            "SELECT id FROM timetable_faculty WHERE campus_id=?", String.class, campus);
    for (var entry : sessions) {
      if (entry == null
          || entry.weekday() < 0
          || entry.weekday() > 6
          || !rooms.contains(entry.roomId()))
        throw new ApiException(
            422, "Every session requires a valid day and approved campus room ID");
      try {
        if (entry.startTime() == null
            || entry.endTime() == null
            || !entry.startTime().matches("(?:[01]\\d|2[0-3]):[0-5]\\d")
            || !entry.endTime().matches("(?:[01]\\d|2[0-3]):[0-5]\\d")
            || !LocalTime.parse(entry.startTime()).isBefore(LocalTime.parse(entry.endTime())))
          throw new IllegalArgumentException();
      } catch (IllegalArgumentException error) {
        throw new ApiException(422, "Session times must be HH:MM with start before end");
      }
      if (entry.subject() == null
          || entry.subject().isBlank()
          || entry.subject().length() > 100
          || entry.subject().toUpperCase().contains("ELECTIVE"))
        throw new ApiException(422, "Resolve the exact subject, including elective subgroup");
      if (entry.facultyIds() == null
          || entry.facultyIds().isEmpty()
          || entry.facultyIds().size() > 10
          || !faculty.containsAll(entry.facultyIds()))
        throw new ApiException(422, "Faculty IDs must be in the approved faculty directory");
    }
    if (!rooms.containsAll(coverage))
      throw new ApiException(422, "Coverage contains unknown or inactive rooms");
    for (int i = 0; i < sessions.size(); i++)
      for (int j = i + 1; j < sessions.size(); j++)
        if (overlaps(sessions.get(i), sessions.get(j)))
          throw new ApiException(
              422, "Sessions overlap in one room; explicitly consolidate shared classes");
  }

  private static boolean overlaps(Session left, Session right) {
    return left.roomId().equals(right.roomId())
        && left.weekday() == right.weekday()
        && left.startTime().compareTo(right.endTime()) < 0
        && right.startTime().compareTo(left.endTime()) < 0;
  }

  private List<Window> windows(String suffix, Object... args) {
    return jdbc.query(
        "SELECT * FROM timetable_publications " + suffix,
        (row, number) ->
            new Window(
                row.getString("id"),
                row.getString("scope_id"),
                row.getString("revision_id"),
                row.getObject("valid_from", LocalDate.class),
                row.getObject("valid_until", LocalDate.class)),
        args);
  }

  private void insertWindow(String scope, String revision, LocalDate start, LocalDate end) {
    jdbc.update(
        "INSERT INTO timetable_publications(id,scope_id,revision_id,valid_from,valid_until) VALUES"
            + " (?,?,?,?,?)",
        id(),
        scope,
        revision,
        start,
        end);
  }

  @Transactional
  public Map<String, Object> publish(
      String revisionId, int expectedVersion, AuthenticatedUser user) {
    writeLock.acquire();
    var revision = find(revisionId, user.campusId(), true);
    if (revision.version() != expectedVersion)
      throw new ApiException(409, "Revision changed; reload before publishing");
    if (!revision.status().equals("REVIEWED")
        || !revision.issues().isEmpty()
        || !revision.acknowledged())
      throw new ApiException(409, "Review and resolve every issue before publishing");
    validate(revision.sessions(), revision.coverageRoomIds(), user.campusId());
    checkConflicts(revision);
    for (var previous :
        windows(
            "WHERE scope_id=? AND valid_from<=? AND valid_until>=?",
            revision.scopeId(),
            revision.validUntil(),
            revision.validFrom())) {
      jdbc.update("DELETE FROM timetable_publications WHERE id=?", previous.id());
      if (previous.validFrom().isBefore(revision.validFrom()))
        insertWindow(
            previous.scopeId(),
            previous.revisionId(),
            previous.validFrom(),
            revision.validFrom().minusDays(1));
      if (previous.validUntil().isAfter(revision.validUntil()))
        insertWindow(
            previous.scopeId(),
            previous.revisionId(),
            revision.validUntil().plusDays(1),
            previous.validUntil());
    }
    insertWindow(revision.scopeId(), revision.id(), revision.validFrom(), revision.validUntil());
    jdbc.update(
        "UPDATE timetable_revisions SET status='PUBLISHED',published_at=now(),version=version+1"
            + " WHERE id=?",
        revisionId);
    audit.record(user, "timetable.published", revisionId, Map.of("scope_key", revision.scopeKey()));
    return detail(revisionId, user);
  }

  private void checkConflicts(Revision revision) {
    for (var window :
        windows(
            "WHERE scope_id<>? AND valid_from<=? AND valid_until>=?",
            revision.scopeId(),
            revision.validUntil(),
            revision.validFrom())) {
      var other = query("WHERE r.id=?", window.revisionId()).getFirst();
      if (!other.campusId().equals(revision.campusId())) continue;
      LocalDate from =
          window.validFrom().isAfter(revision.validFrom())
              ? window.validFrom()
              : revision.validFrom();
      LocalDate until =
          window.validUntil().isBefore(revision.validUntil())
              ? window.validUntil()
              : revision.validUntil();
      for (var entry : revision.sessions()) {
        LocalDate first =
            from.plusDays(Math.floorMod(entry.weekday() - (from.getDayOfWeek().getValue() - 1), 7));
        if (first.isAfter(until)) continue;
        if (other.sessions().stream().anyMatch(candidate -> overlaps(entry, candidate)))
          throw new ApiException(409, "Timetable conflicts with another published scope");
      }
    }
    for (var entry : revision.sessions()) {
      var occupied =
          jdbc.query(
              "SELECT starts_at,ends_at FROM occupancies WHERE room_id=? AND active AND starts_at<?"
                  + " AND ends_at>?",
              (row, number) ->
                  List.of(
                      row.getObject("starts_at", OffsetDateTime.class).atZoneSameInstant(INDIA),
                      row.getObject("ends_at", OffsetDateTime.class).atZoneSameInstant(INDIA)),
              entry.roomId(),
              revision.validUntil().plusDays(1).atStartOfDay(INDIA).toOffsetDateTime(),
              revision.validFrom().atStartOfDay(INDIA).toOffsetDateTime());
      for (var interval : occupied) {
        LocalDate day =
            interval.getFirst().toLocalDate().isAfter(revision.validFrom())
                ? interval.getFirst().toLocalDate()
                : revision.validFrom();
        LocalDate last =
            interval.get(1).toLocalDate().isBefore(revision.validUntil())
                ? interval.get(1).toLocalDate()
                : revision.validUntil();
        for (; !day.isAfter(last); day = day.plusDays(1)) {
          if (day.getDayOfWeek().getValue() - 1 != entry.weekday()) continue;
          var start = day.atTime(LocalTime.parse(entry.startTime())).atZone(INDIA);
          var end = day.atTime(LocalTime.parse(entry.endTime())).atZone(INDIA);
          if (start.isBefore(interval.get(1)) && interval.getFirst().isBefore(end))
            throw new ApiException(409, "Timetable conflicts with an approved booking or closure");
        }
      }
    }
  }

  private List<Revision> active(LocalDate date) {
    return query(
        "JOIN timetable_publications p ON p.revision_id=r.id WHERE p.valid_from<=? AND"
            + " p.valid_until>=?",
        date,
        date);
  }

  @Override
  public List<BusyInterval> busy(String roomId, LocalDate date) {
    return active(date).stream()
        .flatMap(revision -> revision.sessions().stream())
        .filter(
            entry ->
                entry.roomId().equals(roomId)
                    && entry.weekday() == date.getDayOfWeek().getValue() - 1)
        .map(entry -> new BusyInterval(entry.startTime(), entry.endTime(), "CLASS"))
        .toList();
  }

  @Override
  public boolean covered(String roomId, LocalDate date) {
    return active(date).stream().anyMatch(revision -> revision.coverageRoomIds().contains(roomId));
  }

  public List<Map<String, Object>> teachers(AuthenticatedUser user) {
    return jdbc.queryForList(
        "SELECT id,name FROM timetable_faculty WHERE campus_id=? ORDER BY name", user.campusId());
  }

  @Transactional
  public Map<String, Object> addFaculty(String facultyId, String name, AuthenticatedUser user) {
    if (facultyId == null
        || facultyId.isBlank()
        || facultyId.length() > 80
        || name == null
        || name.isBlank()
        || name.length() > 150) throw new ApiException(422, "Provide faculty ID and name");
    int added =
        jdbc.update(
            "INSERT INTO timetable_faculty(id,campus_id,name) VALUES (?,?,?) ON CONFLICT DO"
                + " NOTHING",
            facultyId,
            user.campusId(),
            name.strip());
    if (added == 0) throw new ApiException(409, "Faculty ID already exists");
    audit.record(user, "faculty.created", id(), Map.of("faculty_id", facultyId, "name", name));
    return Map.of("id", facultyId, "name", name.strip());
  }

  public Map<String, Object> teacherSchedule(
      String facultyId, LocalDate date, AuthenticatedUser user) {
    var teacher =
        jdbc.queryForList(
            "SELECT id,name FROM timetable_faculty WHERE id=? AND campus_id=?",
            facultyId,
            user.campusId());
    if (teacher.isEmpty()) throw new ApiException(404, "Teacher not found");
    List<Map<String, Object>> sessions = new ArrayList<>();
    for (var revision : active(date)) {
      if (!revision.campusId().equals(user.campusId())) continue;
      for (var entry : revision.sessions())
        if (entry.weekday() == date.getDayOfWeek().getValue() - 1
            && entry.facultyIds().contains(facultyId)) {
          var room = jdbc.queryForMap("SELECT code,building FROM rooms WHERE id=?", entry.roomId());
          sessions.add(
              Map.of(
                  "start_time",
                  entry.startTime(),
                  "end_time",
                  entry.endTime(),
                  "subject",
                  entry.subject(),
                  "room_id",
                  entry.roomId(),
                  "room_code",
                  room.get("code"),
                  "building",
                  room.get("building")));
        }
    }
    sessions.sort(java.util.Comparator.comparing(entry -> (String) entry.get("start_time")));
    return Map.of(
        "teacher",
        teacher.getFirst(),
        "date",
        date,
        "sessions",
        sessions,
        "notice",
        "Scheduled teaching venues; not live location or meeting availability.");
  }
}
