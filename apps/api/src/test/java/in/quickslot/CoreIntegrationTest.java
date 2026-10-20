package in.quickslot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.quickslot.availability.BusyInterval;
import in.quickslot.availability.TimetableLookup;
import in.quickslot.identity.AuthService;
import jakarta.servlet.http.Cookie;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class CoreIntegrationTest {
  private static final String SCHEMA = "test_core_" + UUID.randomUUID().toString().replace("-", "");
  private static final String URL = System.getenv("TEST_DATABASE_URL");
  private static final String USER =
      System.getenv().getOrDefault("TEST_DATABASE_USER", "quickslot");
  private static final String PASSWORD = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "");
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired AuthService auth;
  @Autowired ScheduleFixture schedule;

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
    registry.add("quickslot.cookie-secure", () -> false);
    registry.add("quickslot.demo-seed", () -> false);
  }

  @AfterAll
  static void removeSchema() throws Exception {
    try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
        var command = connection.createStatement()) {
      command.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }
  }

  @BeforeEach
  void prepare() {
    jdbc.execute("TRUNCATE users,rooms CASCADE");
    jdbc.execute("TRUNCATE login_attempts");
    jdbc.update(
        "UPDATE clubs SET active=true,name='Co-Dev Club',campus_id='gehu-hill-dehradun' WHERE"
            + " id='codev'");
    schedule.complete = false;
    for (String role : List.of("STUDENT", "FACULTY", "CLUB_HEAD", "COORDINATOR", "HOD", "ADMIN")) {
      jdbc.update(
          "INSERT INTO users(id,full_name,email,password_hash,role) VALUES (?,?,?,?,?)",
          role,
          role,
          role.toLowerCase(Locale.ROOT) + "@test.local",
          auth.passwordHash("test-password"),
          role);
    }
    jdbc.update(
        "INSERT INTO rooms(id,code,kind,building,capacity) VALUES ('room','017','CLASSROOM','Test"
            + " block',60),('other','018','CLASSROOM','Test block',60)");
    jdbc.update("INSERT INTO allocations(user_id,room_id) VALUES ('COORDINATOR','room')");
  }

  record Login(Cookie cookie, String csrf) {}

  private Login login(String role) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                role.toLowerCase(Locale.ROOT) + "@test.local",
                                "password",
                                "test-password"))))
            .andExpect(status().isOk())
            .andReturn();
    return new Login(
        result.getResponse().getCookie(AuthService.COOKIE),
        json.readTree(result.getResponse().getContentAsString()).get("csrf_token").asText());
  }

  private Map<String, Object> input(String room) {
    return Map.of(
        "room_id",
        room,
        "date",
        LocalDate.now().plusDays(30).toString(),
        "start_time",
        "10:00",
        "end_time",
        "11:00",
        "purpose",
        "Workshop",
        "attendance",
        20);
  }

  private String submit(Login login, String room) throws Exception {
    var result =
        mvc.perform(
                post("/api/requests")
                    .cookie(login.cookie())
                    .header("X-CSRF-Token", login.csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(input(room))))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void publicSearchIsUnknownAndStudentsCannotBook() throws Exception {
    mvc.perform(
            get("/api/availability")
                .param("date", LocalDate.now().plusDays(30).toString())
                .param("start_time", "10:00")
                .param("end_time", "11:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rooms[0].state").value("UNKNOWN"))
        .andExpect(jsonPath("$.rooms[0].requester_name").doesNotExist());
    var student = login("STUDENT");
    mvc.perform(
            post("/api/requests")
                .cookie(student.cookie())
                .header("X-CSRF-Token", student.csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(input("room"))))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/switch-demo-role")).andExpect(status().isNotFound());
  }

  @Test
  void csrfAndAccountRevocationAreEnforced() throws Exception {
    var faculty = login("FACULTY");
    mvc.perform(
            post("/api/requests")
                .cookie(faculty.cookie())
                .contentType("application/json")
                .content(json.writeValueAsString(input("room"))))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET active=false WHERE id='FACULTY'");
    mvc.perform(get("/api/auth/me").cookie(faculty.cookie())).andExpect(status().isUnauthorized());
  }

  @Test
  void bookingsRejectMinuteOffsetsAndUnverifiedCapacity() throws Exception {
    var faculty = login("FACULTY");
    var payload = new LinkedHashMap<>(input("room"));
    payload.put("start_time", "10:15");
    mvc.perform(
            post("/api/requests")
                .cookie(faculty.cookie())
                .header("X-CSRF-Token", faculty.csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(payload)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value("Bookings must start and end on the hour."));
    String requestId = submit(faculty, "room");
    jdbc.update("UPDATE rooms SET capacity=NULL WHERE id='room'");
    schedule.complete = true;
    var coordinator = login("COORDINATOR");
    mvc.perform(
            post("/api/requests/" + requestId + "/approve")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Room capacity must be verified before approval."));
  }

  @Test
  void forwardingRequiresHodAndCoordinatorScope() throws Exception {
    String id = submit(login("FACULTY"), "room");
    var coordinator = login("COORDINATOR");
    mvc.perform(
            post("/api/requests/" + id + "/forward")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf())
                .contentType("application/json")
                .content("{\"reason\":\"HOD review required\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FORWARDED"));
    mvc.perform(
            post("/api/requests/" + id + "/reject")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf())
                .contentType("application/json")
                .content("{\"reason\":\"No\"}"))
        .andExpect(status().isForbidden());
    var hod = login("HOD");
    mvc.perform(
            post("/api/requests/" + id + "/reject")
                .cookie(hod.cookie())
                .header("X-CSRF-Token", hod.csrf())
                .contentType("application/json")
                .content("{\"reason\":\"No\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.history[2].action").value("REJECTED"));
    String other = submit(login("CLUB_HEAD"), "other");
    mvc.perform(
            post("/api/requests/" + other + "/reject")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf())
                .contentType("application/json")
                .content("{\"reason\":\"No\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unknownApprovalIsBlockedAndApprovedBookingCanBeCancelled() throws Exception {
    var faculty = login("FACULTY");
    String id = submit(faculty, "room");
    var coordinator = login("COORDINATOR");
    mvc.perform(
            post("/api/requests/" + id + "/approve")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf()))
        .andExpect(status().isConflict());
    schedule.complete = true;
    mvc.perform(
            post("/api/requests/" + id + "/approve")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf())
                .header("Idempotency-Key", "approval"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
    mvc.perform(
            post("/api/requests/" + id + "/approve")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf())
                .header("Idempotency-Key", "approval"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/requests/" + id + "/cancel")
                .cookie(faculty.cookie())
                .header("X-CSRF-Token", faculty.csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Postponed\"}"))
        .andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM occupancies WHERE active", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class))
        .isGreaterThan(0);
  }

  @Test
  void databaseExclusionRejectsConcurrentOverlapButAllowsAdjacent() throws Exception {
    var barrier = new CyclicBarrier(2);
    var pool = Executors.newFixedThreadPool(2);
    var start = OffsetDateTime.now().plusDays(20);
    Callable<String> reserve =
        () -> {
          try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            connection.setAutoCommit(false);
            try (var command = connection.createStatement()) {
              command.execute("SET search_path TO " + SCHEMA + ",public");
            }
            barrier.await(10, TimeUnit.SECONDS);
            try (var insert =
                connection.prepareStatement(
                    "INSERT INTO occupancies(id,room_id,starts_at,ends_at,kind,source_id) VALUES"
                        + " (?,'room',?,?,'BOOKING',?)")) {
              insert.setString(1, UUID.randomUUID().toString());
              insert.setObject(2, start);
              insert.setObject(3, start.plusHours(1));
              insert.setString(4, UUID.randomUUID().toString());
              insert.executeUpdate();
              connection.commit();
              return "saved";
            } catch (java.sql.SQLException conflict) {
              connection.rollback();
              // PostgreSQL may choose a deadlock victim when both exclusion checks begin together.
              if (!Set.of("23P01", "40P01").contains(conflict.getSQLState())) throw conflict;
              return "conflict";
            }
          }
        };
    try {
      var results = pool.invokeAll(List.of(reserve, reserve));
      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder("saved", "conflict");
      jdbc.update(
          "INSERT INTO occupancies(id,room_id,starts_at,ends_at,kind,source_id) VALUES"
              + " (?,'room',?,?,'BOOKING',?)",
          UUID.randomUUID().toString(),
          start.plusHours(1),
          start.plusHours(2),
          UUID.randomUUID().toString());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void submitIdempotencyIncludesAttendance() throws Exception {
    var faculty = login("FACULTY");
    var body = json.writeValueAsString(input("room"));
    var first =
        mvc.perform(
                post("/api/requests")
                    .cookie(faculty.cookie())
                    .header("X-CSRF-Token", faculty.csrf())
                    .header("Idempotency-Key", "workshop")
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    var second =
        mvc.perform(
                post("/api/requests")
                    .cookie(faculty.cookie())
                    .header("X-CSRF-Token", faculty.csrf())
                    .header("Idempotency-Key", "workshop")
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    assertThat(json.readTree(first.getResponse().getContentAsString()).get("id"))
        .isEqualTo(json.readTree(second.getResponse().getContentAsString()).get("id"));
    var changed = new HashMap<>(input("room"));
    changed.put("attendance", 25);
    mvc.perform(
            post("/api/requests")
                .cookie(faculty.cookie())
                .header("X-CSRF-Token", faculty.csrf())
                .header("Idempotency-Key", "workshop")
                .contentType("application/json")
                .content(json.writeValueAsString(changed)))
        .andExpect(status().isConflict());
  }

  @Test
  void competingApprovalsProduceOneBooking() throws Exception {
    schedule.complete = true;
    String first = submit(login("FACULTY"), "room");
    String second = submit(login("CLUB_HEAD"), "room");
    var coordinator = login("COORDINATOR");
    var barrier = new CyclicBarrier(2);
    var pool = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Integer>> tasks =
          List.of(first, second).stream()
              .map(
                  id ->
                      (Callable<Integer>)
                          () -> {
                            barrier.await(10, TimeUnit.SECONDS);
                            return mvc.perform(
                                    post("/api/requests/" + id + "/approve")
                                        .cookie(coordinator.cookie())
                                        .header("X-CSRF-Token", coordinator.csrf()))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                          })
              .toList();
      var results = pool.invokeAll(tasks);
      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(200, 409);
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM occupancies WHERE active", Integer.class))
          .isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void onlyAdministratorCanVerifyRoomFloorAndCapacity() throws Exception {
    var details =
        json.writeValueAsString(Map.of("building", "", "capacity", 60, "floor", "First floor"));
    var student = login("STUDENT");
    mvc.perform(
            put("/api/rooms/room/details")
                .cookie(student.cookie())
                .header("X-CSRF-Token", student.csrf())
                .contentType("application/json")
                .content(details))
        .andExpect(status().isForbidden());
    var admin = login("ADMIN");
    mvc.perform(
            put("/api/rooms/room/details")
                .cookie(admin.cookie())
                .header("X-CSRF-Token", admin.csrf())
                .contentType("application/json")
                .content(details))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.floor").value("First floor"));
    mvc.perform(get("/api/rooms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'room')].floor").value("First floor"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action='ROOM_DETAILS_VERIFIED'",
                Integer.class))
        .isEqualTo(1);
  }

  private void verifiedMembership() {
    jdbc.update(
        "INSERT INTO club_memberships(id,club_id,student_id,status) VALUES"
            + " ('membership','codev','STUDENT','VERIFIED')");
  }

  private Map<String, Object> studentClubInput() {
    var payload = new LinkedHashMap<>(input("room"));
    payload.put("club_id", "codev");
    payload.put("reason_category", "HACKATHON");
    return payload;
  }

  private MvcResult studentRequest(Login student, Map<String, Object> payload, int expected)
      throws Exception {
    return mvc.perform(
            post("/api/requests")
                .cookie(student.cookie())
                .header("X-CSRF-Token", student.csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(payload)))
        .andExpect(status().is(expected))
        .andReturn();
  }

  @Test
  void verifiedMembersCanRequestForTheirClubAndStillNeedCoordinatorApproval() throws Exception {
    mvc.perform(get("/api/requests/eligibility")).andExpect(status().isUnauthorized());
    var student = login("STUDENT");
    mvc.perform(get("/api/requests/eligibility").cookie(student.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.can_request").value(false))
        .andExpect(jsonPath("$.club_required").value(true))
        .andExpect(jsonPath("$.clubs").isEmpty());
    studentRequest(student, studentClubInput(), 403);
    jdbc.update(
        "INSERT INTO club_memberships(id,club_id,student_id,status) VALUES"
            + " ('membership','codev','STUDENT','PENDING')");
    studentRequest(student, studentClubInput(), 403);
    jdbc.update("UPDATE club_memberships SET status='VERIFIED' WHERE id='membership'");
    mvc.perform(get("/api/requests/eligibility").cookie(student.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.can_request").value(true))
        .andExpect(jsonPath("$.clubs[0].id").value("codev"));
    studentRequest(student, input("room"), 403);
    var created = studentRequest(student, studentClubInput(), 201);
    var body = json.readTree(created.getResponse().getContentAsString());
    String id = body.get("id").asText();
    assertThat(body.get("status").asText()).isEqualTo("PENDING");
    assertThat(body.get("club_name").asText()).isEqualTo("Co-Dev Club");
    assertThat(body.get("reason_category").asText()).isEqualTo("HACKATHON");
    assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id='STUDENT'", String.class))
        .isEqualTo("STUDENT");
    mvc.perform(
            post("/api/requests/" + id + "/approve")
                .cookie(student.cookie())
                .header("X-CSRF-Token", student.csrf()))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE clubs SET name='Changed club name' WHERE id='codev'");
    mvc.perform(get("/api/requests").cookie(student.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].club_name").value("Co-Dev Club"));
    schedule.complete = true;
    var coordinator = login("COORDINATOR");
    mvc.perform(
            post("/api/requests/" + id + "/approve")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void clubMembershipAndAccountAreCheckedAgainBeforeApproval() throws Exception {
    verifiedMembership();
    var student = login("STUDENT");
    var created = studentRequest(student, studentClubInput(), 201);
    String id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
    schedule.complete = true;
    var coordinator = login("COORDINATOR");
    jdbc.update("UPDATE club_memberships SET status='REJECTED' WHERE id='membership'");
    mvc.perform(
            post("/api/requests/" + id + "/approve")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Verified membership in this club is required."));
    jdbc.update("UPDATE club_memberships SET status='VERIFIED' WHERE id='membership'");
    jdbc.update("UPDATE clubs SET active=false WHERE id='codev'");
    mvc.perform(
            post("/api/requests/" + id + "/approve")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf()))
        .andExpect(status().isConflict());
    jdbc.update("UPDATE clubs SET active=true WHERE id='codev'");
    jdbc.update("UPDATE users SET active=false WHERE id='STUDENT'");
    mvc.perform(
            post("/api/requests/" + id + "/approve")
                .cookie(coordinator.cookie())
                .header("X-CSRF-Token", coordinator.csrf()))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject("SELECT status FROM booking_requests WHERE id=?", String.class, id))
        .isEqualTo("PENDING");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM occupancies", Integer.class)).isZero();
  }

  @Test
  void clubRequestsStayInsideVerifiedActiveCampusMembership() throws Exception {
    verifiedMembership();
    var student = login("STUDENT");
    var foreignClub = new LinkedHashMap<>(studentClubInput());
    foreignClub.put("club_id", "not-my-club");
    studentRequest(student, foreignClub, 403);
    jdbc.update("UPDATE clubs SET active=false WHERE id='codev'");
    studentRequest(student, studentClubInput(), 403);
    mvc.perform(get("/api/requests/eligibility").cookie(student.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.can_request").value(false));
    jdbc.update("UPDATE clubs SET active=true,campus_id='other-campus' WHERE id='codev'");
    studentRequest(student, studentClubInput(), 403);
    mvc.perform(get("/api/requests/eligibility").cookie(student.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clubs").isEmpty());
  }

  @Test
  void facultyReasonsAreValidatedAndLegacyRequestsDefaultToOther() throws Exception {
    var faculty = login("FACULTY");
    mvc.perform(get("/api/requests/eligibility").cookie(faculty.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.can_request").value(true))
        .andExpect(jsonPath("$.club_required").value(false));
    String id = submit(faculty, "room");
    assertThat(
            jdbc.queryForObject(
                "SELECT reason_category FROM booking_requests WHERE id=?", String.class, id))
        .isEqualTo("OTHER");
    var invalid = new LinkedHashMap<>(input("room"));
    invalid.put("reason_category", "INVALID");
    mvc.perform(
            post("/api/requests")
                .cookie(faculty.cookie())
                .header("X-CSRF-Token", faculty.csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(invalid)))
        .andExpect(status().isUnprocessableEntity());
    var extraClass = new LinkedHashMap<>(input("room"));
    extraClass.put("reason_category", "EXTRA_CLASS");
    mvc.perform(
            post("/api/requests")
                .cookie(faculty.cookie())
                .header("X-CSRF-Token", faculty.csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(extraClass)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reason_category").value("EXTRA_CLASS"));
  }

  @TestConfiguration
  static class FixtureConfiguration {
    @Bean
    @Primary
    ScheduleFixture timetableFixture() {
      return new ScheduleFixture();
    }
  }

  static class ScheduleFixture implements TimetableLookup {
    volatile boolean complete;

    public List<BusyInterval> busy(String roomId, LocalDate date) {
      return List.of();
    }

    public boolean covered(String roomId, LocalDate date) {
      return complete;
    }
  }
}
