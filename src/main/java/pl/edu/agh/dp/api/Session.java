package pl.edu.agh.dp.api;

/**
 * AutoCloseable to be able to use in try ( ... )
 */
public interface Session extends AutoCloseable {
    <T> void save(T entity);
    <T> T find(Class<T> entityClass, Object id);
    <T> void delete(T entity);
    void close();
}
