package io.mysql4j.spring;

import io.mysql4j.MySQL;
import io.mysql4j.MySQLConfig;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

/** equals/hashCode matter: Spring uses them as part of the context cache key. */
final class EmbeddedMySQLContextCustomizer implements ContextCustomizer {

    /** One server per distinct settings for the whole JVM, so cached contexts and several test classes share it. */
    private static final Map<String, MySQL> INSTANCES = new ConcurrentHashMap<>();

    private final String version;
    private final String database;
    private final String[] extraArgs;

    EmbeddedMySQLContextCustomizer(String version, String database, String[] extraArgs) {
        this.version = version;
        this.database = database;
        this.extraArgs = extraArgs.clone();
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        MySQL db = INSTANCES.computeIfAbsent(version + "|" + database + "|" + Arrays.toString(extraArgs), k -> start());
        TestPropertyValues.of(
                "spring.datasource.url=" + db.jdbcUrl(),
                "spring.datasource.username=" + db.username(),
                "spring.datasource.password=" + db.password())
                .applyTo(context);
    }

    private MySQL start() {
        MySQLConfig.Builder b = MySQLConfig.builder()
                .version(version)
                .databaseName(database.isEmpty() ? null : database);
        for (String arg : extraArgs) {
            b.extraArg(arg);
        }
        MySQL db = MySQL.start(b.build());
        Runtime.getRuntime().addShutdownHook(new Thread(db::close, "mysql4j-shutdown"));
        return db;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmbeddedMySQLContextCustomizer)) {
            return false;
        }
        EmbeddedMySQLContextCustomizer other = (EmbeddedMySQLContextCustomizer) o;
        return version.equals(other.version) && database.equals(other.database)
                && Arrays.equals(extraArgs, other.extraArgs);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * version.hashCode() + database.hashCode()) + Arrays.hashCode(extraArgs);
    }
}
