package pl.edu.agh.dp.core.uow;

import java.util.HashSet;
import java.util.Set;

public class DefaultUnitOfWork implements UnitOfWork {

    private final Set<Object> newEntities = new HashSet<>();
    private final Set<Object> dirtyEntities = new HashSet<>();
    private final Set<Object> removedEntities = new HashSet<>();



    @Override
    public void registerNew(Object entity) {

    }

    @Override
    public void registerDirty(Object entity) {

    }

    @Override
    public void registerClean(Object entity) {

    }

    @Override
    public void registerRemoved(Object entity) {

    }

    @Override
    public void commit() {

    }

    @Override
    public void rollback() {

    }
}
