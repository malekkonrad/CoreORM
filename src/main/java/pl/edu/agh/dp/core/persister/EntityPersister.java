package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PairTargetStatements;
import pl.edu.agh.dp.core.mapping.TargetStatement;

import java.util.List;

public interface EntityPersister {
    Object findById(Object id, Session session);
    <T> List<T> findAll(Class<T> entityClass, Session session);
    <T> List<T> findAll(Class<T> entityClass, Session session, PairTargetStatements pairTargetStatements);
    <T> List<T> findBy(Class<T> entityClass, Session session, QuerySpec<T> querySpec);
    void insert(Object entity, Session session);
    void update(Object entity, Session session);
    void delete(Object entity, Session session);

    InheritanceStrategy getInheritanceStrategy();
    EntityMetadata getEntityMetadata();
}
