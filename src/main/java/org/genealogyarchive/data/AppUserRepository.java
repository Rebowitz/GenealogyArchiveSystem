package org.genealogyarchive.data;

import org.genealogyarchive.access.Role;
import org.genealogyarchive.security.AppUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** app_user rows (V0003). Password hashes are read only for authentication. */
public final class AppUserRepository {

    private static final String COLUMNS =
            "user_id, username, display_name, role, enabled, created_at, updated_at, row_version";

    private static final RowMapper<AppUser> USER = (rs, n) -> new AppUser(
            Rows.uuid(rs, "user_id"), rs.getString("username"), rs.getString("display_name"),
            Role.valueOf(rs.getString("role")), rs.getBoolean("enabled"),
            Rows.instant(rs, "created_at"), Rows.instant(rs, "updated_at"), rs.getLong("row_version"));

    private final JdbcClient jdbc;

    public AppUserRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** User plus stored password hash, for authentication only. */
    public record Credentials(AppUser user, String passwordHash) {
    }

    public void insert(AppUser user, String passwordHash) {
        jdbc.sql("""
                INSERT INTO gas.app_user (user_id, username, display_name, role, password_hash, enabled, created_at, updated_at)
                VALUES (:id, :username, :displayName, :role, :hash, :enabled, :created, :created)
                """)
                .param("id", user.userId()).param("username", user.username())
                .param("displayName", user.displayName()).param("role", user.role().name())
                .param("hash", passwordHash).param("enabled", user.enabled())
                .param("created", Rows.param(user.createdAt()))
                .update();
    }

    public Optional<AppUser> findById(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.app_user WHERE user_id = :id")
                .param("id", userId).query(USER).optional();
    }

    public Optional<Credentials> findCredentialsByUsername(String username) {
        return jdbc.sql("SELECT " + COLUMNS + ", password_hash FROM gas.app_user WHERE lower(username) = lower(:u)")
                .param("u", username)
                .query((rs, n) -> new Credentials(USER.mapRow(rs, n), rs.getString("password_hash")))
                .optional();
    }

    public Optional<String> passwordHash(UUID userId) {
        return jdbc.sql("SELECT password_hash FROM gas.app_user WHERE user_id = :id")
                .param("id", userId).query(String.class).optional();
    }

    public boolean usernameTaken(String username) {
        return jdbc.sql("SELECT count(*) FROM gas.app_user WHERE lower(username) = lower(:u)")
                .param("u", username).query(Long.class).single() > 0;
    }

    public List<AppUser> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.app_user ORDER BY lower(username)").query(USER).list();
    }

    /** Locks every enabled administrator row; used to keep at least one enabled administrator. */
    public List<UUID> lockEnabledAdministrators() {
        return jdbc.sql("SELECT user_id FROM gas.app_user WHERE role = 'ADMINISTRATOR' AND enabled ORDER BY user_id FOR UPDATE")
                .query(UUID.class).list();
    }

    public long countUsers() {
        return jdbc.sql("SELECT count(*) FROM gas.app_user").query(Long.class).single();
    }

    /** @return true if the row had {@code expectedVersion} and was updated */
    public boolean update(UUID userId, long expectedVersion, String displayName, Role role, boolean enabled, Instant now) {
        return jdbc.sql("""
                UPDATE gas.app_user
                   SET display_name = :displayName, role = :role, enabled = :enabled,
                       updated_at = :now, row_version = row_version + 1
                 WHERE user_id = :id AND row_version = :expected
                """)
                .param("displayName", displayName).param("role", role.name()).param("enabled", enabled)
                .param("now", Rows.param(now)).param("id", userId).param("expected", expectedVersion)
                .update() == 1;
    }

    /** @return true if the row had {@code expectedVersion} and was updated */
    public boolean updatePassword(UUID userId, long expectedVersion, String passwordHash, Instant now) {
        return jdbc.sql("""
                UPDATE gas.app_user
                   SET password_hash = :hash, updated_at = :now, row_version = row_version + 1
                 WHERE user_id = :id AND row_version = :expected
                """)
                .param("hash", passwordHash).param("now", Rows.param(now))
                .param("id", userId).param("expected", expectedVersion)
                .update() == 1;
    }
}
