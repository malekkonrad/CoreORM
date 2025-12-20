package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;

import java.util.List;

public interface InheritanceStrategy {
    String create(JdbcExecutor jdbcExecutor);
    Object insert(EntityMetadata rootMetadata, Object entity, Session session);
    void update(EntityMetadata rootMetadata, Object entity, Session session);
    void delete(EntityMetadata rootMetadata, Object entity, Session session);
    <T> T findById(EntityMetadata rootMetadata, Class<T> type, Object id, Session session);
    <T> List<T> findAll(EntityMetadata rootMetadata, Class<T> type, Session session);
}
