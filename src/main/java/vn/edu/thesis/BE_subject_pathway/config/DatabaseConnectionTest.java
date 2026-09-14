package vn.edu.thesis.BE_subject_pathway.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lightweight startup check: verifies PostgreSQL connectivity and that the
 * `universities` table is readable. Read-only (SELECT ... LIMIT 1), never
 * mutates data. Failures are logged, not thrown, so startup is not blocked.
 *
 * Uses ObjectProvider so the bean can be created even when no JdbcTemplate
 * exists (e.g. test contexts that exclude DataSourceAutoConfiguration);
 * the check is skipped in that case.
 */
@Component
public class DatabaseConnectionTest implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionTest.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DatabaseConnectionTest(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public void run(String... args) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            log.info("[SKIP] No JdbcTemplate bean available - database connection check skipped.");
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT university_code, university_name FROM universities LIMIT 1");
            if (rows.isEmpty()) {
                log.info("[SUCCESS] Connected to DB! But table 'universities' is empty - no record fetched.");
            } else {
                Map<String, Object> row = rows.get(0);
                log.info("[SUCCESS] Connected to DB! Fetched 1 record: {} - {}",
                        row.get("university_code"), row.get("university_name"));
            }
        } catch (Exception ex) {
            log.error("[FAIL] Database connection check failed: {}", ex.getMessage());
            log.debug("[FAIL] Database connection check failed - root cause:", ex);
        }
    }
}