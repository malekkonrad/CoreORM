package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.persister.EntityPersister;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SessionImpl implements Session {

    private final Connection connection;
    private final Map<Class<?>, EntityPersister> entityPersisters;

    private final Set<Object> newEntities = new HashSet<>();
    private final Set<Object> dirtyEntities = new HashSet<>();
    private final Set<Object> removedEntities = new HashSet<>();

    public SessionImpl(Connection connection,
                       Map<Class<?>, EntityPersister> entityPersisters) {
        this.connection = connection;
        this.entityPersisters = entityPersisters;
    }

    @Override
    public <T> void save(T entity) {
        newEntities.add(entity);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object id) {
        return null;
    }

    @Override
    public <T> void delete(T entity) {
        removedEntities.add(entity);
    }

    @Override
    public <T> void update(T entity) {
        dirtyEntities.add(entity);
    }

    @Override
    public void commit() {
        flush();
        try {
            connection.commit(); // commit
            newEntities.clear(); // clear after successful rollback
            dirtyEntities.clear();
            removedEntities.clear();
            connection.setAutoCommit(true); // end transaction
            connection.setAutoCommit(false); // begin transaction
        } catch (SQLException e) {
            throw new RuntimeException(e); // TODO handle Exception
        }
    }

    @Override
    public void rollback() {
        try {
            connection.rollback(); // rollback
            newEntities.clear(); // clear after successful rollback
            dirtyEntities.clear();
            removedEntities.clear();
            connection.setAutoCommit(true); // end transaction
            connection.setAutoCommit(false); // begin transaction
        } catch (SQLException e) {
            throw new RuntimeException(e); // TODO handle Exception
        }
    }

    @Override
    public void flush() {
        // just add to database without commiting it
        for (Object entity : newEntities) {
            entityPersisters.get(entity.getClass()).insert(entity, this);
        }
    }

    @Override
    public void begin() {
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e); // TODO handle the Exception
        }
    }

    @Override
    public void close() {
        System.out.println("Closing session");
        try{
            connection.rollback(); // default rollback on closing
            newEntities.clear(); // clear after successful rollback
            dirtyEntities.clear();
            removedEntities.clear();
            connection.setAutoCommit(true); // end transactions
            connection.close();
        }catch(Exception e){
            System.err.println("Error closing connection");
        }

    }

    // TODO I think it's useless now ?
    @Override
    public EntityPersister getEntityPersister(Class<?> clazz) {
        return entityPersisters.get(clazz);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }
}
