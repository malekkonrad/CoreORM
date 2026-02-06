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
    public void executeStatement(String sql) {
        if (sql == null) {
            System.err.println("sql is null");
            return;
        }
        try(Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }catch(SQLException e){
            System.out.println("Sth in jdbcExecutorImpl.executeStatement");
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void dropTable(String sql) throws SQLException {

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
            System.out.println(e.getMessage());
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
            int rows = ps.executeUpdate();
            System.out.println("→ Rows affected: " + rows);
            System.out.println("→ SQL: " + sql);
            System.out.println("→ AutoCommit: " + connection.getAutoCommit());
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException("Update failed: " + sql, e);
        }
    }

    @Override
    public Long insert(String sql, Object... params) {
        return insert(sql, "id", params);
    }
    
    @Override
    public Long insert(String sql, String idColumnName, Object... params) {
        boolean doReturnKey = !idColumnName.isBlank();
        // Sprawdź czy to PostgreSQL
        boolean isPostgres = false;
        try {
            isPostgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (SQLException ignored) {}
        
        String sqlToExecute = sql;
        // return generated id if postgres and idColumnName is not empty (id is not complex)
        if (isPostgres && !sql.toLowerCase().contains("returning") && doReturnKey) {
            sqlToExecute = sql + " RETURNING " + idColumnName;
        }
        
        try (PreparedStatement ps = isPostgres
                ? connection.prepareStatement(sqlToExecute)
                : doReturnKey ? connection.prepareStatement(sql, new String[]{idColumnName})
                              : connection.prepareStatement(sql)) {
            setParameters(ps, params);
            
            System.out.println("→ SQL: " + sqlToExecute);
            System.out.println("→ AutoCommit: " + connection.getAutoCommit());
            
            if (isPostgres && doReturnKey) {
                // PostgreSQL z RETURNING: wykonaj query i pobierz id z ResultSet
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long id = rs.getLong(1);
                        System.out.println("→ Generated ID: " + id);
                        return id;
                    }
                    throw new SQLException("No generated key returned");
                }
            } else if (isPostgres) {
                // PostgreSQL bez RETURNING: użyj executeUpdate
                int rows = ps.executeUpdate();
                System.out.println("→ Rows affected: " + rows);
                return null;
            } else {
                // Inne bazy (H2, MySQL, etc.): standardowy sposób
                int rows = ps.executeUpdate();
                System.out.println("→ Rows affected: " + rows);
                
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (doReturnKey) {
                        if (keys.next()) {
                            return keys.getLong(1);
                        } else {
                            throw new SQLException("No generated key returned");
                        }
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Insert failed: " + sqlToExecute, e);
        }
    }

    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

}
