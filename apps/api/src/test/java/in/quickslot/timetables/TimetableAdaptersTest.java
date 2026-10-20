package in.quickslot.timetables;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;

class TimetableAdaptersTest {
  static XWPFDocument template() {
    var document = new XWPFDocument();
    var metadata = document.createTable(4, 4);
    String[][] values = {
      {"Campus ID", "[CAMPUS_ID]", "Programme", "[PROGRAMME]"},
      {"Department", "[DEPARTMENT]", "Semester", "[SEMESTER]"},
      {"Group ID", "[GROUP_ID]", "Term ID", "[TERM_ID]"},
      {"Valid from", "[YYYY-MM-DD]", "Valid until", "[YYYY-MM-DD]"}
    };
    for (int row = 0; row < 4; row++)
      for (int column = 0; column < 4; column++)
        metadata.getRow(row).getCell(column).setText(values[row][column]);
    var grid = document.createTable(3, 11);
    String[] headers = {
      "DAY / TIME",
      "08:00 to 08:55",
      "08:55 to 09:50",
      "10:10 to 11:05",
      "11:05 to 12:00",
      "12:00 to 12:55",
      "12:55 to 13:50",
      "14:10 to 15:05",
      "15:05 to 16:00",
      "16:00 to 16:55",
      "16:55 to 17:50"
    };
    for (int i = 0; i < headers.length; i++) grid.getRow(0).getCell(i).setText(headers[i]);
    grid.getRow(1).getCell(0).setText("MON");
    grid.getRow(2).getCell(0).setText("TUE");
    var courses = document.createTable(3, 3);
    String[] legend = {"SUBJECT CODE", "SUBJECT NAME", "TYPE"};
    for (int i = 0; i < 3; i++) courses.getRow(0).getCell(i).setText(legend[i]);
    for (var entry :
        List.of(
            new Object[] {metadata, "TIMETABLE_METADATA_V1"},
            new Object[] {grid, "TIMETABLE_GRID_V1"},
            new Object[] {courses, "TIMETABLE_COURSES_V1"})) {
      var table = (XWPFTable) entry[0];
      table.getCTTbl().getTblPr().addNewTblCaption().setVal((String) entry[1]);
    }
    var illustration = document.createTable(2, 2);
    illustration.getRow(0).getCell(0).setText("DAY / TIME");
    illustration.getRow(1).getCell(0).setText("MON");
    illustration.getRow(1).getCell(1).setText("Fictional guide cell");
    return document;
  }

  static byte[] bytes(XWPFDocument document) throws Exception {
    var out = new ByteArrayOutputStream();
    document.write(out);
    return out.toByteArray();
  }

  static void classCell(XWPFTableCell cell) {
    cell.setText("CS301");
    cell.addParagraph().createRun().setText("R: room");
    cell.addParagraph().createRun().setText("F: F001");
  }

  @Test
  void actualPdfKeepsEvidenceUnknownIdsElectivesAndMergedLabDuration() throws Exception {
    Path root = Path.of("").toAbsolutePath();
    while (root != null && !Files.isDirectory(root.resolve("data/sources/gehu-dehradun")))
      root = root.getParent();
    if (root == null) throw new IllegalStateException("Repository timetable fixtures not found");
    Path source = root.resolve("data/sources/gehu-dehradun/B.Tech-C.S.E-V-TT-2.pdf");
    var result = new PdfTimetableAdapter().extract(Files.readAllBytes(source));
    assertThat(result.sessions()).hasSizeGreaterThan(100);
    assertThat(result.sessions())
        .allMatch(entry -> entry.roomId() == null && entry.facultyIds().isEmpty());
    assertThat(result.sessions())
        .anyMatch(entry -> entry.source().get("text").toString().contains("017"));
    assertThat(result.sessions()).anyMatch(entry -> entry.subject().contains("ELECTIVE"));
    var merged =
        result.sessions().stream()
            .filter(
                entry ->
                    entry.weekday() == 1
                        && Integer.valueOf(1).equals(entry.source().get("page"))
                        && entry
                            .source()
                            .get("text")
                            .toString()
                            .replaceAll("\\s", "")
                            .contains("PCS502"))
            .findFirst()
            .orElseThrow();
    assertThat(merged.startTime()).isEqualTo("14:10");
    assertThat(merged.endTime()).isEqualTo("16:00");
    assertThat(merged.source().get("bbox")).isInstanceOf(List.class);
    assertThat(result.issues().stream().map(ImportData.Issue::sessionId))
        .containsAll(result.sessions().stream().map(ImportData.Session::id).toList());
  }

  @Test
  void blankMarkedTemplateIgnoresIllustration() throws Exception {
    try (var document = template()) {
      var result = new DocxTimetableAdapter().extract(bytes(document));
      assertThat(result.sessions()).isEmpty();
      assertThat(result.issues()).anyMatch(issue -> issue.code().equals("METADATA_INCOMPLETE"));
      assertThat(result.metadata().get("Campus ID")).isEqualTo("[CAMPUS_ID]");
    }
  }

  @Test
  void docxMergedSpanUsesActualEndAndExplicitIds() throws Exception {
    try (var document = template()) {
      var grid = document.getTables().get(1);
      var row = grid.getRow(1);
      row.getCell(7).getCTTc().addNewTcPr().addNewGridSpan().setVal(java.math.BigInteger.TWO);
      row.removeCell(8);
      classCell(row.getCell(7));
      var legend = document.getTables().get(2).getRow(1);
      legend.getCell(0).setText("CS301");
      legend.getCell(1).setText("Subject");
      legend.getCell(2).setText("LECTURE");
      var result = new DocxTimetableAdapter().extract(bytes(document));
      assertThat(result.sessions()).hasSize(1);
      var entry = result.sessions().getFirst();
      assertThat(entry.startTime()).isEqualTo("14:10");
      assertThat(entry.endTime()).isEqualTo("16:00");
      assertThat(entry.roomId()).isEqualTo("room");
      assertThat(entry.facultyIds()).containsExactly("F001");
      assertThat(result.issues()).noneMatch(issue -> issue.code().equals("CELL_FORMAT"));
    }
  }

  @Test
  void malformedOrUnmarkedDocxIsRejected() throws Exception {
    try (var document = new XWPFDocument()) {
      document.createTable(2, 2);
      assertThatThrownBy(() -> new DocxTimetableAdapter().extract(bytes(document)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("marked tables");
    }
  }

  @Test
  void clockColumnsRejectBreakReversalAndNormalizeOnlyPdfTwelveHourTimes() {
    assertThat(TimeColumns.parse(List.of("12:55 - 01:50", "02:10 - 03:05"), true).getFirst())
        .containsExactly(775, 830);
    assertThatThrownBy(() -> TimeColumns.parse(List.of("12:55 - 01:50"), false))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
