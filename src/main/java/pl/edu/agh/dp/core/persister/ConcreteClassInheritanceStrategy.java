package pl.edu.agh.dp.core.persister;

import javafx.util.Pair;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PairTargetStatements;

import java.util.List;

public class ConcreteClassInheritanceStrategy extends AbstractInheritanceStrategy{

    public ConcreteClassInheritanceStrategy(EntityMetadata metadata) {super(metadata);}

    @Override
    public Pair<String, String> create() {
        return null;
    }

    @Override
    public Object insert(Object entity, Session session) {
        return null;
    }

    @Override
    public void update(Object entity, Session session) {

    }

    @Override
    public void delete(Object entity, Session session) {

    }

    @Override
    public Object findById(Object id, Session session) {
        return null;
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session, PairTargetStatements pairTargetStatements) {
        return List.of();
    }

    @Override
    public <T> List<T> findBy(Class<T> type, Session session, QuerySpec<T> querySpec) {
        return List.of();
    }

    @Override
    public PairTargetStatements getPairStatement(Object entity, String relationshipName) {
        return null;
    }
}
