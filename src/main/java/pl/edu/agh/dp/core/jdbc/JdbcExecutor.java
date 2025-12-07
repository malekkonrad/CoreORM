package pl.edu.agh.dp.core.jdbc;

import java.sql.Connection;
import java.util.List;

public interface JdbcExecutor {
    Connection connection = null;

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);

    public int update(String sql, Object... params);
}
