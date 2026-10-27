package io.github.caiuschou.mysql4j.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.caiuschou.mysql4j.MySQL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

@EmbeddedMySQL(database = "shop", extraArgs = "--lower-case-table-names=1")
class AnnotationTest {

    @Test
    void injectsRunningServer(MySQL db) throws Exception {
        assertEquals(db.jdbcUrl(), System.getProperty("mysql4j.jdbc-url"));
        try (Connection c = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT DATABASE(), @@lower_case_table_names, VERSION()")) {
            assertTrue(rs.next());
            assertEquals("shop", rs.getString(1));
            assertEquals(1, rs.getInt(2));
            assertEquals("8.0.25", rs.getString(3));
        }
    }
}
