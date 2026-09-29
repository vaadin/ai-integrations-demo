package com.vaadin.demo.nordicsupply.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.vaadin.demo.nordicsupply.session.Scope;
import com.vaadin.demo.nordicsupply.session.Scopes;

/**
 * The application's connection to the in-memory H2 database, and the load that fills it at start-up: the dataset's
 * schema script, its CSV files through H2's {@code CSVREAD} from the classpath, then the application's own
 * set-up scripts under {@code db/setup}, in file-name order, and last the scoped views, one schema per region from
 * one script with the region's countries filled in. The database lives only as long as the JVM, so every
 * start rebuilds it and nothing is ever migrated: a change to what the application adds is a change to those
 * scripts. The load happens inside the data source's factory method so that every bean that depends on the data
 * source finds the data already there.
 */
@Configuration
public class DataConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DataConfig.class);

    /** The in-memory database, without connection options; the model's connections open it too. */
    static final String DATABASE = "jdbc:h2:mem:aicases";

    /** The application's connection; {@code DB_CLOSE_DELAY=-1} keeps the database alive between connections. */
    private static final String URL = DATABASE + ";DB_CLOSE_DELAY=-1";

    /** Enough connections for one demo session. */
    private static final int POOL_SIZE = 8;

    /** The loader script names the CSV files at this classpath root; the application keeps them in the dataset. */
    private static final String LOADER_CSV_ROOT = "classpath:/data/";

    private static final String CSV_ROOT = "classpath:/data/nordic_supply/csv/";

    /** The application's own scripts, run after the dataset in file-name order. */
    private static final String SETUP_SCRIPTS = "classpath:db/setup/*.sql";

    /** The scoped views and their account, run once per region with the placeholders filled in. */
    private static final String SCOPED_VIEWS = "db/scoped-views.sql";

    @Bean
    public PackData packData() {
        return new PackData();
    }

    @Bean
    public DataSource dataSource(PackData pack) throws IOException, SQLException {
        var ds = new HikariDataSource();
        ds.setJdbcUrl(URL);
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(POOL_SIZE);
        load(ds, pack);
        return ds;
    }

    /** The views script for one region: its schema, its account and its countries as SQL literals. */
    private static String scopedViews(String template, Scope scope) {
        var countries = scope.countries().stream().map(c -> "'" + c + "'").collect(Collectors.joining(", "));
        return template.replace("${schema}", ScopedConnections.schemaFor(scope))
                .replace("${user}", ScopedConnections.userFor(scope))
                .replace("${countries}", countries);
    }

    private static EncodedResource text(String sql) {
        return new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static List<Resource> setupScripts() throws IOException {
        var scripts = new ArrayList<>(List.of(new PathMatchingResourcePatternResolver().getResources(SETUP_SCRIPTS)));
        scripts.sort(Comparator.comparing(Resource::getFilename));
        return scripts;
    }

    private static void load(DataSource ds, PackData pack) throws IOException, SQLException {
        LOG.info("loading the dataset into H2");
        long start = System.currentTimeMillis();
        try (var con = ds.getConnection()) {
            ScriptUtils.executeSqlScript(con, pack.resource("sql/schema-h2.sql"));
            var loader = pack.read("sql/load-h2-classpath.sql").replace(LOADER_CSV_ROOT, CSV_ROOT);
            ScriptUtils.executeSqlScript(con, text(loader));
            for (var script : setupScripts()) {
                ScriptUtils.executeSqlScript(con, new EncodedResource(script, StandardCharsets.UTF_8));
            }
            var views = new ClassPathResource(SCOPED_VIEWS).getContentAsString(StandardCharsets.UTF_8);
            for (var scope : Scopes.roleScopes()) {
                ScriptUtils.executeSqlScript(con, text(scopedViews(views, scope)));
            }
        }
        LOG.info("dataset and set-up scripts loaded into H2 in {} ms", System.currentTimeMillis() - start);
    }
}
