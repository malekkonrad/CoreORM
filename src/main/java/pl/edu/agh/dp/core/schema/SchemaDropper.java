package pl.edu.agh.dp.core.schema;

import pl.edu.agh.dp.core.jdbc.ConnectionProvider;

import java.sql.*;

public class SchemaDropper {

    private final ConnectionProvider cp;

    public SchemaDropper(ConnectionProvider cp) {
        this.cp = cp;
    }

    public void drop() {
        try (Connection con = cp.getConnection();
             Statement st = con.createStatement()) {

            String db = getDatabaseProduct(con);

            if (db.contains("sqlite")) {
                dropAllSqlite(con, st);
            } else if (db.contains("postgresql")) {
                dropAllPostgres(st);
            } else {
                throw new UnsupportedOperationException("Unsupported DB: " + db);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error dropping schema", e);
        }
    }

    private String getDatabaseProduct(Connection con) throws SQLException {
        return con.getMetaData().getDatabaseProductName().toLowerCase();
    }

    private void dropAllPostgres(Statement st) throws SQLException {
        st.execute("DROP SCHEMA public CASCADE;");
        st.execute("CREATE SCHEMA public;");
    }


    private void dropAllSqlite(Connection con, Statement st) throws SQLException {
        ResultSet rs = st.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';"
        );
        while (rs.next()) {
            String table = rs.getString("name");
            st.executeUpdate("DROP TABLE IF EXISTS \"" + table + "\";");
        }
    }
}

