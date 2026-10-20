package in.quickslot.inventory;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

public final class PdfRoomInventory {
  private static final Pattern ROOM =
      Pattern.compile(
          "\\b(\\d{2,3}(?:-[A-Z])?)\\s*\\(\\s*(CR|LT)\\s*\\)", Pattern.CASE_INSENSITIVE);

  public record RoomLabel(String code, String kind, String printedLabel) {}

  public List<RoomLabel> extract(byte[] content) throws IOException {
    try (var document = Loader.loadPDF(content)) {
      var text = new PDFTextStripper().getText(document);
      var found = new LinkedHashSet<RoomLabel>();
      var matcher = ROOM.matcher(text);
      while (matcher.find()) {
        String code = matcher.group(1).toUpperCase(java.util.Locale.ROOT);
        String convention = matcher.group(2).toUpperCase(java.util.Locale.ROOT);
        found.add(
            new RoomLabel(
                code,
                convention.equals("LT") ? "LT" : "CLASSROOM",
                code + " (" + convention + ")"));
      }
      if (found.isEmpty())
        throw new IOException("No CR or LT room labels found in the source timetable");
      return List.copyOf(found);
    }
  }
}
