package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.mapping.EntityMetadata;

import java.util.List;

public interface EntityPersister {
    Object findById(Object id, Session session);
    List<Object> findAll(Class<?> entityClass, Session session);
    void insert(Object entity, Session session);
    void update(Object entity, Session session);
    void delete(Object entity, Session session);

    InheritanceStrategy getInheritanceStrategy();
}
