package in.quickslot.timetables;

import static in.quickslot.timetables.ImportData.*;

import in.quickslot.inventory.PdfRoomInventory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

public final class ReferenceTimetableExtractor {
  private static final Pattern SUBJECT =
      Pattern.compile("\\b([A-Z]{3})\\s*(\\d{3})(?:\\(([A-Z])\\)|([A-Z]))?(?![A-Z0-9])");
  private static final Pattern OUTSIDE_SCOPE =
      Pattern.compile("(?i)\\b(?:LAB|LABS|AUDI|AUDITORIUM|ONLINE)\\b");

  private record Legend(String subject, String text, List<PdfRoomInventory.RoomLabel> rooms) {}

  public Extraction extract(
      byte[] content, String campusId, List<PdfRoomMatcher.Candidate> candidates)
      throws IOException {
    var raw = new PdfTimetableAdapter().extract(content);
    var legends = legends(content);
    var inventory = new PdfRoomInventory();
    var sessions = new ArrayList<Session>();
    var issues = new ArrayList<Issue>();
    raw.issues().stream().filter(issue -> !issue.code().equals("CELL_REVIEW")).forEach(issues::add);
    for (var session : raw.sessions()) {
      String text = session.source().get("text").toString();
      var labels = inventory.labels(text);
      if (!labels.isEmpty()) {
        add(session, labels, campusId, candidates, sessions, issues);
      } else if (text.contains("ELECTIVE")) {
        var codes = subjects(text);
        if (codes.isEmpty()) {
          unresolved(session, "The elective cell has no readable subject code", issues);
          continue;
        }
        for (String code : codes) {
          var matches =
              legends.getOrDefault((Integer) session.source().get("page"), List.of()).stream()
                  .filter(legend -> legend.subject().equals(code))
                  .toList();
          if (matches.size() != 1) {
            unresolved(
                session,
                "The elective subject " + code + " needs one same-page legend entry",
                issues);
            continue;
          }
          var legend = matches.getFirst();
          var source = new LinkedHashMap<>(session.source());
          source.put("legend_text", legend.text());
          source.put("elective_subject", code);
          var expanded =
              new Session(
                  session.id() + ":" + code,
                  session.weekday(),
                  session.startTime(),
                  session.endTime(),
                  null,
                  code,
                  List.of(),
                  source);
          if (!legend.rooms().isEmpty())
            add(expanded, legend.rooms(), campusId, candidates, sessions, issues);
          else if (OUTSIDE_SCOPE.matcher(legend.text()).find()) outside(expanded, issues);
          else
            unresolved(
                expanded,
                "The elective legend has no explicit classroom or lecture-theatre room",
                issues);
        }
      } else if (OUTSIDE_SCOPE.matcher(text).find()) outside(session, issues);
      else
        unresolved(session, "The cell has no explicit classroom or lecture-theatre room", issues);
    }
    var deduplicated = deduplicate(sessions);
    return new Extraction(
        deduplicated,
        List.copyOf(issues),
        Map.of(
            "source_sessions",
            Integer.toString(raw.sessions().size()),
            "mapped_sessions",
            Integer.toString(sessions.size()),
            "deduplicated_sessions",
            Integer.toString(deduplicated.size())));
  }

  private static void add(
      Session session,
      List<PdfRoomInventory.RoomLabel> labels,
      String campusId,
      List<PdfRoomMatcher.Candidate> candidates,
      List<Session> sessions,
      List<Issue> issues) {
    if (labels.size() != 1) {
      unresolved(session, "The cell has multiple classroom or lecture-theatre room labels", issues);
      return;
    }
    var label = labels.getFirst();
    var rooms =
        candidates.stream()
            .filter(
                room ->
                    room.active()
                        && campusId.equals(room.campusId())
                        && label.code().equals(room.code())
                        && label.kind().equals(room.kind()))
            .map(PdfRoomMatcher.Candidate::id)
            .distinct()
            .toList();
    if (rooms.size() != 1) {
      unresolved(
          session,
          "The printed room " + label.printedLabel() + " needs one active campus room match",
          issues);
      return;
    }
    sessions.add(
        new Session(
            session.id(),
            session.weekday(),
            session.startTime(),
            session.endTime(),
            rooms.getFirst(),
            session.subject(),
            List.of(),
            session.source()));
  }

  private static List<String> subjects(String text) {
    var subjects = new java.util.LinkedHashSet<String>();
    var matcher = SUBJECT.matcher(text);
    while (matcher.find())
      subjects.add(
          matcher.group(1)
              + matcher.group(2)
              + (matcher.group(3) != null
                  ? matcher.group(3)
                  : matcher.group(4) != null ? matcher.group(4) : ""));
    return List.copyOf(subjects);
  }

  private static Map<Integer, List<Legend>> legends(byte[] content) throws IOException {
    var result = new LinkedHashMap<Integer, List<Legend>>();
    var inventory = new PdfRoomInventory();
    try (var document = Loader.loadPDF(content)) {
      var stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      for (int page = 1; page <= document.getNumberOfPages(); page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String text = stripper.getText(document);
        int start = text.indexOf("SUBJECT CODE");
        if (start < 0) continue;
        var entries = new ArrayList<Legend>();
        for (String line : text.substring(start).lines().toList()) {
          var codes = subjects(line);
          if (codes.size() == 1)
            entries.add(new Legend(codes.getFirst(), line.strip(), inventory.labels(line)));
        }
        result.put(page, List.copyOf(entries));
      }
    }
    return result;
  }

  private static List<Session> deduplicate(List<Session> sessions) {
    var byTime = new LinkedHashMap<String, Session>();
    for (var session : sessions) {
      var code = subjects(session.source().get("text").toString());
      if (code.size() != 1) code = subjects(session.subject());
      String subject = code.size() == 1 ? code.getFirst() : session.subject();
      String key =
          session.roomId()
              + ":"
              + session.weekday()
              + ":"
              + session.startTime()
              + ":"
              + session.endTime()
              + ":"
              + subject;
      var previous = byTime.get(key);
      if (previous == null) byTime.put(key, session);
      else {
        var source = new LinkedHashMap<>(previous.source());
        var occurrences = new ArrayList<Map<String, Object>>();
        if (source.get("occurrences") instanceof List<?> existing) {
          for (Object occurrence : existing) {
            @SuppressWarnings("unchecked")
            var evidence = (Map<String, Object>) occurrence;
            occurrences.add(evidence);
          }
        } else occurrences.add(Map.copyOf(previous.source()));
        occurrences.add(Map.copyOf(session.source()));
        source.put("occurrences", List.copyOf(occurrences));
        byTime.put(
            key,
            new Session(
                previous.id(),
                previous.weekday(),
                previous.startTime(),
                previous.endTime(),
                previous.roomId(),
                previous.subject(),
                List.of(),
                source));
      }
    }
    return List.copyOf(byTime.values());
  }

  private static void unresolved(Session session, String message, List<Issue> issues) {
    issues.add(issue("REFERENCE_UNRESOLVED", message, session.source(), session.id()));
  }

  private static void outside(Session session, List<Issue> issues) {
    issues.add(
        issue(
            "REFERENCE_OUTSIDE_SCOPE",
            "This cell is outside the classroom and lecture-theatre room register",
            session.source(),
            session.id()));
  }
}
