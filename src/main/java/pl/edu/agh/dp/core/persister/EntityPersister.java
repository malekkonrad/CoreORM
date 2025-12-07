package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.mapping.metadata.EntityMetadata;

public interface EntityPersister {
    EntityMetadata metadata = null;
    InheritanceStrategy inheritanceStrategy = null;

    public Object findById(Object id, Session session);
    public void insert(Object entity, Session session);
    public void update(Object entity, Session session);
    public void delete(Object entity, Session session);
}
