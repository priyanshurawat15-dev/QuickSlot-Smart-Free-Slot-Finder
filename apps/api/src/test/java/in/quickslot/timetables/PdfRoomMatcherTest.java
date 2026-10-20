package in.quickslot.timetables;

import static in.quickslot.timetables.ImportData.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PdfRoomMatcherTest {
  private final PdfRoomMatcher matcher = new PdfRoomMatcher();

  private static PdfRoomMatcher.Candidate room(String id, String code, String kind) {
    return new PdfRoomMatcher.Candidate(id, code, kind, "campus", true);
  }

  private static Extraction extraction(String text) {
    var source = Map.<String, Object>of("text", text, "page", 1);
    var session = new Session("session", 1, "08:00", "08:55", null, "CS301", List.of(), source);
    return new Extraction(
        List.of(session),
        List.of(issue("CELL_REVIEW", "Review source", source, session.id())),
        Map.of("Programme", "CSE"));
  }

  @Test
  void matchesExactPrintedConventionAndKeepsEvidenceAndReview() {
    var input = extraction("CS301\n017 (cr)");
    var result =
        matcher.match(
            input,
            "campus",
            List.of(room("classroom", "017", "CLASSROOM"), room("theatre", "017", "LT")));
    var session = result.sessions().getFirst();
    assertThat(session.roomId()).isEqualTo("classroom");
    assertThat(session.source()).isEqualTo(input.sessions().getFirst().source());
    assertThat(session.id()).isEqualTo("session");
    assertThat(session.startTime()).isEqualTo("08:00");
    assertThat(session.endTime()).isEqualTo("08:55");
    assertThat(session.facultyIds()).isEmpty();
    assertThat(result.issues()).isEqualTo(input.issues());
    assertThat(result.metadata()).isEqualTo(input.metadata());
  }

  @Test
  void repeatedSameLabelAndSameCandidateDoNotBecomeAmbiguous() {
    var candidate = room("theatre", "106", "LT");
    var result =
        matcher.match(extraction("106 (LT)\n106(lt)"), "campus", List.of(candidate, candidate));
    assertThat(result.sessions().getFirst().roomId()).isEqualTo("theatre");
  }

  @Test
  void multipleLabelsOrDirectoryMatchesRequireReview() {
    for (var result :
        List.of(
            matcher.match(
                extraction("017 (CR) / 106 (LT)"),
                "campus",
                List.of(room("a", "017", "CLASSROOM"), room("b", "106", "LT"))),
            matcher.match(
                extraction("017 (CR)"),
                "campus",
                List.of(room("a", "017", "CLASSROOM"), room("b", "017", "CLASSROOM"))))) {
      assertThat(result.sessions().getFirst().roomId()).isNull();
      assertThat(result.issues()).anyMatch(issue -> issue.code().equals("ROOM_AMBIGUOUS"));
    }
  }

  @Test
  void missingUnknownAndOutOfScopeLabelsAreNotGuessed() {
    for (String text : List.of("ELECTIVE", "PCS502 LAB-8", "529 (Audi)", "999 (CR)", "1017 (CR)")) {
      var result =
          matcher.match(extraction(text), "campus", List.of(room("room", "017", "CLASSROOM")));
      assertThat(result.sessions().getFirst().roomId()).isNull();
      assertThat(result.issues()).anyMatch(issue -> issue.code().equals("ROOM_NOT_MATCHED"));
    }
  }

  @Test
  void ignoresInactiveRoomsAndOtherCampuses() {
    var result =
        matcher.match(
            extraction("017 (CR)"),
            "campus",
            List.of(
                new PdfRoomMatcher.Candidate("other-campus", "017", "CLASSROOM", "other", true),
                new PdfRoomMatcher.Candidate("inactive", "017", "CLASSROOM", "campus", false)));
    assertThat(result.sessions().getFirst().roomId()).isNull();
    assertThat(result.issues()).anyMatch(issue -> issue.code().equals("ROOM_NOT_MATCHED"));
  }

  @Test
  void preservesAnExplicitRoomId() {
    var session =
        new Session(
            "session",
            1,
            "08:00",
            "08:55",
            "explicit",
            "CS301",
            List.of("faculty"),
            Map.of("text", "017(CR)"));
    var input = new Extraction(List.of(session), List.of(), Map.of());
    assertThat(matcher.match(input, "campus", List.of()).sessions()).containsExactly(session);
  }
}
