package in.quickslot.inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SourceInventory {
  private final JdbcTemplate jdbc;

  public SourceInventory(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void registerProvidedTimetables() {
    for (String filename : new String[] {"B.Tech-CSE-III.pdf", "B.Tech-CSE-V.pdf"}) {
      try (var stream = new ClassPathResource("timetables/" + filename).getInputStream()) {
        byte[] content = stream.readAllBytes();
        String digest =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        for (var label : new PdfRoomInventory().extract(content)) {
          String roomId =
              UUID.nameUUIDFromBytes(
                      ("gehu-hill-dehradun:" + label.printedLabel())
                          .getBytes(StandardCharsets.UTF_8))
                  .toString();
          jdbc.update(
              "INSERT INTO rooms(id,code,kind,building,capacity,source_label,floor) VALUES"
                  + " (?,?,?,'',NULL,?,?) ON CONFLICT(id) DO NOTHING",
              roomId,
              label.code(),
              label.kind(),
              "Provided CSE timetable",
              floorFromCode(label.code()));
          jdbc.update(
              "INSERT INTO room_sources(room_id,filename,sha256,printed_label) VALUES (?,?,?,?) ON"
                  + " CONFLICT DO NOTHING",
              roomId,
              filename,
              digest,
              label.printedLabel());
        }
      } catch (IOException | java.security.NoSuchAlgorithmException error) {
        throw new IllegalStateException("Cannot register provided timetable room inventory", error);
      }
    }
  }

  static String floorFromCode(String code) {
    return switch (Integer.parseInt(code.split("-")[0]) / 100) {
      case 0 -> "Ground floor";
      case 1 -> "First floor";
      case 2 -> "Second floor";
      default -> null;
    };
  }
}
