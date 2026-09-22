package com.vaadin.demo.nordicsupply.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * The application's connection to the in-memory H2 database, and the load that fills it at start-up: the dataset's
 * schema script, then its CSV files through H2's {@code CSVREAD} from the classpath. The database lives only as
 * long as the JVM, so every start rebuilds it from the dataset and nothing is ever migrated. The load happens inside
 * the data source's factory method so that every bean that depends on the data source finds the data already there.
 */
@Configuration
public class DataConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DataConfig.class);

    /** The in-memory database; {@code DB_CLOSE_DELAY=-1} keeps it alive between connections. */
    private static final String URL = "jdbc:h2:mem:aicases;DB_CLOSE_DELAY=-1";

    /** Enough connections for one demo session. */
    private static final int POOL_SIZE = 8;

    /** The loader script names the CSV files at this classpath root; the application keeps them in the dataset. */
    private static final String LOADER_CSV_ROOT = "classpath:/data/";

    private static final String CSV_ROOT = "classpath:/data/nordic_supply/csv/";

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

    private static void load(DataSource ds, PackData pack) throws IOException, SQLException {
        LOG.info("loading the dataset into H2");
        long start = System.currentTimeMillis();
        try (var con = ds.getConnection()) {
            ScriptUtils.executeSqlScript(con, pack.resource("sql/schema-h2.sql"));
            var loader = pack.read("sql/load-h2-classpath.sql").replace(LOADER_CSV_ROOT, CSV_ROOT);
            ScriptUtils.executeSqlScript(
                    con,
                    new EncodedResource(
                            new ByteArrayResource(loader.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
        LOG.info("dataset loaded into H2 in {} ms", System.currentTimeMillis() - start);
    }
}
