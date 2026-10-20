package in.quickslot.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public AuditService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void record(
      AuthenticatedUser user, String action, String entityId, Map<String, ?> details) {
    try {
      jdbc.update(
          "INSERT INTO audit_events(id,actor_id,action,entity_id,details) VALUES"
              + " (?,?,?,?,?::jsonb)",
          UUID.randomUUID().toString(),
          user.id(),
          action,
          entityId,
          json.writeValueAsString(details));
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Audit data cannot be serialized", error);
    }
  }
}
