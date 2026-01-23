package pl.edu.agh.dp.core.api;

import lombok.Getter;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.jdbc.JdbcExecutorImpl;
import pl.edu.agh.dp.core.mapping.AssociationMetadata;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.persister.EntityPersister;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class SessionImpl implements Session {

    // TODO dirty testing
    @Getter
    private final Map<Class<?>, EntityPersister> entityPersisters;

    private final Set<Object> cachedEntities = new HashSet<>();
    private final Set<Object> newEntities = new LinkedHashSet<>();
    private final Set<Object> dirtyEntities = new HashSet<>();
    private final Set<Object> removedEntities = new HashSet<>();

    private final JdbcExecutor jdbcExecutor;

    public SessionImpl(JdbcExecutor jdbcExecutor,  Map<Class<?>, EntityPersister> entityPersisters) {
        this.entityPersisters = entityPersisters;
        this.jdbcExecutor = jdbcExecutor;
    }

    @Override
    public <T> void save(T entity) {
        // for relationships we need to separate each entity
        EntityPersister entityPersister = entityPersisters.get(entity.getClass());
        EntityMetadata entityMetadata = entityPersister.getEntityMetadata();
        Collection<AssociationMetadata> associationMetadata = entityMetadata.getAssociationMetadata().values();
        // don't add if exists
        if (newEntities.contains(entity)) {
            return;
        }
        // don't add if cached
        if (cachedEntities.contains(entity)) {
            return;
        }
        // no relationships, simple add
        if (associationMetadata.isEmpty()) {
            newEntities.add(entity);
            return;
        }
        // there are relationships
        for (AssociationMetadata am : associationMetadata) {
            Object value = ReflectionUtils.getFieldValue(entity, am.getField());
            if (value != null) {
                if (am.getType() == AssociationMetadata.Type.ONE_TO_ONE) {
                    System.out.println("Inserting 1 to 1");
                    // determine where is the fk key
                    // first insert the dominating entity, later the fk one
                    if (am.getHasForeignKey()) {
                        // set opposing relationship
                        // TODO do not set when null
                        System.out.println("Setting: " + am.getMappedBy()+ " in " + value + " to " + entity);
                        ReflectionUtils.setFieldValue(value, am.getMappedBy(), entity);
                        this.save(value);
                        newEntities.add(entity);
                    } else {
                        // set opposing relationship
                        // TODO do not set when null
                        // TODO error on the wrong set
                        System.out.println("Setting: " + am.getMappedBy()+ " in " + value + " to " + entity);
                        ReflectionUtils.setFieldValue(value, am.getMappedBy(), entity);
                        newEntities.add(entity);
                        this.save(value);
                    }
                } else if (am.getType() == AssociationMetadata.Type.ONE_TO_MANY) {
                    System.out.println("Inserting 1 to *");
                    newEntities.add(entity);
                    assert value instanceof Collection;
                    for (Object relationshipEntity : (Collection)value) { // relationship must be some Collection
                        // TODO do not set when null
                        // TODO error on the wrong set
                        System.out.println("Setting: " + am.getMappedBy()+ " in " + relationshipEntity + " to " + entity);
                        ReflectionUtils.setFieldValue(relationshipEntity, am.getMappedBy(), entity);
                        this.save(relationshipEntity);
                    }
                } else if (am.getType() == AssociationMetadata.Type.MANY_TO_ONE) {
                    System.out.println("Inserting * to 1");
                    // TODO do not set when null
                    // TODO error on the wrong set
                    Object field = ReflectionUtils.getFieldValue(value, am.getMappedBy());
                    if (field == null) {
                        throw new IntegrityException(
                                "Field cannot be null.\n" +
                                "Source class: " + value.getClass().getName() + "\n" +
                                "Field: " + am.getMappedBy() + "\n" +
                                "Field is set to null, but it should be initialized."
                        );
                    }
                    assert field instanceof Collection;
                    boolean isBackrefered = false;
                    for (Object relationshipEntity : (Collection)field) {
                        if (relationshipEntity == entity) {
                            isBackrefered = true;
                        }
                    }
                    if (!isBackrefered) {
                        System.out.println("Adding: " + value + " to field " + am.getMappedBy() + " to " + entity);
                        ((Collection<T>) field).add(entity); // TODO some assert ???
                    }
                    this.save(value);
                    newEntities.add(entity);
                } else if (am.getType() == AssociationMetadata.Type.MANY_TO_MANY) {
                    System.out.println("Inserting * to *");
                    // fill all the data
                    assert value instanceof Collection;
                    for (Object relationshipEntity : (Collection)value) {
                        Object field = ReflectionUtils.getFieldValue(relationshipEntity, am.getMappedBy());
                        if (field == null) {
                            throw new IntegrityException(
                                    "Field cannot be null.\n" +
                                    "Source class: " + relationshipEntity.getClass().getName() + "\n" +
                                    "Field: " + am.getMappedBy() + "\n" +
                                    "Field is set to null, but it should be initialized."
                            );
                        }
                        assert field instanceof Collection;
                        boolean isBackrefered = false;
                        for (Object reverseEntity : (Collection)field) {
                            if (reverseEntity == entity) {
                                isBackrefered = true;
                            }
                        }
                        if (!isBackrefered) {
                            System.out.println("Adding: " + relationshipEntity + " to field " + am.getMappedBy() + " to " + entity);
                            ((Collection<T>) field).add(entity); // TODO some assert ???
                        }
                    }
                    // actual insert
                    if (am.getHasForeignKey()) {
                        for (Object relationshipEntity : (Collection)value) { // relationship must be some Collection
                            this.save(relationshipEntity);
                        }
                        newEntities.add(entity);
                    } else {
                        newEntities.add(entity);
                        for (Object relationshipEntity : (Collection)value) { // relationship must be some Collection
                            this.save(relationshipEntity);
                        }
                    }
                } else {
                    throw new IntegrityException("Unhandled association type: " + am.getType());
                }
            } else {
                // default to add
                newEntities.add(entity);
            }
        }
    }

    @Override
    public <T> T find(Class<T> entityClass, Object id) {
        Object entity = entityPersisters.get(entityClass).findById(id, this);
        cachedEntities.add(entity);
        return entityClass.cast(entity);
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        List<T> entity = entityPersisters.get(entityClass).findAll(entityClass, this);
        cachedEntities.addAll(entity);
        return entity;
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
            cachedEntities.addAll(newEntities);
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
        System.out.println("Object to add: " + newEntities.size());
        for (Object entity : newEntities) {
            System.out.println("NOW INSERTING: " + entity);
            entityPersisters.get(entity.getClass()).insert(entity, this);
        }
        for (Object entity : removedEntities) {
            entityPersisters.get(entity.getClass()).delete(entity, this);
        }
        for (Object entity : dirtyEntities){
            entityPersisters.get(entity.getClass()).update(entity, this);
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
