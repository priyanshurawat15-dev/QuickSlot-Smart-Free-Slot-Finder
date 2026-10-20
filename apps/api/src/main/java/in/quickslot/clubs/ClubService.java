package in.quickslot.clubs;

import in.quickslot.shared.ApiException;
import in.quickslot.shared.AuditService;
import in.quickslot.shared.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClubService {
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  public ClubService(JdbcTemplate jdbc, AuditService audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  public List<Map<String, Object>> list() {
    return jdbc.queryForList("SELECT slug FROM clubs ORDER BY name").stream()
        .map(row -> profile(row.get("slug").toString()))
        .toList();
  }

  public Map<String, Object> profile(String slug) {
    var club = find(slug);
    var result = new LinkedHashMap<>(club);
    result.remove("campus_id");
    result.put(
        "leadership",
        jdbc.queryForList(
            "SELECT name,role FROM club_leadership WHERE club_id=? ORDER BY position",
            club.get("id")));
    return result;
  }

  private Map<String, Object> find(String slug) {
    var rows = jdbc.queryForList("SELECT * FROM clubs WHERE slug=?", slug);
    if (rows.isEmpty()) throw new ApiException(404, "Club not found.");
    return rows.getFirst();
  }

  private boolean canReview(AuthenticatedUser user, Map<String, Object> club) {
    if (!user.campusId().equals(club.get("campus_id"))) return false;
    if (user.role().equals("ADMIN")) return true;
    return user.role().equals("CLUB_HEAD")
        && Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM club_managers WHERE club_id=? AND user_id=?)",
                Boolean.class,
                club.get("id"),
                user.id()));
  }

  public Map<String, Object> membership(AuthenticatedUser user, String slug) {
    var club = find(slug);
    var statuses =
        jdbc.queryForList(
            "SELECT status FROM club_memberships WHERE club_id=? AND student_id=?",
            club.get("id"),
            user.id());
    return Map.of(
        "status",
        statuses.isEmpty() ? "NONE" : statuses.getFirst().get("status"),
        "can_review",
        canReview(user, club));
  }

  @Transactional
  public Map<String, Object> join(AuthenticatedUser user, String slug) {
    var club = find(slug);
    if (!user.role().equals("STUDENT") || !user.campusId().equals(club.get("campus_id")))
      throw new ApiException(403, "Only students from this campus can request membership.");
    // The account lock serializes repeated joins before the unique membership row exists.
    jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", user.id());
    var existing =
        jdbc.queryForList(
            "SELECT * FROM club_memberships WHERE club_id=? AND student_id=?",
            club.get("id"),
            user.id());
    if (!existing.isEmpty() && !existing.getFirst().get("status").equals("REJECTED"))
      return member(existing.getFirst().get("id").toString());
    String id =
        existing.isEmpty()
            ? UUID.randomUUID().toString()
            : existing.getFirst().get("id").toString();
    jdbc.update(
        "INSERT INTO club_memberships(id,club_id,student_id,status) VALUES (?,?,?,'PENDING')"
            + " ON CONFLICT(club_id,student_id) DO UPDATE SET status='PENDING',created_at=now(),"
            + " reviewed_at=NULL,reviewed_by=NULL,reason=''",
        id,
        club.get("id"),
        user.id());
    audit.record(user, "CLUB_MEMBERSHIP_REQUESTED", id, Map.of("club_id", club.get("id")));
    return member(id);
  }

  public List<Map<String, Object>> memberships(AuthenticatedUser user, String slug) {
    var club = find(slug);
    if (!canReview(user, club))
      throw new ApiException(403, "Club membership review permission required.");
    return jdbc.queryForList(
        "SELECT m.id,u.full_name AS student_name,m.status,m.created_at FROM club_memberships m"
            + " JOIN users u ON u.id=m.student_id WHERE m.club_id=? ORDER BY m.created_at DESC",
        club.get("id"));
  }

  @Transactional
  public Map<String, Object> decide(
      AuthenticatedUser user, String slug, String id, String action, String reason) {
    var club = find(slug);
    if (!canReview(user, club))
      throw new ApiException(403, "Club membership review permission required.");
    var rows =
        jdbc.queryForList(
            "SELECT * FROM club_memberships WHERE id=? AND club_id=? FOR UPDATE",
            id,
            club.get("id"));
    if (rows.isEmpty()) throw new ApiException(404, "Membership request not found.");
    var membership = rows.getFirst();
    if (user.id().equals(membership.get("student_id")))
      throw new ApiException(403, "You cannot verify your own membership.");
    String next =
        switch (action) {
          case "approve" -> "VERIFIED";
          case "reject" -> "REJECTED";
          default -> throw new ApiException(404, "Membership action not found.");
        };
    if (action.equals("reject") && reason.isBlank())
      throw new ApiException(422, "Give a reason for rejecting this membership request.");
    if (membership.get("status").equals(next)) return member(id);
    if (!membership.get("status").equals("PENDING"))
      throw new ApiException(409, "This membership request has already been reviewed.");
    jdbc.update(
        "UPDATE club_memberships SET status=?,reviewed_at=?,reviewed_by=?,reason=? WHERE id=?",
        next,
        OffsetDateTime.now(),
        user.id(),
        reason,
        id);
    audit.record(
        user,
        "CLUB_MEMBERSHIP_" + next,
        id,
        Map.of(
            "club_id",
            club.get("id"),
            "student_id",
            membership.get("student_id"),
            "reason",
            reason));
    return member(id);
  }

  private Map<String, Object> member(String id) {
    return jdbc.queryForMap(
        "SELECT m.id,u.full_name AS student_name,m.status,m.created_at"
            + " FROM club_memberships m JOIN users u ON u.id=m.student_id WHERE m.id=?",
        id);
  }
}
