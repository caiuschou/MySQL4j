package io.mysql4j.junit5;

import io.mysql4j.MySQLConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Starts an embedded MySQL before the annotated test class and stops it afterwards.
 * Inject the {@link io.mysql4j.MySQL} as a test method/constructor parameter, or read the
 * {@code mysql4j.jdbc-url}, {@code mysql4j.username}, {@code mysql4j.password} and {@code mysql4j.port}
 * system properties.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(MySQLExtension.class)
public @interface EmbeddedMySQL {

    String version() default MySQLConfig.DEFAULT_VERSION;

    /** 0 picks a free port. */
    int port() default 0;

    /** Database created at startup; empty for none. */
    String database() default "test";

    /** Extra mysqld options, e.g. {@code --lower-case-table-names=1}. */
    String[] extraArgs() default {};

    /** Reuse one server for all test classes in the JVM that ask for the same settings. */
    boolean shared() default false;
}
