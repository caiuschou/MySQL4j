package io.mysql4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class MySQLTest {

    @Test
    void startsMySQL8025AndRunsQueries() throws Exception {
        try (MySQL db = MySQL.start();
                Connection c = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
                Statement st = c.createStatement()) {

            try (ResultSet rs = st.executeQuery("SELECT VERSION()")) {
                assertTrue(rs.next());
                assertEquals("8.0.25", rs.getString(1));
            }

            st.execute("CREATE TABLE t (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(32) NOT NULL) ENGINE=InnoDB");
            st.execute("INSERT INTO t (name) VALUES ('中文'), ('b')");
            try (ResultSet rs = st.executeQuery("SELECT name FROM t ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals("中文", rs.getString(1));
            }

            try (ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                assertTrue(rs.next());
                assertEquals("test", rs.getString(1));
            }
        }
    }
}
