package pl.edu.agh.dp.api;

import pl.edu.agh.dp.core.finder.Finder;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.persister.EntityPersister;

import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * AutoCloseable to be able to use in try ( ... )
 */
public interface Session extends AutoCloseable {

    <T> void save(T entity);
    <T> T find(Class<T> entityClass, Object id);
    <T> List<T> findAll(Class<T> entityClass);
    <T> List<T> findBy(QuerySpec<T> querySpec);
    <T> Finder<T> finder(Class<T> entityClass);
    <T> void delete(T entity);
    <T>  void update(T entity);
    <T> void load(T entity, String relationshipName);
    void commit();
    void rollback();
    void flush();
    void begin();
    void close();
    boolean isOpen();

    JdbcExecutor getJdbcExecutor();
    Map<Class<?>, EntityPersister> getEntityPersisters();
}
