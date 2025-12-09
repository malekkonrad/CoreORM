package pl.edu.agh.dp.core.uow;

import pl.edu.agh.dp.api.Session;

public interface UnitOfWork {

    void registerNew(Object entity);
    void registerDirty(Object entity);
    void registerClean(Object entity);
    void registerRemoved(Object entity);
    void commit(Session session);
    void rollback();
}
