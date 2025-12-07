package pl.edu.agh.dp.core.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider {
    Connection getConnection();
}
