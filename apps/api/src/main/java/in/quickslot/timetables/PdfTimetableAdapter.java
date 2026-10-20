package in.quickslot.timetables;

import static in.quickslot.timetables.ImportData.*;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public final class PdfTimetableAdapter {
  record Glyph(float x, float y, String text) {}

  record Line(double x0, double y0, double x1, double y1) {}

  public Extraction extract(byte[] content) throws IOException {
    List<Session> sessions = new ArrayList<>();
    List<Issue> issues = new ArrayList<>();
    try (var document = Loader.loadPDF(content)) {
      if (document.getNumberOfPages() > 80)
        throw new IllegalArgumentException("PDF exceeds 80 pages");
      for (int number = 1; number <= document.getNumberOfPages(); number++) {
        var page = document.getPage(number - 1);
        if (page.getRotation() != 0) {
          issues.add(
              issue(
                  "PAGE_ROTATED",
                  "Rotate this page before importing",
                  Map.of("page", number),
                  null));
          continue;
        }
        var glyphs = new ArrayList<Glyph>();
        var stripper =
            new PDFTextStripper() {
              @Override
              protected void processTextPosition(TextPosition position) {
                glyphs.add(
                    new Glyph(position.getXDirAdj(), position.getYDirAdj(), position.getUnicode()));
                super.processTextPosition(position);
              }
            };
        stripper.setStartPage(number);
        stripper.setEndPage(number);
        stripper.getText(document);
        var geometry = new Geometry(page);
        geometry.processPage(page);
        double width = page.getCropBox().getWidth();
        var horizontal =
            joinedHorizontal(geometry.lines).stream()
                .filter(line -> line.x1 - line.x0 > width * .65)
                .toList();
        var boundaries = unique(horizontal.stream().map(Line::y0).sorted().toList());
        boolean found = false;
        for (int header = 0; header + 1 < boundaries.size(); header++) {
          double top = boundaries.get(header), bottom = boundaries.get(header + 1);
          var border =
              horizontal.stream()
                  .filter(line -> Math.abs(line.y0 - top) < 1)
                  .findFirst()
                  .orElseThrow();
          double left = Math.min(border.x0, border.x1), right = Math.max(border.x0, border.x1);
          // Text-box outlines inside a cell are not timetable column borders.
          var columns =
              unique(
                  geometry.lines.stream()
                      .filter(
                          line ->
                              Math.abs(line.x0 - line.x1) < 1
                                  && Math.min(line.y0, line.y1) <= top + 2
                                  && Math.max(line.y0, line.y1) >= bottom - 2
                                  && line.x0 >= left - 1
                                  && line.x0 <= right + 1)
                      .map(Line::x0)
                      .sorted()
                      .toList());
          if (columns.size() < 3
              || !text(glyphs, columns.get(0), top, columns.get(1), bottom).contains("DAY"))
            continue;
          found = true;
          List<String> headers = new ArrayList<>();
          for (int i = 1; i + 1 < columns.size(); i++)
            headers.add(text(glyphs, columns.get(i), top, columns.get(i + 1), bottom));
          List<int[]> times;
          try {
            times = TimeColumns.parse(headers, true);
          } catch (IllegalArgumentException error) {
            issues.add(issue("TIME_HEADERS", error.getMessage(), Map.of("page", number), null));
            break;
          }
          int rowIndex = 0;
          for (int row = header + 1; row + 1 < boundaries.size(); row++) {
            double rowTop = boundaries.get(row), rowBottom = boundaries.get(row + 1);
            int day =
                TimeColumns.day(text(glyphs, columns.get(0), rowTop, columns.get(1), rowBottom));
            if (day < 0) break;
            rowIndex++;
            double rowMiddle = (rowTop + rowBottom) / 2;
            int column = 1;
            while (column + 1 < columns.size()) {
              int end = column + 1;
              while (end + 1 < columns.size()
                  && !verticalAt(geometry.lines, columns.get(end), rowMiddle)) end++;
              String raw = text(glyphs, columns.get(column), rowTop, columns.get(end), rowBottom);
              if (!raw.isBlank()) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("page", number);
                source.put("row", rowIndex);
                source.put("column", column);
                source.put(
                    "bbox", List.of(columns.get(column), rowTop, columns.get(end), rowBottom));
                source.put("text", raw);
                var entry =
                    new Session(
                        day,
                        TimeColumns.clock(times.get(column - 1)[0]),
                        TimeColumns.clock(times.get(end - 2)[1]),
                        null,
                        raw.lines().findFirst().orElse(raw),
                        List.of(),
                        source);
                sessions.add(entry);
                issues.add(
                    issue(
                        "CELL_REVIEW",
                        "Resolve subject, room and faculty against approved directories",
                        source,
                        entry.id()));
                for (int i = column - 1; i < end - 2; i++)
                  if (times.get(i)[1] != times.get(i + 1)[0])
                    issues.add(
                        issue(
                            "MERGE_ACROSS_BREAK",
                            "Merged cell crosses a break; confirm its duration",
                            source,
                            entry.id()));
                if (sessions.size() > 3000)
                  throw new IllegalArgumentException("Timetable exceeds 3000 sessions");
              }
              column = end;
            }
          }
          break;
        }
        if (!found)
          issues.add(
              issue(
                  "GRID_NOT_FOUND",
                  "No supported ruled timetable grid on this page",
                  Map.of("page", number),
                  null));
      }
    }
    return new Extraction(sessions, issues, Map.of());
  }

  private static List<Line> joinedHorizontal(List<Line> segments) {
    var sorted =
        segments.stream()
            .filter(line -> Math.abs(line.y0 - line.y1) < 1 && Math.abs(line.x1 - line.x0) > 2)
            .map(
                line ->
                    new Line(
                        Math.min(line.x0, line.x1), line.y0, Math.max(line.x0, line.x1), line.y0))
            .sorted(Comparator.comparingDouble(Line::y0).thenComparingDouble(Line::x0))
            .toList();
    List<List<Line>> rows = new ArrayList<>();
    for (var line : sorted) {
      if (rows.isEmpty() || Math.abs(rows.getLast().getFirst().y0 - line.y0) > 1)
        rows.add(new ArrayList<>());
      rows.getLast().add(line);
    }
    List<Line> joined = new ArrayList<>();
    for (var row : rows) {
      row.sort(Comparator.comparingDouble(Line::x0));
      Line current = row.getFirst();
      for (var next : row.subList(1, row.size())) {
        if (next.x0 <= current.x1 + 2)
          current = new Line(current.x0, current.y0, Math.max(current.x1, next.x1), current.y0);
        else {
          joined.add(current);
          current = next;
        }
      }
      joined.add(current);
    }
    return joined;
  }

  private static List<Double> unique(List<Double> sorted) {
    List<Double> result = new ArrayList<>();
    for (double value : sorted)
      if (result.isEmpty() || value - result.get(result.size() - 1) > 2) result.add(value);
    return result;
  }

  private static boolean verticalAt(List<Line> lines, double x, double y) {
    return lines.stream()
        .anyMatch(
            line ->
                Math.abs(line.x0 - line.x1) < 1
                    && Math.abs(line.x0 - x) < 1.5
                    && Math.min(line.y0, line.y1) < y
                    && Math.max(line.y0, line.y1) > y);
  }

  private static String text(
      List<Glyph> glyphs, double left, double top, double right, double bottom) {
    var inside =
        glyphs.stream()
            .filter(g -> g.x >= left - .3 && g.x < right - .3 && g.y > top && g.y < bottom)
            .sorted(Comparator.comparingDouble(Glyph::y).thenComparingDouble(Glyph::x))
            .toList();
    List<List<Glyph>> rows = new ArrayList<>();
    for (var glyph : inside) {
      if (rows.isEmpty() || Math.abs(rows.get(rows.size() - 1).get(0).y - glyph.y) > 2)
        rows.add(new ArrayList<>());
      rows.get(rows.size() - 1).add(glyph);
    }
    List<String> lines = new ArrayList<>();
    for (var row : rows) {
      row.sort(Comparator.comparingDouble(Glyph::x));
      StringBuilder line = new StringBuilder();
      for (var glyph : row) line.append(glyph.text);
      if (!line.toString().isBlank()) lines.add(line.toString().strip());
    }
    return String.join("\n", lines);
  }

  static final class Geometry extends PDFGraphicsStreamEngine {
    final List<Line> lines = new ArrayList<>();
    private final List<Line> pending = new ArrayList<>();
    private Point2D current = new Point2D.Double();
    private Point2D first = current;
    private final double height;

    Geometry(PDPage page) {
      super(page);
      height = page.getCropBox().getHeight();
    }

    private void segment(Point2D from, Point2D to) {
      pending.add(new Line(from.getX(), height - from.getY(), to.getX(), height - to.getY()));
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
      segment(p0, p1);
      segment(p1, p2);
      segment(p2, p3);
      segment(p3, p0);
      current = p0;
      first = p0;
    }

    @Override
    public void drawImage(PDImage image) {}

    @Override
    public void clip(int windingRule) {}

    @Override
    public void moveTo(float x, float y) {
      current = new Point2D.Float(x, y);
      first = current;
    }

    @Override
    public void lineTo(float x, float y) {
      var next = new Point2D.Float(x, y);
      segment(current, next);
      current = next;
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
      current = new Point2D.Float(x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
      return current;
    }

    @Override
    public void closePath() {
      segment(current, first);
      current = first;
    }

    @Override
    public void endPath() {
      pending.clear();
    }

    @Override
    public void strokePath() {
      lines.addAll(pending);
      pending.clear();
    }

    @Override
    public void fillPath(int windingRule) {
      lines.addAll(pending);
      pending.clear();
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
      lines.addAll(pending);
      pending.clear();
    }

    @Override
    public void shadingFill(COSName name) {}
  }
}
