package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.persister.EntityPersister;
import pl.edu.agh.dp.core.persister.DefaultUnitOfWork;
import pl.edu.agh.dp.core.persister.UnitOfWork;

import java.sql.Connection;
import java.util.Map;

public class SessionImpl implements Session {

    private final Connection connection;
    private final Map<Class<?>, EntityPersister> entityPersisters;

    private final UnitOfWork uow;

    public SessionImpl(Connection connection,
                       Map<Class<?>, EntityPersister> entityPersisters) {
        this.uow = new DefaultUnitOfWork();
        this.connection = connection;
        this.entityPersisters = entityPersisters;
    }

    @Override
    public <T> void save(T entity) {
        uow.registerNew(entity);
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
        System.out.println("Closing session");
        uow.commit(this);
        try{
            connection.close();
        }catch(Exception e){
            System.err.println("Error closing connection");
        }

    }

    @Override
    public EntityPersister getEntityPersister(Class<?> clazz) {
        return entityPersisters.get(clazz);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }
}
