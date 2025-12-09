package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.mapping.metadata.EntityMetadata;

public interface EntityPersister {
    EntityMetadata metadata = null;
    InheritanceStrategy inheritanceStrategy = null;

    Object findById(Object id, Session session);
    void insert(Object entity, Session session);
    void update(Object entity, Session session);
    void delete(Object entity, Session session);
}
