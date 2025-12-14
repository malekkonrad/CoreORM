package pl.edu.agh.dp.core.jdbc;

import pl.edu.agh.dp.core.persister.RowMapper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

public interface JdbcExecutor {

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);

    <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params);

    int update(String sql, Object... params);

    Long insert(String sql, Object... params);
}
