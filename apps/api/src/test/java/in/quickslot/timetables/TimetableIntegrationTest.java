package in.quickslot.timetables;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.quickslot.identity.AuthService;
import in.quickslot.shared.ApiException;
import in.quickslot.shared.AuthenticatedUser;
import jakarta.servlet.http.Cookie;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
class TimetableIntegrationTest {
  private static final String SCHEMA =
      "test_timetables_" + UUID.randomUUID().toString().replace("-", "");
  private static final String URL = System.getenv("TEST_DATABASE_URL");
  private static final String USER =
      System.getenv().getOrDefault("TEST_DATABASE_USER", "quickslot");
  private static final String PASSWORD = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "");
  private final AuthenticatedUser admin =
      new AuthenticatedUser(
          "admin", "Administrator", "admin@test.invalid", "ADMIN", "gehu-hill-dehradun", "CS");
  @Autowired TimetableService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired MockMvc mvc;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws Exception {
    try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
        var command = connection.createStatement()) {
      command.execute("CREATE SCHEMA " + SCHEMA);
      command.execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
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
        var command = connection.createStatement()) {
      command.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }
  }

  @BeforeEach
  void prepare() {
    jdbc.execute("TRUNCATE users,rooms,timetable_scopes,timetable_faculty CASCADE");
    jdbc.update(
        "INSERT INTO users(id,full_name,email,password_hash,role) VALUES"
            + " ('admin','Administrator','admin@test.invalid','unused','ADMIN')");
    jdbc.update(
        "INSERT INTO rooms(id,code,kind,building,capacity) VALUES"
            + " ('room','017','CLASSROOM','Main',40)");
    service.addFaculty("F001", "Faculty", admin);
  }

  private ImportData.Session session(String start, String end) {
    return new ImportData.Session(
        "session",
        0,
        start,
        end,
        "room",
        "CS301",
        List.of("F001"),
        Map.of("page", 1, "text", "source cell"));
  }

  private String draft(String scope, LocalDate from, LocalDate until) throws Exception {
    try (var document = TimetableAdaptersTest.template()) {
      return service
          .upload(
              admin,
              "template.docx",
              TimetableAdaptersTest.bytes(document),
              scope,
              from,
              until,
              List.of())
          .get("id")
          .toString();
    }
  }

  private String reviewed(String scope, LocalDate from, LocalDate until, String start, String end)
      throws Exception {
    String id = draft(scope, from, until);
    service.review(
        id,
        new TimetableService.Review(0, List.of(session(start, end)), List.of("room"), true),
        admin);
    return id;
  }

  private Cookie sessionFor(String role, String campus) {
    String userId = UUID.randomUUID().toString();
    String token = UUID.randomUUID().toString();
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
        UUID.randomUUID().toString(),
        OffsetDateTime.now().plusHours(1));
    return new Cookie(AuthService.COOKIE, token);
  }

  @Test
  void teacherLookupIsRestrictedToStaffAndCampusWhileRoomSchedulesStayPublic() throws Exception {
    String revision =
        reviewed("CSE-V", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "09:00", "10:00");
    service.publish(revision, 1, admin);
    String teachers = "/api/timetables/teachers";
    String schedule = teachers + "/F001/schedule";
    mvc.perform(get(teachers)).andExpect(status().isUnauthorized());
    mvc.perform(get(schedule).param("date", "2026-10-05")).andExpect(status().isUnauthorized());
    for (String role : List.of("STUDENT", "CLUB_HEAD")) {
      var cookie = sessionFor(role, "gehu-hill-dehradun");
      mvc.perform(get(teachers).cookie(cookie)).andExpect(status().isForbidden());
      mvc.perform(get(schedule).param("date", "2026-10-05").cookie(cookie))
          .andExpect(status().isForbidden());
    }
    for (String role : List.of("FACULTY", "COORDINATOR", "HOD", "ADMIN")) {
      var cookie = sessionFor(role, "gehu-hill-dehradun");
      mvc.perform(get(teachers).cookie(cookie))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value("F001"));
      mvc.perform(get(schedule).param("date", "2026-10-05").cookie(cookie))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sessions[0].subject").value("CS301"));
    }
    var otherCampus = sessionFor("FACULTY", "other-campus");
    mvc.perform(get(teachers).cookie(otherCampus))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mvc.perform(get(schedule).param("date", "2026-10-05").cookie(otherCampus))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/rooms/room/schedule").param("date", "2026-10-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessions[0].title").value("CLASS"))
        .andExpect(jsonPath("$.sessions[0].subject").doesNotExist())
        .andExpect(jsonPath("$.sessions[0].faculty_ids").doesNotExist())
        .andExpect(jsonPath("$.sessions[0].requester_name").doesNotExist());
  }

  @Test
  void draftIssuesAndStaleEditsCannotPublishAndSourceIsRetained() throws Exception {
    var from = LocalDate.of(2026, 7, 1);
    var until = LocalDate.of(2026, 12, 31);
    String id = draft("CSE-V", from, until);
    assertThatThrownBy(() -> service.publish(id, 0, admin)).isInstanceOf(ApiException.class);
    var payload =
        new TimetableService.Review(0, List.of(session("09:00", "10:00")), List.of("room"), true);
    service.review(id, payload, admin);
    assertThatThrownBy(() -> service.review(id, payload, admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("changed");
    assertThatThrownBy(() -> service.publish(id, 0, admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("changed");
    service.publish(id, 1, admin);
    assertThat(service.source(id, admin).content()).isNotEmpty();
    assertThat(service.teacherSchedule("F001", LocalDate.of(2026, 10, 5), admin).get("sessions"))
        .asList()
        .hasSize(1);
  }

  @Test
  void replacementPreservesUnrelatedScopeHistoricalDatesAndFutureTail() throws Exception {
    var from = LocalDate.of(2026, 7, 1);
    var until = LocalDate.of(2026, 12, 31);
    String original = reviewed("CSE-V", from, until, "09:00", "10:00");
    service.publish(original, 1, admin);
    String unrelated = reviewed("CSE-III", from, until, "11:00", "12:00");
    service.publish(unrelated, 1, admin);
    String next =
        reviewed("CSE-V", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), "10:00", "11:00");
    service.publish(next, 1, admin);
    assertThat(service.busy("room", LocalDate.of(2026, 10, 5)))
        .extracting(value -> value.startTime())
        .containsExactlyInAnyOrder("10:00", "11:00");
    assertThat(service.busy("room", LocalDate.of(2026, 11, 2)))
        .extracting(value -> value.startTime())
        .containsExactlyInAnyOrder("09:00", "11:00");
    assertThat(service.busy("room", LocalDate.of(2026, 9, 28)))
        .extracting(value -> value.startTime())
        .containsExactlyInAnyOrder("09:00", "11:00");
    assertThat(service.covered("room", LocalDate.of(2026, 10, 5))).isTrue();
    assertThat(service.covered("missing", LocalDate.of(2026, 10, 5))).isFalse();
  }

  @Test
  void publicationCannotOverrideApprovedBookingAndRejectedPublishIsAtomic() throws Exception {
    String id =
        reviewed("CSE-V", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "09:00", "10:00");
    jdbc.update(
        "INSERT INTO occupancies(id,room_id,starts_at,ends_at,kind,source_id) VALUES"
            + " ('booking','room',?,?,'BOOKING','booking')",
        OffsetDateTime.parse("2026-10-05T09:30:00+05:30"),
        OffsetDateTime.parse("2026-10-05T10:30:00+05:30"));
    assertThatThrownBy(() -> service.publish(id, 1, admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("approved booking");
    assertThat(service.detail(id, admin).get("status")).isEqualTo("REVIEWED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_publications", Integer.class))
        .isZero();
  }

  @Test
  void concurrentPublicationOfConflictingScopesHasOneWinner() throws Exception {
    var from = LocalDate.of(2026, 7, 1);
    var until = LocalDate.of(2026, 12, 31);
    String a = reviewed("CSE-V", from, until, "09:00", "10:00"),
        b = reviewed("CSE-III", from, until, "09:30", "10:30");
    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Callable<Boolean>> jobs = List.of(() -> attemptPublish(a), () -> attemptPublish(b));
      var results = executor.invokeAll(jobs);
      assertThat(
              results.stream()
                  .filter(
                      result -> {
                        try {
                          return result.get();
                        } catch (Exception error) {
                          throw new RuntimeException(error);
                        }
                      })
                  .count())
          .isEqualTo(1);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_publications", Integer.class))
        .isEqualTo(1);
  }

  private boolean attemptPublish(String id) {
    try {
      service.publish(id, 1, admin);
      return true;
    } catch (ApiException error) {
      return false;
    }
  }
}
