package pl.edu.agh.dp.core.persister;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PairTargetStatements;

import java.util.*;

@Getter
@Setter
@NoArgsConstructor
public class EntityPersisterImpl implements EntityPersister {

    private EntityMetadata metadata;

    public EntityMetadata getEntityMetadata() {
        return metadata;
    }

    private InheritanceStrategy inheritanceStrategy;

    public EntityPersisterImpl(EntityMetadata metadata) {
        this.metadata = metadata;
        this.inheritanceStrategy = InheritanceStrategyFactory.build(metadata.getInheritanceMetadata().getType(), metadata);
    }

    @Override
    public Object findById(Object id, Session session) {
        return  inheritanceStrategy.findById(id, session);
    }

    @Override
    public  <T> List<T> findAll(Class<T> entityClass, Session session) {
        return inheritanceStrategy.findAll(entityClass, session, new PairTargetStatements());
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass, Session session, PairTargetStatements pairTargetStatements) {
        return inheritanceStrategy.findAll(entityClass, session, pairTargetStatements);
    }

    @Override
    public <T> List<T> findBy(Class<T> entityClass, Session session, QuerySpec<T> querySpec) {
        return inheritanceStrategy.findBy(entityClass, session, querySpec);
    }

    @Override
    public void insert(Object entity, Session session) {
        inheritanceStrategy.insert(entity, session);
    }

    @Override
    public void update(Object entity, Session session) {
        inheritanceStrategy.update(entity, session);
    }

    @Override
    public void delete(Object entity, Session session) {
        inheritanceStrategy.delete(entity, session);
    }
}
