package in.quickslot.timetables;

import static in.quickslot.timetables.ImportData.*;

import in.quickslot.inventory.PdfRoomInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PdfRoomMatcher {
  public record Candidate(String id, String code, String kind, String campusId, boolean active) {}

  public Extraction match(Extraction extraction, String campusId, List<Candidate> candidates) {
    var rooms =
        candidates.stream()
            .filter(room -> room.active() && campusId.equals(room.campusId()))
            .toList();
    var inventory = new PdfRoomInventory();
    var sessions = new ArrayList<Session>();
    var issues = new ArrayList<>(extraction.issues());
    for (var session : extraction.sessions()) {
      if (session.roomId() != null && !session.roomId().isBlank()) {
        sessions.add(session);
        continue;
      }
      var labels = inventory.labels(String.valueOf(session.source().getOrDefault("text", "")));
      List<String> matches = List.of();
      if (labels.size() == 1) {
        var label = labels.getFirst();
        matches =
            rooms.stream()
                .filter(
                    room ->
                        label.code().equals(normalize(room.code()))
                            && label.kind().equals(normalize(room.kind())))
                .map(Candidate::id)
                .distinct()
                .toList();
      }
      if (labels.size() == 1 && matches.size() == 1) {
        sessions.add(
            new Session(
                session.id(),
                session.weekday(),
                session.startTime(),
                session.endTime(),
                matches.getFirst(),
                session.subject(),
                session.facultyIds(),
                session.source()));
      } else {
        sessions.add(session);
        boolean ambiguous = labels.size() > 1 || matches.size() > 1;
        issues.add(
            issue(
                ambiguous ? "ROOM_AMBIGUOUS" : "ROOM_NOT_MATCHED",
                ambiguous
                    ? "Confirm the room because this cell has more than one possible match"
                    : "Choose an approved room; no exact active campus room matches this cell",
                session.source(),
                session.id()));
      }
    }
    return new Extraction(List.copyOf(sessions), List.copyOf(issues), extraction.metadata());
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
  }
}
