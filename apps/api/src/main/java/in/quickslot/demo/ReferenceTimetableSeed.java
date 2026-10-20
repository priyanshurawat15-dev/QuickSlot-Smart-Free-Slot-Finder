package in.quickslot.demo;

import static in.quickslot.timetables.ImportData.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.quickslot.shared.AuditService;
import in.quickslot.shared.AuthenticatedUser;
import in.quickslot.shared.WriteLock;
import in.quickslot.timetables.PdfRoomMatcher;
import in.quickslot.timetables.ReferenceTimetableExtractor;
import in.quickslot.timetables.TimetableService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class ReferenceTimetableSeed {
  public static final String III_SCOPE = "CSE III supplied timetable";
  public static final String V_SCOPE = "CSE V supplied timetable";
  private static final LocalDate VALID_FROM = LocalDate.of(2026, 7, 13);
  private final JdbcTemplate jdbc;
  private final TimetableService timetables;
  private final ObjectMapper json;
  private final WriteLock writeLock;
  private final AuditService audit;

  public ReferenceTimetableSeed(
      JdbcTemplate jdbc,
      TimetableService timetables,
      ObjectMapper json,
      WriteLock writeLock,
      AuditService audit) {
    this.jdbc = jdbc;
    this.timetables = timetables;
    this.json = json;
    this.writeLock = writeLock;
    this.audit = audit;
  }

  @Transactional
  public void seed(AuthenticatedUser admin) {
    writeLock.acquire();
    seedFile("B.Tech-CSE-III.pdf", III_SCOPE, admin);
    seedFile("B.Tech-CSE-V.pdf", V_SCOPE, admin);
  }

  private void seedFile(String filename, String scope, AuthenticatedUser admin) {
    // A retained scope is also the marker after its publication is removed or replaced.
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM timetable_scopes WHERE campus_id=? AND scope_key=?)",
            Boolean.class,
            admin.campusId(),
            scope))) return;
    try (var stream = new ClassPathResource("timetables/" + filename).getInputStream()) {
      byte[] content = stream.readAllBytes();
      var candidates =
          jdbc.query(
              "SELECT id,code,kind,campus_id,active FROM rooms WHERE campus_id=? AND active",
              (row, number) ->
                  new PdfRoomMatcher.Candidate(
                      row.getString("id"),
                      row.getString("code"),
                      row.getString("kind"),
                      row.getString("campus_id"),
                      row.getBoolean("active")),
              admin.campusId());
      var extracted =
          new ReferenceTimetableExtractor().extract(content, admin.campusId(), candidates);
      var blocking =
          extracted.issues().stream()
              .filter(issue -> !issue.code().equals("REFERENCE_OUTSIDE_SCOPE"))
              .toList();
      if (!blocking.isEmpty())
        throw new IllegalStateException(
            "Reference timetable has unresolved room mappings: "
                + filename
                + " ("
                + blocking.size()
                + " issues)");
      var sessions = consolidate(extracted.sessions());
      var coverage = sessions.stream().map(Session::roomId).distinct().sorted().toList();
      var draft = timetables.upload(admin, filename, content, scope, VALID_FROM, null, coverage);
      String revisionId = draft.get("id").toString();
      // Keep the exact cell evidence for the consolidated room intervals before normal review.
      jdbc.update(
          "UPDATE timetable_revisions SET sessions=?::jsonb WHERE id=?",
          json.writeValueAsString(sessions),
          revisionId);
      timetables.review(
          revisionId, new TimetableService.Review(0, sessions, coverage, true), admin);
      timetables.publish(revisionId, 1, admin);
      audit.record(
          admin,
          "timetable.reference_dataset_accepted",
          revisionId,
          Map.of(
              "dataset",
              "user-approved supplied CSE timetables",
              "scope_key",
              scope,
              "sha256",
              draft.get("sha256"),
              "excluded_cells",
              extracted.issues(),
              "room_interval_count",
              sessions.size()));
    } catch (IOException error) {
      throw new IllegalStateException(
          "Cannot load supplied reference timetable " + filename, error);
    }
  }

  private static List<Session> consolidate(List<Session> source) {
    var sorted =
        source.stream()
            .sorted(
                Comparator.comparing(Session::roomId)
                    .thenComparingInt(Session::weekday)
                    .thenComparing(Session::startTime)
                    .thenComparing(Session::endTime))
            .toList();
    var result = new ArrayList<Session>();
    var evidence = new ArrayList<Map<String, Object>>();
    Session current = null;
    for (var entry : sorted) {
      if (current != null
          && current.roomId().equals(entry.roomId())
          && current.weekday() == entry.weekday()
          && entry.startTime().compareTo(current.endTime()) <= 0) {
        evidence.add(entry.source());
        String end =
            current.endTime().compareTo(entry.endTime()) >= 0 ? current.endTime() : entry.endTime();
        current = interval(current, current.startTime(), end, evidence);
      } else {
        if (current != null) result.add(current);
        evidence = new ArrayList<>();
        evidence.add(entry.source());
        current = interval(entry, entry.startTime(), entry.endTime(), evidence);
      }
    }
    if (current != null) result.add(current);
    return List.copyOf(result);
  }

  private static Session interval(
      Session source, String start, String end, List<Map<String, Object>> cells) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("room_occupancy_union", true);
    evidence.put("cells", List.copyOf(cells));
    return new Session(
        UUID.randomUUID().toString(),
        source.weekday(),
        start,
        end,
        source.roomId(),
        "Scheduled class",
        List.of(),
        evidence);
  }
}
