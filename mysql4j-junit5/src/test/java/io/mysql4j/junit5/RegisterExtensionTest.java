package io.mysql4j.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mysql4j.MySQLConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RegisterExtensionTest {

    @RegisterExtension
    static MySQLExtension mysql = new MySQLExtension(MySQLConfig.builder().databaseName("orders").build());

    @Test
    void usesConfiguredDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(mysql.jdbcUrl(), "root", "");
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            assertTrue(rs.next());
            assertEquals("orders", rs.getString(1));
        }
    }
}
