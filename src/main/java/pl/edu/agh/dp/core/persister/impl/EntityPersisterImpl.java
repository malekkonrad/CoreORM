package pl.edu.agh.dp.core.persister.impl;

import lombok.NoArgsConstructor;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.mapping.metadata.EntityMetadata;
import pl.edu.agh.dp.core.persister.EntityPersister;

@NoArgsConstructor
public class EntityPersisterImpl implements EntityPersister {

    private EntityMetadata metadata;

    public EntityPersisterImpl(EntityMetadata metadata) {
        this.metadata = metadata;
    }


    @Override
    public Object findById(Object id, Session session) {
        return null;
    }

    @Override
    public void insert(Object entity, Session session) {

    }

    @Override
    public void update(Object entity, Session session) {

    }

    @Override
    public void delete(Object entity, Session session) {

    }
}
