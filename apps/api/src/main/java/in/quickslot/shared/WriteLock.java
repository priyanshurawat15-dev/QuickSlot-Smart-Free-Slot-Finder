package in.quickslot.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class WriteLock {
  private final JdbcTemplate jdbc;

  public WriteLock(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void acquire() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Schedule writes require a database transaction.");
    }
    jdbc.execute("SET LOCAL lock_timeout = '2s'");
    jdbc.execute("SET LOCAL statement_timeout = '8s'");
    jdbc.execute("SELECT pg_advisory_xact_lock(824613)");
  }
}
