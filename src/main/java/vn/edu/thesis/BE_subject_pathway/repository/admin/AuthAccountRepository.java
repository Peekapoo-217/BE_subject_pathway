package vn.edu.thesis.BE_subject_pathway.repository.admin;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AuthAccountRepository {
    private final JdbcTemplate jdbc;

    public AuthAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void register(String id, String username, String hash) {
        jdbc.update("INSERT INTO admin.app_users(id, username, password_hash, role) VALUES (?::uuid, ?, ?, 'USER')", id, username, hash);
    }

    public List<String[]> findActiveByUsername(String username) {
        return jdbc.query("SELECT id::text, username, password_hash, role, token_version FROM admin.app_users WHERE username = ? AND active",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), Integer.toString(rs.getInt(5))}, username);
    }

    public List<String[]> findActiveForRefresh(String id) {
        return jdbc.query("SELECT username,role,token_version FROM admin.app_users WHERE id=?::uuid AND active",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2), Integer.toString(rs.getInt(3))}, id);
    }

    public Map<String, Object> currentUser(String id) {
        return jdbc.queryForMap("SELECT id::text AS id, username, role, created_at AS \"createdAt\" FROM admin.app_users WHERE id=?::uuid AND active", id);
    }

    public boolean isCurrentAccount(String id, String role, int version) {
        var accounts = jdbc.query("SELECT role,token_version FROM admin.app_users WHERE id=?::uuid AND active=true",
                (rs, row) -> new String[]{rs.getString(1), Integer.toString(rs.getInt(2))}, id);
        return !accounts.isEmpty() && accounts.get(0)[0].equals(role)
                && Integer.parseInt(accounts.get(0)[1]) == version;
    }
}
