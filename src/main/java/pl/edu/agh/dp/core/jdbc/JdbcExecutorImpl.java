package pl.edu.agh.dp.core.jdbc;

import pl.edu.agh.dp.core.persister.RowMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcExecutorImpl implements JdbcExecutor {

    private Connection connection;

    public JdbcExecutorImpl(Connection connection) {
        this.connection = connection;
    }


    public void commit() throws SQLException {
        connection.commit();
    }
    public void rollback() throws SQLException {
        connection.rollback();
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(autoCommit);
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }


    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = new ArrayList<>();
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setParameters(ps, params);
            
            try (ResultSet rs = ps.executeQuery()) {
                // mapping
                while (rs.next()) {
                    T mapped = mapper.mapRow(rs);
                    results.add(mapped);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
        
        return results;
    }

    @Override
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setParameters(ps, params);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapper.mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("QueryOne failed: " + sql, e);
        }
    }

    @Override
    public int update(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setParameters(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed: " + sql, e);
        }
    }

    @Override
    public Long insert(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParameters(ps, params);
            ps.executeUpdate();
            
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("No generated key returned");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Insert failed: " + sql, e);
        }
    }

    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

}
