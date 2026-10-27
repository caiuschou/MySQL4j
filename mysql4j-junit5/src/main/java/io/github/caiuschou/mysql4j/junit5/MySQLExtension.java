package io.github.caiuschou.mysql4j.junit5;

import io.github.caiuschou.mysql4j.MySQL;
import io.github.caiuschou.mysql4j.MySQLConfig;
import java.util.Arrays;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Starts one MySQL per test class. Use it through {@link EmbeddedMySQL}, or as
 * {@code @RegisterExtension static MySQLExtension mysql = new MySQLExtension();} to configure it in code.
 */
public class MySQLExtension implements BeforeAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MySQLExtension.class);

    private final MySQLConfig explicitConfig;
    private volatile MySQL current;

    public MySQLExtension() {
        this(null);
    }

    public MySQLExtension(MySQLConfig config) {
        this.explicitConfig = config;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        MySQLConfig config = explicitConfig;
        boolean shared = false;
        if (config == null) {
            EmbeddedMySQL ann = AnnotationSupport.findAnnotation(context.getRequiredTestClass(), EmbeddedMySQL.class)
                    .orElse(null);
            if (ann == null) {
                config = MySQLConfig.defaults();
            } else {
                MySQLConfig.Builder b = MySQLConfig.builder()
                        .version(ann.version())
                        .port(ann.port())
                        .databaseName(ann.database().isEmpty() ? null : ann.database());
                for (String arg : ann.extraArgs()) {
                    b.extraArg(arg);
                }
                config = b.build();
                shared = ann.shared();
            }
        }
        final MySQLConfig cfg = config;
        // A resource in the root store is closed when the whole run ends, one in the class store when the class ends.
        ExtensionContext.Store store = shared ? context.getRoot().getStore(NAMESPACE) : context.getStore(NAMESPACE);
        String key = shared ? key(cfg) : "mysql";
        Holder holder = store.getOrComputeIfAbsent(key, k -> new Holder(MySQL.start(cfg)), Holder.class);
        current = holder.db;
        publish(holder.db);
    }

    /** The running server; valid once the test class has started. */
    public MySQL mysql() {
        MySQL db = current;
        if (db == null) {
            throw new IllegalStateException("MySQL is not started yet");
        }
        return db;
    }

    public String jdbcUrl() {
        return mysql().jdbcUrl();
    }

    public int port() {
        return mysql().port();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == MySQL.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return mysql();
    }

    private static void publish(MySQL db) {
        System.setProperty("mysql4j.jdbc-url", db.jdbcUrl());
        System.setProperty("mysql4j.username", db.username());
        System.setProperty("mysql4j.password", db.password());
        System.setProperty("mysql4j.port", String.valueOf(db.port()));
    }

    private static String key(MySQLConfig c) {
        return c.version() + "|" + c.port() + "|" + c.databaseName() + "|" + Arrays.toString(c.extraArgs().toArray());
    }

    private static final class Holder implements ExtensionContext.Store.CloseableResource {
        private final MySQL db;

        Holder(MySQL db) {
            this.db = db;
        }

        @Override
        public void close() {
            db.close();
        }
    }
}
