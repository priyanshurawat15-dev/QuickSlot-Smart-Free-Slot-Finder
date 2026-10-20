package in.quickslot.demo;

import static org.assertj.core.api.Assertions.*;

import in.quickslot.availability.AvailabilityService;
import in.quickslot.inventory.SourceInventory;
import in.quickslot.shared.AuthenticatedUser;
import in.quickslot.timetables.ImportData;
import in.quickslot.timetables.TimetableService;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("demo")
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class ReferenceTimetableSeedIntegrationTest {
  private static final String SCHEMA =
      "test_reference_" + UUID.randomUUID().toString().replace("-", "");
  private static final String URL = System.getenv("TEST_DATABASE_URL");
  private static final String USER =
      System.getenv().getOrDefault("TEST_DATABASE_USER", "quickslot");
  private static final String PASSWORD = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "");
  private final AuthenticatedUser admin =
      new AuthenticatedUser(
          "admin", "Administrator", "admin@test.invalid", "ADMIN", "gehu-hill-dehradun", "CS");
  @Autowired ReferenceTimetableSeed seed;
  @Autowired SourceInventory inventory;
  @Autowired TimetableService timetables;
  @Autowired AvailabilityService availability;
  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws Exception {
    try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
        var statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA " + SCHEMA);
      statement.execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
    }
    registry.add(
        "spring.datasource.url",
        () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public");
    registry.add("spring.datasource.username", () -> USER);
    registry.add("spring.datasource.password", () -> PASSWORD);
    registry.add("spring.flyway.default-schema", () -> SCHEMA);
    registry.add("spring.flyway.schemas", () -> SCHEMA);
    registry.add("quickslot.demo-seed", () -> false);
  }

  @AfterAll
  static void cleanup() throws Exception {
    try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
        var statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }
  }

  @Test
  void actualPdfDataDrivesRoomStatesAndRestartDoesNotRestoreRemovedOrReplacedTimetables()
      throws Exception {
    jdbc.update(
        "INSERT INTO users(id,full_name,email,password_hash,role) VALUES"
            + " ('admin','Administrator','admin@test.invalid','unused','ADMIN')");
    inventory.registerProvidedTimetables();
    seed.seed(admin);
    var revisions = timetables.list(admin);
    assertThat(revisions).hasSize(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_publications", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_faculty", Integer.class))
        .isZero();
    var roomIds = jdbc.queryForList("SELECT id FROM rooms WHERE active", String.class);
    assertThat(roomIds).hasSize(12);
    for (String roomId : roomIds) {
      assertThat(
              availability.state(
                  roomId, LocalDate.of(2026, 7, 19), LocalTime.of(8, 0), LocalTime.of(9, 0)))
          .isEqualTo("AVAILABLE");
    }
    for (var revision : revisions) {
      assertThat(revision).containsEntry("valid_until", null).containsEntry("status", "PUBLISHED");
      try (var original =
          new ClassPathResource("timetables/" + revision.get("filename")).getInputStream()) {
        assertThat(timetables.source(revision.get("id").toString(), admin).content())
            .isEqualTo(original.readAllBytes());
      }
      @SuppressWarnings("unchecked")
      var sessions =
          (List<ImportData.Session>)
              timetables.detail(revision.get("id").toString(), admin).get("sessions");
      assertThat(sessions)
          .isNotEmpty()
          .allSatisfy(
              entry -> {
                assertThat(entry.facultyIds()).isEmpty();
                assertThat(entry.source()).containsKey("cells");
              });
      var entry = sessions.getFirst();
      assertThat(
              availability.state(
                  entry.roomId(),
                  LocalDate.of(2026, 7, 13).plusDays(entry.weekday()),
                  LocalTime.parse(entry.startTime()),
                  LocalTime.parse(entry.endTime())))
          .isEqualTo("OCCUPIED");
    }
    var original =
        revisions.stream()
            .filter(revision -> revision.get("scope_key").equals(ReferenceTimetableSeed.III_SCOPE))
            .findFirst()
            .orElseThrow();
    var second =
        revisions.stream()
            .filter(revision -> revision.get("scope_key").equals(ReferenceTimetableSeed.V_SCOPE))
            .findFirst()
            .orElseThrow();
    String replacement;
    try (var content = new ClassPathResource("timetables/B.Tech-CSE-III.pdf").getInputStream()) {
      replacement =
          timetables
              .upload(
                  admin,
                  "replacement.pdf",
                  content.readAllBytes(),
                  ReferenceTimetableSeed.III_SCOPE,
                  LocalDate.of(2030, 1, 1),
                  null,
                  List.of())
              .get("id")
              .toString();
    }
    timetables.review(
        replacement,
        new TimetableService.Review(
            0,
            List.of(
                new ImportData.Session(
                    0, "18:00", "19:00", roomIds.getFirst(), "CS301", List.of(), Map.of())),
            roomIds,
            true),
        admin);
    timetables.publish(replacement, 1, admin);
    assertThat(
            jdbc.queryForObject(
                "SELECT valid_until FROM timetable_publications WHERE revision_id=?",
                LocalDate.class,
                original.get("id")))
        .isEqualTo(LocalDate.of(2029, 12, 31));
    timetables.retire(second.get("id").toString(), 2, LocalDate.of(2030, 1, 1), admin);
    timetables.retire(replacement, 2, LocalDate.of(2030, 1, 1), admin);
    seed.seed(admin);
    assertThat(timetables.list(admin)).hasSize(3);
    for (String roomId : roomIds) {
      assertThat(
              availability.state(
                  roomId, LocalDate.of(2032, 7, 12), LocalTime.of(9, 0), LocalTime.of(10, 0)))
          .isEqualTo("UNKNOWN");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE"
                    + " action='timetable.reference_dataset_accepted'",
                Integer.class))
        .isEqualTo(2);
  }
}
