package in.quickslot.timetables;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import in.quickslot.availability.AvailabilityService;
import in.quickslot.identity.AuthService;
import in.quickslot.shared.ApiException;
import in.quickslot.shared.AuthenticatedUser;
import jakarta.servlet.http.Cookie;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class TimetableDeletionIntegrationTest {
  private static final String SCHEMA =
      "test_delete_timetable_" + UUID.randomUUID().toString().replace("-", "");
  private static final String URL = System.getenv("TEST_DATABASE_URL");
  private static final String USER =
      System.getenv().getOrDefault("TEST_DATABASE_USER", "quickslot");
  private static final String PASSWORD = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "");
  private static final LocalDate TODAY = LocalDate.now(AvailabilityService.INDIA);
  private final AuthenticatedUser admin =
      new AuthenticatedUser(
          "admin", "Administrator", "admin@test.invalid", "ADMIN", "gehu-hill-dehradun", "CS");
  @Autowired TimetableService timetables;
  @Autowired AvailabilityService availability;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

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

  @BeforeEach
  void prepare() {
    jdbc.execute("TRUNCATE users,rooms,timetable_scopes,timetable_faculty CASCADE");
    jdbc.update(
        "INSERT INTO users(id,full_name,email,password_hash,role) VALUES"
            + " ('admin','Administrator','admin@test.invalid','unused','ADMIN')");
    jdbc.update(
        "INSERT INTO rooms(id,code,kind,building,capacity) VALUES ('room','017','CLASSROOM','Test"
            + " block',40),('other','106','LT','Test block',40)");
  }

  private String draft(String scope) throws Exception {
    try (var document = TimetableAdaptersTest.template()) {
      return timetables
          .upload(
              admin,
              "source.docx",
              TimetableAdaptersTest.bytes(document),
              scope,
              TODAY.minusDays(30),
              null,
              List.of())
          .get("id")
          .toString();
    }
  }

  private TimetableService.Review review(int version, String room) {
    return new TimetableService.Review(
        version,
        List.of(
            new ImportData.Session(
                TODAY.getDayOfWeek().getValue() - 1,
                "09:00",
                "10:00",
                room,
                "CS301",
                List.of(),
                Map.of())),
        List.of(room),
        true);
  }

  private String published(String scope, String room) throws Exception {
    String id = draft(scope);
    timetables.review(id, review(0, room), admin);
    timetables.publish(id, 1, admin);
    return id;
  }

  private String state(String room, LocalDate date, int hour) {
    return availability.state(room, date, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0));
  }

  @Test
  void deletionStopsOnlySelectedTimetableFromTodayWhileSourceAndPastDatesRemain() throws Exception {
    String selected = published("CSE-III", "room");
    String untouched = published("CSE-V", "other");
    byte[] source = timetables.source(selected, admin).content();
    var bookingStart =
        TODAY.plusDays(7).atTime(14, 0).atZone(AvailabilityService.INDIA).toOffsetDateTime();
    jdbc.update(
        "INSERT INTO occupancies(id,room_id,starts_at,ends_at,kind,source_id) VALUES"
            + " ('booking','room',?,?,'BOOKING','booking')",
        bookingStart,
        bookingStart.plusHours(1));
    assertThat(state("room", TODAY.plusDays(7), 9)).isEqualTo("OCCUPIED");
    var result = timetables.archive(selected, 2, admin);
    assertThat(result.get("archived_at")).isNotNull();
    assertThat(result).containsEntry("version", 3);
    assertThat(state("room", TODAY, 9)).isEqualTo("UNKNOWN");
    assertThat(state("room", TODAY.plusDays(7), 9)).isEqualTo("UNKNOWN");
    assertThat(state("room", TODAY.minusDays(7), 9)).isEqualTo("OCCUPIED");
    assertThat(state("other", TODAY.plusDays(7), 9)).isEqualTo("OCCUPIED");
    assertThat(state("room", TODAY.plusDays(7), 14)).isEqualTo("OCCUPIED");
    assertThat(timetables.list(admin))
        .extracting(item -> item.get("id"))
        .containsExactly(untouched);
    assertThat(timetables.list(admin, true)).hasSize(2);
    assertThat(timetables.list(admin, true))
        .allSatisfy(summary -> assertThat(summary).doesNotContainKeys("sessions", "issues"));
    assertThat(timetables.source(selected, admin).content()).isEqualTo(source);
    assertThat(timetables.detail(selected, admin).get("sessions")).asList().hasSize(1);
    assertThat(timetables.detail(selected, admin)).containsKeys("sessions", "issues");
    assertThat(
            jdbc.queryForObject(
                "SELECT valid_until FROM timetable_publications WHERE revision_id=?",
                LocalDate.class,
                selected))
        .isEqualTo(TODAY.minusDays(1));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action='timetable.deleted' AND entity_id=?"
                    + " AND jsonb_array_length(details->'previous_windows')=1",
                Integer.class,
                selected))
        .isEqualTo(1);
  }

  @Test
  void deletingFuturePublicationDoesNotRestoreAnOlderReplacedTimetable() throws Exception {
    String original = published("CSE", "room");
    String future;
    try (var document = TimetableAdaptersTest.template()) {
      future =
          timetables
              .upload(
                  admin,
                  "future.docx",
                  TimetableAdaptersTest.bytes(document),
                  "CSE",
                  TODAY.plusDays(7),
                  null,
                  List.of())
              .get("id")
              .toString();
    }
    timetables.review(future, review(0, "other"), admin);
    timetables.publish(future, 1, admin);
    timetables.archive(future, 2, admin);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM timetable_publications WHERE revision_id=?",
                Integer.class,
                future))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT valid_until FROM timetable_publications WHERE revision_id=?",
                LocalDate.class,
                original))
        .isEqualTo(TODAY.plusDays(6));
    assertThat(state("room", TODAY, 9)).isEqualTo("OCCUPIED");
    assertThat(state("room", TODAY.plusDays(7), 9)).isEqualTo("UNKNOWN");
    assertThat(state("other", TODAY.plusDays(7), 9)).isEqualTo("UNKNOWN");
  }

  @Test
  void archivedDraftIsHiddenAndCannotBeReviewedPublishedOrRestoredByStaleMutations()
      throws Exception {
    String id = draft("draft");
    assertThatThrownBy(() -> timetables.archive(id, 1, admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("changed");
    assertThat(timetables.list(admin)).hasSize(1);
    timetables.archive(id, 0, admin);
    assertThat(timetables.list(admin)).isEmpty();
    assertThat(timetables.list(admin, true)).hasSize(1);
    assertThatThrownBy(() -> timetables.review(id, review(1, "room"), admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("deleted");
    assertThatThrownBy(() -> timetables.publish(id, 1, admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("deleted");
    assertThatThrownBy(() -> timetables.retire(id, 1, TODAY, admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("deleted");
    assertThatThrownBy(() -> timetables.archive(id, 1, admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("deleted");
    assertThat(timetables.source(id, admin).content()).isNotEmpty();
  }

  private record Login(Cookie cookie, String csrf) {}

  private Login login(String role, String campus) {
    String userId = UUID.randomUUID().toString();
    String token = UUID.randomUUID().toString();
    String csrf = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users(id,full_name,email,password_hash,role,campus_id) VALUES (?,?,?,?,?,?)",
        userId,
        role,
        userId + "@test.invalid",
        "unused",
        role,
        campus);
    jdbc.update(
        "INSERT INTO login_sessions(token_hash,user_id,csrf_token,expires_at) VALUES (?,?,?,?)",
        AuthService.hash(token),
        userId,
        csrf,
        OffsetDateTime.now().plusHours(1));
    return new Login(new Cookie(AuthService.COOKIE, token), csrf);
  }

  @Test
  void deleteRequiresScopedAdministratorCsrfAndCurrentVersionAndHistoryIsExplicit()
      throws Exception {
    String id = draft("private");
    String path = "/api/timetables/" + id;
    mvc.perform(delete(path).param("expected_revision", "0")).andExpect(status().isUnauthorized());
    var student = login("STUDENT", admin.campusId());
    mvc.perform(
            delete(path)
                .param("expected_revision", "0")
                .cookie(student.cookie())
                .header("X-CSRF-Token", student.csrf()))
        .andExpect(status().isForbidden());
    var administrator = login("ADMIN", admin.campusId());
    mvc.perform(delete(path).param("expected_revision", "0").cookie(administrator.cookie()))
        .andExpect(status().isForbidden());
    var otherCampus = login("ADMIN", "other-campus");
    mvc.perform(
            delete(path)
                .param("expected_revision", "0")
                .cookie(otherCampus.cookie())
                .header("X-CSRF-Token", otherCampus.csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(
            delete(path)
                .param("expected_revision", "1")
                .cookie(administrator.cookie())
                .header("X-CSRF-Token", administrator.csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            delete(path)
                .param("expected_revision", "0")
                .cookie(administrator.cookie())
                .header("X-CSRF-Token", administrator.csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived_at").isNotEmpty());
    mvc.perform(get("/api/timetables").cookie(administrator.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mvc.perform(
            get("/api/timetables").param("include_archived", "true").cookie(administrator.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get(path + "/source").cookie(administrator.cookie())).andExpect(status().isOk());
  }
}
