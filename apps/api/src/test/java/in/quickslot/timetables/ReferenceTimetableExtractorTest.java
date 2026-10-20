package in.quickslot.timetables;

import static org.assertj.core.api.Assertions.*;

import in.quickslot.inventory.PdfRoomInventory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReferenceTimetableExtractorTest {
  private static final String CAMPUS = "gehu-hill-dehradun";
  private static byte[] thirdSource;
  private static ImportData.Extraction third;
  private static ImportData.Extraction fifth;

  private static byte[] source(String filename) throws Exception {
    try (var stream =
        ReferenceTimetableExtractorTest.class.getResourceAsStream("/timetables/" + filename)) {
      if (stream == null) throw new IllegalStateException("Supplied timetable fixture is missing");
      return stream.readAllBytes();
    }
  }

  private static List<PdfRoomMatcher.Candidate> rooms(byte[] content) throws Exception {
    return new PdfRoomInventory()
        .extract(content).stream()
            .map(
                label ->
                    new PdfRoomMatcher.Candidate(
                        label.printedLabel(), label.code(), label.kind(), CAMPUS, true))
            .toList();
  }

  @BeforeAll
  static void extractProvidedSources() throws Exception {
    thirdSource = source("B.Tech-CSE-III.pdf");
    var fifthSource = source("B.Tech-CSE-V.pdf");
    third = new ReferenceTimetableExtractor().extract(thirdSource, CAMPUS, rooms(thirdSource));
    fifth = new ReferenceTimetableExtractor().extract(fifthSource, CAMPUS, rooms(fifthSource));
  }

  @Test
  void resolvesProvidedClassroomSessionsWithoutInventingFacultyOrRoomCoverage() {
    assertThat(third.sessions()).hasSize(189);
    assertThat(fifth.sessions()).hasSize(204);
    assertThat(third.metadata())
        .containsEntry("source_sessions", "555")
        .containsEntry("mapped_sessions", "349");
    assertThat(fifth.metadata())
        .containsEntry("source_sessions", "455")
        .containsEntry("mapped_sessions", "435");
    for (var result : List.of(third, fifth)) {
      assertThat(result.issues()).allMatch(issue -> issue.code().equals("REFERENCE_OUTSIDE_SCOPE"));
      assertThat(result.issues())
          .allMatch(
              issue -> issue.source().containsKey("text") && issue.source().containsKey("page"));
      assertThat(result.sessions())
          .allMatch(session -> session.facultyIds().isEmpty() && session.roomId() != null);
      assertThat(result.sessions())
          .anyMatch(session -> session.source().containsKey("occurrences"));
    }
    var combined = new ArrayList<>(third.sessions());
    combined.addAll(fifth.sessions());
    for (int index = 0; index < combined.size(); index++) {
      var session = combined.get(index);
      for (var other : combined.subList(index + 1, combined.size())) {
        boolean overlap =
            session.roomId().equals(other.roomId())
                && session.weekday() == other.weekday()
                && session.startTime().compareTo(other.endTime()) < 0
                && other.startTime().compareTo(session.endTime()) < 0;
        assertThat(overlap)
            .as(
                "room %s, day %s, %s / %s",
                session.roomId(), session.weekday(), session.source(), other.source())
            .isFalse();
      }
    }
  }

  @Test
  void electiveExpansionUsesPrintedSamePageLegendAndOriginalMinuteBoundaries() {
    assertThat(third.sessions())
        .anySatisfy(
            session -> {
              assertThat(session.source())
                  .containsEntry("page", 1)
                  .containsEntry("elective_subject", "TCS351");
              assertThat(session.roomId()).isEqualTo("117 (CR)");
              assertThat(session.startTime()).isEqualTo("08:55");
              assertThat(session.endTime()).isEqualTo("09:50");
              assertThat(session.source().get("legend_text").toString()).contains("117 (CR)");
              assertThat(session.source()).containsKeys("bbox", "text");
            });
    assertThat(fifth.sessions())
        .anySatisfy(
            session -> {
              assertThat(session.source())
                  .containsEntry("page", 1)
                  .containsEntry("elective_subject", "TCS597E");
              assertThat(session.roomId()).isEqualTo("124 (LT)");
              assertThat(session.source().get("legend_text").toString())
                  .contains("TCS 597(E)", "124 (LT)");
            });
  }

  @Test
  void missingCampusRoomCannotDisappearAsAnOutsideScopeCell() throws Exception {
    var result =
        new ReferenceTimetableExtractor().extract(thirdSource, "other-campus", rooms(thirdSource));
    assertThat(result.sessions()).isEmpty();
    assertThat(result.issues()).anyMatch(issue -> issue.code().equals("REFERENCE_UNRESOLVED"));
    assertThat(result.issues())
        .filteredOn(issue -> issue.code().equals("REFERENCE_UNRESOLVED"))
        .allMatch(
            issue -> issue.source().containsKey("page") && issue.source().containsKey("text"));
  }
}
