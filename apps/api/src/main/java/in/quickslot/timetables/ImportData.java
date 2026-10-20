package in.quickslot.timetables;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ImportData {
  private ImportData() {}

  public record Session(
      String id,
      int weekday,
      String startTime,
      String endTime,
      String roomId,
      String subject,
      List<String> facultyIds,
      Map<String, Object> source) {
    public Session(
        int weekday,
        String startTime,
        String endTime,
        String roomId,
        String subject,
        List<String> facultyIds,
        Map<String, Object> source) {
      this(
          UUID.randomUUID().toString(),
          weekday,
          startTime,
          endTime,
          roomId,
          subject,
          facultyIds,
          source);
    }
  }

  public record Issue(String code, String message, String sessionId, Map<String, Object> source) {}

  public record Extraction(
      List<Session> sessions, List<Issue> issues, Map<String, String> metadata) {}

  public static Issue issue(String code, String message, Map<String, Object> source, String id) {
    return new Issue(code, message, id, source);
  }
}
