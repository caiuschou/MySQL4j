package io.mysql4j.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfigureEmbeddedMySQL(database = "app")
@SpringBootTest(classes = SpringBootIntegrationTest.App.class)
class SpringBootIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    static class App {
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void dataSourceIsBackedByEmbeddedMySQL() {
        assertEquals("8.0.25", jdbc.queryForObject("SELECT VERSION()", String.class));
        assertEquals("app", jdbc.queryForObject("SELECT DATABASE()", String.class));

        jdbc.execute("CREATE TABLE t (id INT PRIMARY KEY, v VARCHAR(8))");
        jdbc.update("INSERT INTO t VALUES (1, 'x')");
        assertEquals("x", jdbc.queryForObject("SELECT v FROM t WHERE id = 1", String.class));
    }
}
