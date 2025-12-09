package pl.edu.agh.dp.core.session;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.persister.EntityPersister;
import pl.edu.agh.dp.core.persister.impl.EntityPersisterImpl;
import pl.edu.agh.dp.core.uow.DefaultUnitOfWork;
import pl.edu.agh.dp.core.uow.UnitOfWork;

import java.sql.Connection;
import java.util.Map;

public class SessionImpl implements Session {

    private final Connection connectionProvider;
    private final Map<Class<?>, EntityPersister> entityPersisters;

    private final UnitOfWork unitOfWork;

    public SessionImpl(Connection connectionProvider,
                       Map<Class<?>, EntityPersister> entityPersisters) {
        this.unitOfWork = new DefaultUnitOfWork();
        this.connectionProvider = connectionProvider;
        this.entityPersisters = entityPersisters;
    }

    @Override
    public <T> void save(T entity) {

    }

    @Override
    public <T> T find(Class<T> entityClass, Object id) {
        return null;
    }

    @Override
    public <T> void delete(T entity) {

    }

    @Override
    public <T> void update(T entity) {

    }

    @Override
    public void commit() {

    }

    @Override
    public void rollback() {

    }

    @Override
    public void flush() {

    }

    @Override
    public void begin() {

    }

    @Override
    public void close() {

    }
}
