package in.quickslot.clubs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.quickslot.identity.AuthService;
import jakarta.servlet.http.Cookie;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class ClubIntegrationTest {
  private static final String SCHEMA =
      "test_clubs_" + UUID.randomUUID().toString().replace("-", "");
  private static final String URL = System.getenv("TEST_DATABASE_URL");
  private static final String USER =
      System.getenv().getOrDefault("TEST_DATABASE_USER", "quickslot");
  private static final String PASSWORD = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "");
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired AuthService auth;

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
  void accounts() {
    jdbc.execute("TRUNCATE users,rooms CASCADE");
    jdbc.execute("TRUNCATE login_attempts");
    for (String role : List.of("STUDENT", "CLUB_HEAD", "ADMIN", "FACULTY")) {
      jdbc.update(
          "INSERT INTO users(id,full_name,email,password_hash,role) VALUES (?,?,?,?,?)",
          role,
          role,
          role.toLowerCase() + "@test.local",
          auth.passwordHash("test-password"),
          role);
    }
    jdbc.update(
        "INSERT INTO users(id,full_name,email,password_hash,role,campus_id) VALUES"
            + " ('OTHER_ADMIN','Other admin','other_admin@test.local',?,'ADMIN','other-campus')",
        auth.passwordHash("test-password"));
    jdbc.update("INSERT INTO club_managers(club_id,user_id) VALUES ('codev','CLUB_HEAD')");
  }

  record Login(Cookie cookie, String csrf) {}

  private Login login(String role) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                role.toLowerCase() + "@test.local",
                                "password",
                                "test-password"))))
            .andExpect(status().isOk())
            .andReturn();
    return new Login(
        result.getResponse().getCookie(AuthService.COOKIE),
        json.readTree(result.getResponse().getContentAsString()).get("csrf_token").asText());
  }

  private String join(Login student) throws Exception {
    var response =
        mvc.perform(
                post("/api/clubs/codev/join")
                    .cookie(student.cookie())
                    .header("X-CSRF-Token", student.csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();
    return json.readTree(response.getResponse().getContentAsString()).get("id").asText();
  }

  private void decide(Login reviewer, String id, String action, String reason, int expected)
      throws Exception {
    mvc.perform(
            post("/api/clubs/codev/memberships/" + id + "/" + action)
                .cookie(reviewer.cookie())
                .header("X-CSRF-Token", reviewer.csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("reason", reason))))
        .andExpect(status().is(expected));
  }

  @Test
  void profilesUseProvidedLeadershipAndMembershipDataRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/clubs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Co-Dev Club"));
    mvc.perform(get("/api/clubs/codev"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("codev@gehu.ac.in"))
        .andExpect(jsonPath("$.leadership[0].name").value("Priyanshu Negi"))
        .andExpect(jsonPath("$.leadership[0].role").value("President"))
        .andExpect(jsonPath("$.leadership.length()").value(16))
        .andExpect(jsonPath("$.student_id").doesNotExist());
    mvc.perform(get("/api/clubs/codev/membership")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/clubs/codev/memberships")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/clubs/codev/join")).andExpect(status().isUnauthorized());
    var student = login("STUDENT");
    mvc.perform(post("/api/clubs/codev/join").cookie(student.cookie()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/clubs/codev/membership").cookie(student.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NONE"))
        .andExpect(jsonPath("$.can_review").value(false));
  }

  @Test
  void rejectionCanBeRetriedAndVerificationDoesNotGrantBookingPermission() throws Exception {
    var student = login("STUDENT");
    var head = login("CLUB_HEAD");
    String id = join(student);
    assertThat(join(student)).isEqualTo(id);
    decide(head, id, "reject", "", 422);
    decide(head, id, "reject", "Please confirm your student details", 200);
    assertThat(join(student)).isEqualTo(id);
    decide(head, id, "approve", "", 200);
    decide(head, id, "approve", "", 200);
    mvc.perform(get("/api/clubs/codev/membership").cookie(student.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFIED"));
    mvc.perform(
            post("/api/clubs/codev/join")
                .cookie(student.cookie())
                .header("X-CSRF-Token", student.csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFIED"));
    mvc.perform(
            post("/api/requests")
                .cookie(student.cookie())
                .header("X-CSRF-Token", student.csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "room_id",
                            "missing",
                            "date",
                            LocalDate.now().plusDays(1).toString(),
                            "start_time",
                            "10:00",
                            "end_time",
                            "11:00",
                            "purpose",
                            "Club meeting",
                            "attendance",
                            10))))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id='STUDENT'", String.class))
        .isEqualTo("STUDENT");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE entity_id=?", Integer.class, id))
        .isEqualTo(4);
  }

  @Test
  void reviewRequiresAssignmentAndCampusAndCannotApproveSelf() throws Exception {
    String id = join(login("STUDENT"));
    var head = login("CLUB_HEAD");
    var otherAdmin = login("OTHER_ADMIN");
    decide(otherAdmin, id, "approve", "", 403);
    mvc.perform(get("/api/clubs/codev/memberships").cookie(otherAdmin.cookie()))
        .andExpect(status().isForbidden());
    jdbc.update("DELETE FROM club_managers WHERE user_id='CLUB_HEAD'");
    decide(head, id, "approve", "", 403);
    jdbc.update("INSERT INTO club_managers(club_id,user_id) VALUES ('codev','CLUB_HEAD')");
    var faculty = login("FACULTY");
    mvc.perform(
            post("/api/clubs/codev/join")
                .cookie(faculty.cookie())
                .header("X-CSRF-Token", faculty.csrf()))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE club_memberships SET student_id='ADMIN' WHERE id=?", id);
    decide(login("ADMIN"), id, "approve", "", 403);
    jdbc.update("UPDATE club_memberships SET student_id='STUDENT' WHERE id=?", id);
    decide(login("ADMIN"), id, "approve", "", 200);
    mvc.perform(get("/api/clubs/codev/memberships").cookie(head.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].student_name").value("STUDENT"))
        .andExpect(jsonPath("$[0].status").value("VERIFIED"));
  }

  @Test
  void simultaneousJoinsProduceOnePendingMembershipAndOneAuditEvent() throws Exception {
    var student = login("STUDENT");
    var barrier = new CyclicBarrier(2);
    var pool = Executors.newFixedThreadPool(2);
    Callable<String> submit =
        () -> {
          barrier.await(10, TimeUnit.SECONDS);
          return join(student);
        };
    try {
      var results = pool.invokeAll(List.of(submit, submit));
      assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
      assertThat(jdbc.queryForObject("SELECT count(*) FROM club_memberships", Integer.class))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class))
          .isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }
}
