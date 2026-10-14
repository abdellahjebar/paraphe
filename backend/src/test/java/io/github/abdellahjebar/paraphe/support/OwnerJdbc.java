package io.github.abdellahjebar.paraphe.support;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A connection as the database <em>owner</em>, the way migrations run.
 *
 * <p>The application itself connects as a restricted role that cannot even attempt to change the
 * evidence tables. Tests that prove the database triggers use this connection to get past the
 * privileges and reach the triggers; tests that prove the privileges use the normal connection.
 */
public record OwnerJdbc(JdbcClient client) {
}
