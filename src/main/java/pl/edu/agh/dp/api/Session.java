package pl.edu.agh.dp.api;

import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.persister.EntityPersister;

import java.sql.Connection;
import java.util.List;

/**
 * AutoCloseable to be able to use in try ( ... )
 */
public interface Session extends AutoCloseable {

    <T> void save(T entity);
    <T> T find(Class<T> entityClass, Object id);
    <T> List<T> findAll(Class<T> entityClass);
    <T> void delete(T entity);
    <T>  void update(T entity);
    void commit();
    void rollback();
    void flush();
    void begin();
    void close();

    JdbcExecutor getJdbcExecutor();
}
