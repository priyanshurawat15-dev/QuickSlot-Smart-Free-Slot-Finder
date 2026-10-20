package in.quickslot.inventory;

import in.quickslot.shared.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoomRepository {
  private final JdbcTemplate jdbc;

  public RoomRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private Room map(ResultSet row, int index) throws SQLException {
    return new Room(
        row.getString("id"),
        row.getString("code"),
        row.getString("kind"),
        row.getString("building"),
        row.getObject("capacity", Integer.class),
        row.getString("campus_id"),
        row.getString("department"),
        row.getBoolean("active"),
        row.getString("source_label"),
        row.getString("floor"));
  }

  public List<Room> publicRooms() {
    return jdbc.query(
        "SELECT * FROM rooms WHERE active AND campus_id='gehu-hill-dehradun' ORDER BY code",
        this::map);
  }

  public Room get(String id) {
    return jdbc.query("SELECT * FROM rooms WHERE id=?", this::map, id).stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(404, "Room not found."));
  }
}
