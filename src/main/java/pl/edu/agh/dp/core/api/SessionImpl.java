package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.jdbc.JdbcExecutorImpl;
import pl.edu.agh.dp.core.persister.EntityPersister;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SessionImpl implements Session {

    private final Map<Class<?>, EntityPersister> entityPersisters;

    private final Set<Object> newEntities = new HashSet<>();
    private final Set<Object> dirtyEntities = new HashSet<>();
    private final Set<Object> removedEntities = new HashSet<>();

    private final JdbcExecutor jdbcExecutor;

    public SessionImpl(JdbcExecutor jdbcExecutor,  Map<Class<?>, EntityPersister> entityPersisters) {
        this.entityPersisters = entityPersisters;
        this.jdbcExecutor = jdbcExecutor;
    }

    @Override
    public <T> void save(T entity) {
        newEntities.add(entity);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object id) {
        Object entity = entityPersisters.get(entityClass).findById(id, this);
        return entityClass.cast(entity);
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
            jdbcExecutor.commit();  // commit
            newEntities.clear(); // clear after successful rollback
            dirtyEntities.clear();
            removedEntities.clear();
            jdbcExecutor.setAutoCommit(true); // end transaction
            jdbcExecutor.setAutoCommit(false); // begin transaction
        } catch (SQLException e) {
            throw new RuntimeException(e); // TODO handle Exception
        }
    }

    @Override
    public void rollback() {
        try {
            jdbcExecutor.rollback(); // rollback
            newEntities.clear(); // clear after successful rollback
            dirtyEntities.clear();
            removedEntities.clear();
            jdbcExecutor.setAutoCommit(true); // end transaction
            jdbcExecutor.setAutoCommit(false); // begin transaction
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
            jdbcExecutor.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e); // TODO handle the Exception
        }
    }

    @Override
    public void close() {
        System.out.println("Closing session");
        try{
            jdbcExecutor.rollback(); // default rollback on closing
            newEntities.clear(); // clear after successful rollback
            dirtyEntities.clear();
            removedEntities.clear();
            jdbcExecutor.setAutoCommit(true); // end transactions
            jdbcExecutor.close();
        }catch(Exception e){
            System.err.println("Error closing connection");
        }

    }


    @Override
    public JdbcExecutor getJdbcExecutor() {
        return jdbcExecutor;
    }
}
