package in.quickslot.timetables;

import static in.quickslot.timetables.ImportData.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.w3c.dom.Element;

public final class DocxTimetableAdapter {
  private static final String WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

  public Extraction extract(byte[] content) throws IOException {
    boundExpandedArchive(content);
    try (var document = new XWPFDocument(new ByteArrayInputStream(content))) {
      Map<String, XWPFTable> marked = new HashMap<>();
      for (var table : document.getTables()) {
        var captions =
            ((Element) table.getCTTbl().getDomNode()).getElementsByTagNameNS(WORD, "tblCaption");
        if (captions.getLength() == 0) continue;
        String marker = ((Element) captions.item(0)).getAttributeNS(WORD, "val");
        if (marked.put(marker, table) != null)
          throw new IllegalArgumentException("Duplicate marked import table");
      }
      if (!marked
          .keySet()
          .containsAll(
              List.of("TIMETABLE_METADATA_V1", "TIMETABLE_GRID_V1", "TIMETABLE_COURSES_V1")))
        throw new IllegalArgumentException("DOCX requires the three DOCX-TT-1 marked tables");
      List<Issue> issues = new ArrayList<>();
      Map<String, String> metadata = new LinkedHashMap<>();
      for (var row : marked.get("TIMETABLE_METADATA_V1").getRows()) {
        var cells = row.getTableCells();
        for (int i = 0; i + 1 < cells.size(); i += 2) {
          String key = cellText(cells.get(i)).strip();
          String value = cellText(cells.get(i + 1)).strip();
          metadata.put(key, value);
          if (value.isEmpty() || value.contains("["))
            issues.add(issue("METADATA_INCOMPLETE", "Complete " + key, Map.of(), null));
        }
      }
      Map<String, String> subjects = new HashMap<>();
      var legend = marked.get("TIMETABLE_COURSES_V1");
      for (int row = 1; row < legend.getNumberOfRows(); row++) {
        var values =
            legend.getRow(row).getTableCells().stream()
                .map(cell -> cellText(cell).strip())
                .toList();
        if (values.stream().allMatch(String::isEmpty)) continue;
        if (values.size() != 3 || values.stream().anyMatch(String::isEmpty))
          issues.add(
              issue(
                  "LEGEND_INCOMPLETE",
                  "Complete subject code, name and type",
                  Map.of("row", row),
                  null));
        else if (subjects.put(values.get(0), values.get(1)) != null)
          issues.add(
              issue("LEGEND_DUPLICATE", "Subject codes must be unique", Map.of("row", row), null));
      }
      var grid = marked.get("TIMETABLE_GRID_V1");
      if (((Element) grid.getCTTbl().getDomNode())
              .getElementsByTagNameNS(WORD, "vMerge")
              .getLength()
          > 0) throw new IllegalArgumentException("Vertical day merges are not supported");
      var headers = grid.getRow(0).getTableCells();
      if (headers.stream().anyMatch(cell -> span(cell) != 1))
        throw new IllegalArgumentException("Time headers must not be merged");
      var times =
          TimeColumns.parse(
              headers.subList(1, headers.size()).stream()
                  .map(DocxTimetableAdapter::cellText)
                  .toList(),
              false);
      List<Session> sessions = new ArrayList<>();
      var days = new HashSet<Integer>();
      for (int rowIndex = 1; rowIndex < grid.getNumberOfRows(); rowIndex++) {
        var cells = grid.getRow(rowIndex).getTableCells();
        int day = TimeColumns.day(cellText(cells.get(0)));
        if (day < 0 || !days.add(day)) {
          issues.add(
              issue(
                  "DAY_UNKNOWN",
                  "Each day must be unique and recognized",
                  Map.of("row", rowIndex),
                  null));
          continue;
        }
        int column = 0;
        for (var cell : cells.subList(1, cells.size())) {
          int span = span(cell);
          if (column + span > times.size())
            throw new IllegalArgumentException("Grid column count differs from headers");
          String text = cellText(cell).strip();
          if (!text.isEmpty()) {
            Map<String, Object> source =
                Map.of(
                    "table",
                    "TIMETABLE_GRID_V1",
                    "row",
                    rowIndex,
                    "column",
                    column + 1,
                    "text",
                    text);
            var lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
            String room = null;
            List<String> faculty = List.of();
            boolean format =
                lines.size() == 3 && lines.get(1).startsWith("R:") && lines.get(2).startsWith("F:");
            if (format) {
              room = lines.get(1).substring(2).strip();
              faculty =
                  List.of(lines.get(2).substring(2).split(";")).stream()
                      .map(String::strip)
                      .filter(value -> !value.isEmpty())
                      .toList();
            }
            var session =
                new Session(
                    day,
                    TimeColumns.clock(times.get(column)[0]),
                    TimeColumns.clock(times.get(column + span - 1)[1]),
                    room,
                    lines.get(0),
                    faculty,
                    source);
            sessions.add(session);
            if (!format)
              issues.add(
                  issue(
                      "CELL_FORMAT",
                      "Use subject code, R: room ID and F: faculty IDs",
                      source,
                      session.id()));
            if (!subjects.containsKey(lines.get(0)))
              issues.add(
                  issue("SUBJECT_UNKNOWN", "Subject is missing from legend", source, session.id()));
            for (int i = column; i < column + span - 1; i++)
              if (times.get(i)[1] != times.get(i + 1)[0])
                issues.add(
                    issue(
                        "MERGE_ACROSS_BREAK", "Do not merge across a break", source, session.id()));
          }
          column += span;
        }
        if (column != times.size())
          throw new IllegalArgumentException("Grid column count differs from headers");
      }
      return new Extraction(sessions, issues, metadata);
    }
  }

  private static String cellText(XWPFTableCell cell) {
    return cell.getParagraphs().stream()
        .map(paragraph -> paragraph.getText())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private static int span(XWPFTableCell cell) {
    var spans = ((Element) cell.getCTTc().getDomNode()).getElementsByTagNameNS(WORD, "gridSpan");
    return spans.getLength() == 0
        ? 1
        : Integer.parseInt(((Element) spans.item(0)).getAttributeNS(WORD, "val"));
  }

  private static void boundExpandedArchive(byte[] bytes) throws IOException {
    long expanded = 0;
    int entries = 0;
    byte[] buffer = new byte[8192];
    try (var archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      while (archive.getNextEntry() != null) {
        if (++entries > 2000) throw new IllegalArgumentException("DOCX has too many entries");
        int read;
        while ((read = archive.read(buffer)) != -1) {
          expanded += read;
          if (expanded > 30L * 1024 * 1024)
            throw new IllegalArgumentException("DOCX expanded content exceeds 30 MiB");
        }
      }
    }
  }
}
