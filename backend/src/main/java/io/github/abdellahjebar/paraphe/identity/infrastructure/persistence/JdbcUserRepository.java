package io.github.abdellahjebar.paraphe.identity.infrastructure.persistence;

import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.identity.domain.UserId;
import io.github.abdellahjebar.paraphe.identity.port.UserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS =
            "id, email, display_name, password_hash, active, must_change_password, created_at";

    private final JdbcClient jdbc;

    JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AppUser user) {
        jdbc.sql("""
                INSERT INTO app_user (id, email, display_name, password_hash, active, must_change_password, created_at)
                VALUES (:id, :email, :name, :hash, :active, :mustChange, :createdAt)
                """)
                .param("id", user.id().value())
                .param("email", user.email())
                .param("name", user.displayName())
                .param("hash", user.passwordHash())
                .param("active", user.active())
                .param("mustChange", user.mustChangePassword())
                .param("createdAt", OffsetDateTime.ofInstant(user.createdAt(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public Optional<AppUser> findByEmail(String normalizedEmail) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE email = :email")
                .param("email", normalizedEmail)
                .query((rs, n) -> toUser(rs))
                .optional();
    }

    @Override
    public Optional<AppUser> findById(UserId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id")
                .param("id", id.value())
                .query((rs, n) -> toUser(rs))
                .optional();
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT count(*) FROM app_user").query(Long.class).single();
    }

    @Override
    public void updatePassword(UserId id, String passwordHash, boolean mustChangePassword) {
        jdbc.sql("UPDATE app_user SET password_hash = :hash, must_change_password = :must WHERE id = :id")
                .param("hash", passwordHash)
                .param("must", mustChangePassword)
                .param("id", id.value())
                .update();
    }

    @Override
    public void recordLogin(UserId id, Instant at) {
        jdbc.sql("UPDATE app_user SET last_login_at = :at WHERE id = :id")
                .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                .param("id", id.value())
                .update();
    }

    private static AppUser toUser(ResultSet rs) throws SQLException {
        return new AppUser(
                new UserId(rs.getObject("id", UUID.class)),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getBoolean("active"),
                rs.getBoolean("must_change_password"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
