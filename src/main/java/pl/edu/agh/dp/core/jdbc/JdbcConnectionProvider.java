package pl.edu.agh.dp.core.jdbc;

import lombok.AllArgsConstructor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@AllArgsConstructor
public class JdbcConnectionProvider implements ConnectionProvider {
    String url, user, password;


    @Override
    public Connection getConnection() {
        try{
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }
}
