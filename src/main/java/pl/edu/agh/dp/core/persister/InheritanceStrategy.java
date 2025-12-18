package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.core.mapping.EntityMetadata;

import java.util.List;

public interface InheritanceStrategy {
    Object insert(EntityMetadata rootMetadata, Object entity);
    void update(EntityMetadata rootMetadata, Object entity);
    void delete(EntityMetadata rootMetadata, Object entity);
    <T> T findById(EntityMetadata rootMetadata, Class<T> type, Object id);
    <T> List<T> findAll(EntityMetadata rootMetadata, Class<T> type);
}
