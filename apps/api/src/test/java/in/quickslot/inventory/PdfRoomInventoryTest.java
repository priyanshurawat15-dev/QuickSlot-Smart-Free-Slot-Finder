package in.quickslot.inventory;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PdfRoomInventoryTest {
  @Test
  void floorsFollowTheUserConfirmedNumberingConvention() {
    assertThat(SourceInventory.floorFromCode("017")).isEqualTo("Ground floor");
    assertThat(SourceInventory.floorFromCode("106")).isEqualTo("First floor");
    assertThat(SourceInventory.floorFromCode("224")).isEqualTo("Second floor");
  }

  @Test
  void providedTimetablesDefineOnlyTheirPrintedClassroomAndLectureTheatreLabels() throws Exception {
    var labels = new HashSet<String>();
    for (String filename : new String[] {"B.Tech-CSE-III.pdf", "B.Tech-CSE-V.pdf"}) {
      try (var stream = new ClassPathResource("timetables/" + filename).getInputStream()) {
        var rooms = new PdfRoomInventory().extract(stream.readAllBytes());
        assertThat(rooms)
            .allMatch(room -> room.kind().equals("CLASSROOM") || room.kind().equals("LT"));
        rooms.forEach(room -> labels.add(room.printedLabel()));
      }
    }
    assertThat(labels)
        .containsExactlyInAnyOrder(
            "017 (CR)",
            "106 (LT)",
            "107 (CR)",
            "113 (CR)",
            "114 (CR)",
            "115 (CR)",
            "116 (CR)",
            "117 (CR)",
            "123 (CR)",
            "124 (LT)",
            "206 (LT)",
            "224 (LT)");
    assertThat(labels).doesNotContain("LT-101", "203 (CR)");
  }
}
