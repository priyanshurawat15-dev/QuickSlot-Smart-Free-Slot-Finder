package in.quickslot.requests;

import in.quickslot.shared.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RequestRepository {
  private final JdbcTemplate jdbc;

  public RequestRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private BookingRequest map(ResultSet row, int index) throws SQLException {
    return new BookingRequest(
        row.getString("id"),
        row.getString("requester_id"),
        row.getString("room_id"),
        row.getObject("date", LocalDate.class),
        row.getObject("start_time", LocalTime.class),
        row.getObject("end_time", LocalTime.class),
        row.getString("purpose"),
        row.getInt("attendance"),
        row.getString("status"),
        row.getString("reason_category"),
        row.getString("club_id"),
        row.getString("club_name"),
        row.getBoolean("club_membership_required"));
  }

  public BookingRequest get(String id, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM booking_requests WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            this::map,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(404, "Request not found."));
  }

  public List<BookingRequest> all() {
    return jdbc.query("SELECT * FROM booking_requests ORDER BY created_at DESC", this::map);
  }
}
