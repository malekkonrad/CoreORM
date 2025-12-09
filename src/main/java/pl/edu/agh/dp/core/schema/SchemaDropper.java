package pl.edu.agh.dp.core.schema;

import pl.edu.agh.dp.core.jdbc.ConnectionProvider;

import java.sql.Connection;
import java.sql.Statement;

public class SchemaDropper {

    private final ConnectionProvider cp;

    public SchemaDropper(ConnectionProvider cp) {
        this.cp = cp;
    }

    public void drop() {
        try (Connection con = cp.getConnection();
             Statement st = con.createStatement()) {

            // Hardkor devowy, ale działa:
            st.executeUpdate("DROP SCHEMA public CASCADE;");
            st.executeUpdate("CREATE SCHEMA public;");

        } catch (Exception e) {
            throw new RuntimeException("Error dropping schema", e);
        }
    }
}

