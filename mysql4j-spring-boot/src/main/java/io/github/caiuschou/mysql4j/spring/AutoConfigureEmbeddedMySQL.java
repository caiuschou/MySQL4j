package io.github.caiuschou.mysql4j.spring;

import io.github.caiuschou.mysql4j.MySQLConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts an embedded MySQL (once per JVM and distinct settings, stopped at JVM exit) and sets
 * {@code spring.datasource.url/username/password} of the test's application context to point at it.
 * These values take precedence over application.properties.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoConfigureEmbeddedMySQL {

    String version() default MySQLConfig.DEFAULT_VERSION;

    /** Database created at startup and used in the JDBC url; empty for none. */
    String database() default "test";

    /** Extra mysqld options, e.g. {@code --lower-case-table-names=1}. */
    String[] extraArgs() default {};
}
