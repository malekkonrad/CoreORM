package pl.edu.agh.dp.core.schema;

import pl.edu.agh.dp.core.jdbc.ConnectionProvider;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
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

    public void dropTables() {
        try (Connection con = cp.getConnection();
             Statement st = con.createStatement()) {

            // FIXME works only for sqlite
            ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table';");
            while (rs.next()) {
                ResultSetMetaData metadata = rs.getMetaData();
                int columnCount = metadata.getColumnCount();
                StringBuilder tableNames = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    tableNames.append(rs.getString(i)).append(",");
                }
                tableNames.deleteCharAt(tableNames.lastIndexOf(","));
                st.executeUpdate("DROP TABLE IF EXISTS " + tableNames + ";");
            }

        } catch (Exception e) {
            throw new RuntimeException("Error dropping tables", e);
        }
    }
}

