package com.vaadin.demo.nordicsupply.config;

import java.util.Map;
import java.util.stream.Collectors;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.vaadin.demo.nordicsupply.session.Scope;
import com.vaadin.demo.nordicsupply.session.Scopes;

/**
 * The database connections the model's queries run on: one read-only account per region, and one per country for the
 * Dashboard country filter, each of which can read only its own schema of filtered views ({@code db/scoped-views.sql},
 * run once per scope by the loader with the scope's countries written into the views). A query therefore returns the
 * rows of the signed-in user's countries, or of the one country they narrowed to, because the database says so, not
 * because the question was phrased that way.
 * <p>
 * An account per scope suits the country filter only because its values are few and fixed and each lies inside a
 * region; it is not a pattern for filtering in general. A date range, or a second filter, needs views that read a
 * value the application sets before each query, or in PostgreSQL a policy reading {@code current_setting()}, and on
 * H2 such a view cannot be cached (see {@code db/scoped-views.sql}). A query can reset such a value, so it may narrow
 * what a user chose to see, but what they may see stays with the account or role.
 * <p>
 * The accounts exist to give the database a user to filter by; the demo's users switch between them freely, and the
 * password is the one the script writes. A deployment on a real database creates the accounts and keeps their
 * credentials with its other secrets, outside the repository.
 */
@Service
public class ScopedConnections {

    /**
     * A model-written query is cut off after this. The row cap bounds what a query returns, but not what a sort
     * over a runaway join holds while it runs, so the time has to be short as well.
     */
    private static final int AI_QUERY_TIMEOUT_SECONDS = 30;

    private static final String SCHEMA_PREFIX = "scoped_";

    private static final String USER_PREFIX = "ai_reader_";

    /** The password the script gives every reader account. */
    private static final String PASSWORD = "reader";

    private static final int POOL_SIZE = 4;

    /**
     * Words H2 reads as keywords that a model writes as column aliases: the chart controller's own examples alias the
     * numeric column {@code value}, and period columns come as {@code year} or {@code month}. On this connection they
     * are names, so the model's first query runs instead of failing on a syntax error.
     */
    private static final String NON_KEYWORDS = "VALUE,YEAR,MONTH,DAY,HOUR,MINUTE,SECOND,KEY";

    /** One pool per scope's key; a pool opens its first connection when a query needs it. */
    private final Map<String, HikariDataSource> pools =
            Scopes.all().stream().collect(Collectors.toMap(Scope::key, ScopedConnections::pool));

    /** The schema of a scope's views, {@code scoped_} and the scope's key. */
    static String schemaFor(Scope scope) {
        return SCHEMA_PREFIX + scope.key();
    }

    /** The account that reads a scope's schema, {@code ai_reader_} and the scope's key. */
    static String userFor(Scope scope) {
        return USER_PREFIX + scope.key();
    }

    /**
     * The connection of a region or of one country. Every query on it sees the countries of that scope and nothing
     * else.
     *
     * @throws IllegalArgumentException when the scope is not one of the four regions or the six countries
     */
    public JdbcTemplate templateFor(Scope scope) {
        var pool = pools.get(scope.key());
        if (pool == null) {
            throw new IllegalArgumentException("not a region or a country of the demo: " + scope.key());
        }
        var template = new JdbcTemplate(pool);
        template.setQueryTimeout(AI_QUERY_TIMEOUT_SECONDS);
        return template;
    }

    @PreDestroy
    void closePools() {
        pools.values().forEach(HikariDataSource::close);
    }

    /**
     * A scope's pool, opened on its schema, with the model's aliases as names, and without the application's
     * keep-alive option, which only an administrator may set.
     */
    private static HikariDataSource pool(Scope scope) {
        var ds = new HikariDataSource();
        ds.setJdbcUrl(DataConfig.DATABASE + ";SCHEMA=" + schemaFor(scope) + ";NON_KEYWORDS=" + NON_KEYWORDS);
        ds.setUsername(userFor(scope));
        ds.setPassword(PASSWORD);
        ds.setPoolName(userFor(scope));
        ds.setMaximumPoolSize(POOL_SIZE);
        return ds;
    }
}
