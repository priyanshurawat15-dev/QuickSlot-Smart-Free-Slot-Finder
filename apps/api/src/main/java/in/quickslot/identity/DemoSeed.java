package in.quickslot.identity;

import in.quickslot.demo.ReferenceTimetableSeed;
import in.quickslot.inventory.SourceInventory;
import in.quickslot.shared.AuthenticatedUser;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "quickslot.demo-seed", havingValue = "true")
public class DemoSeed implements CommandLineRunner {
  private final JdbcTemplate jdbc;
  private final AuthService auth;
  private final Environment environment;
  private final SourceInventory inventory;
  private final ReferenceTimetableSeed referenceTimetables;

  public DemoSeed(
      JdbcTemplate jdbc,
      AuthService auth,
      Environment environment,
      SourceInventory inventory,
      ReferenceTimetableSeed referenceTimetables) {
    this.jdbc = jdbc;
    this.auth = auth;
    this.environment = environment;
    this.inventory = inventory;
    this.referenceTimetables = referenceTimetables;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (!environment.matchesProfiles("demo"))
      throw new IllegalStateException("Demo seeding requires the demo profile.");
    for (String role :
        new String[] {"FACULTY", "CLUB_HEAD", "COORDINATOR", "HOD", "ADMIN", "STUDENT"}) {
      String name = role.equals("CLUB_HEAD") ? "club" : role.toLowerCase(java.util.Locale.ROOT);
      jdbc.update(
          "INSERT INTO users(id,full_name,email,password_hash,role) VALUES (?,?,?,?,?) ON"
              + " CONFLICT(email) DO UPDATE SET full_name=EXCLUDED.full_name",
          UUID.randomUUID().toString(),
          role.replace('_', ' '),
          name + "@demo.quickslot.test",
          auth.passwordHash("QuickSlot-demo-2026!"),
          role);
    }
    jdbc.update(
        "UPDATE rooms SET active=false WHERE building='Demo academic block' AND code IN"
            + " ('017','LT-101','203')");
    inventory.registerProvidedTimetables();
    jdbc.update(
        "INSERT INTO club_managers(club_id,user_id) SELECT c.id,u.id FROM clubs c CROSS JOIN users"
            + " u WHERE c.slug='codev' AND u.email='club@demo.quickslot.test' AND"
            + " u.role='CLUB_HEAD' AND u.campus_id=c.campus_id ON CONFLICT DO NOTHING");
    jdbc.update(
        "INSERT INTO allocations(user_id,room_id) SELECT u.id,r.id FROM users u CROSS JOIN rooms r"
            + " WHERE u.email='coordinator@demo.quickslot.test' AND r.active AND r.source_label<>''"
            + " ON CONFLICT DO NOTHING");
    String adminId =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email='admin@demo.quickslot.test'", String.class);
    referenceTimetables.seed(
        new AuthenticatedUser(
            adminId, "ADMIN", "admin@demo.quickslot.test", "ADMIN", "gehu-hill-dehradun", "CS"));
  }
}
