package in.quickslot.identity;

import in.quickslot.shared.ApiException;
import in.quickslot.shared.AuthenticatedUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  public static final String COOKIE = "quickslot_session";
  private final JdbcTemplate jdbc;
  private final boolean secure;
  private final SecureRandom random = new SecureRandom();
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

  public AuthService(JdbcTemplate jdbc, @Value("${quickslot.cookie-secure:true}") boolean secure) {
    this.jdbc = jdbc;
    this.secure = secure;
  }

  public record LoginResult(AuthenticatedUser user, String csrfToken, String cookie) {}

  public static AuthenticatedUser user(ResultSet row, int index) throws SQLException {
    return new AuthenticatedUser(
        row.getString("id"),
        row.getString("full_name"),
        row.getString("email"),
        row.getString("role"),
        row.getString("campus_id"),
        row.getString("department"));
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private String token() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String passwordHash(String value) {
    return passwords.encode(value);
  }

  private String cookieValue(HttpServletRequest request) {
    if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if (COOKIE.equals(cookie.getName())) return cookie.getValue();
      }
    }
    return "";
  }

  public AuthenticatedUser require(HttpServletRequest request) {
    if (request.getAttribute("authenticated_user") instanceof AuthenticatedUser user) return user;
    String token = cookieValue(request);
    var sessions =
        jdbc.queryForList(
            "SELECT s.csrf_token,u.* FROM login_sessions s JOIN users u ON u.id=s.user_id WHERE"
                + " s.token_hash=? AND s.expires_at>now() AND u.active",
            hash(token));
    if (token.isEmpty() || sessions.isEmpty()) throw new ApiException(401, "Please sign in.");
    var row = sessions.getFirst();
    if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
      String csrf = request.getHeader("X-CSRF-Token");
      if (csrf == null
          || !MessageDigest.isEqual(
              csrf.getBytes(StandardCharsets.UTF_8),
              row.get("csrf_token").toString().getBytes(StandardCharsets.UTF_8))) {
        throw new ApiException(403, "Invalid CSRF token.");
      }
    }
    request.setAttribute("csrf_token", row.get("csrf_token"));
    var user =
        new AuthenticatedUser(
            row.get("id").toString(),
            row.get("full_name").toString(),
            row.get("email").toString(),
            row.get("role").toString(),
            row.get("campus_id").toString(),
            row.get("department").toString());
    request.setAttribute("authenticated_user", user);
    return user;
  }

  public AuthenticatedUser admin(HttpServletRequest request) {
    var user = require(request);
    if (!user.role().equals("ADMIN"))
      throw new ApiException(403, "Administrator permission required.");
    return user;
  }

  public LoginResult login(String email, String password, HttpServletRequest request) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiException(422, "Password exceeds the supported byte length.");
    String normalized = email.strip().toLowerCase(java.util.Locale.ROOT);
    String key = hash(request.getRemoteAddr() + ":" + normalized);
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM login_attempts WHERE key=? AND created_at>now()-interval '15"
                + " minutes'",
            Integer.class,
            key);
    if (count != null && count >= 10)
      throw new ApiException(429, "Too many attempts. Try again in 15 minutes.");
    var rows = jdbc.queryForList("SELECT * FROM users WHERE email=?", normalized);
    if (rows.isEmpty()
        || !Boolean.TRUE.equals(rows.getFirst().get("active"))
        || !passwords.matches(password, rows.getFirst().get("password_hash").toString())) {
      jdbc.update(
          "INSERT INTO login_attempts(id,key) VALUES (?,?)", UUID.randomUUID().toString(), key);
      throw new ApiException(401, "Invalid email or password.");
    }
    var user =
        jdbc.queryForObject("SELECT * FROM users WHERE email=?", AuthService::user, normalized);
    String token = token();
    String csrf = token();
    jdbc.update("DELETE FROM login_sessions WHERE token_hash=?", hash(cookieValue(request)));
    jdbc.update(
        "INSERT INTO login_sessions(token_hash,user_id,csrf_token,expires_at) VALUES (?,?,?,?)",
        hash(token),
        user.id(),
        csrf,
        OffsetDateTime.now().plusHours(12));
    jdbc.update("DELETE FROM login_attempts WHERE key=?", key);
    return new LoginResult(
        user,
        csrf,
        ResponseCookie.from(COOKIE, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api")
            .maxAge(43200)
            .build()
            .toString());
  }

  public String logout(HttpServletRequest request) {
    require(request);
    jdbc.update("DELETE FROM login_sessions WHERE token_hash=?", hash(cookieValue(request)));
    return ResponseCookie.from(COOKIE, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/api")
        .maxAge(0)
        .build()
        .toString();
  }
}
