package pl.edu.agh.dp.core.api;

import lombok.Getter;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.AssociationMetadata;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.persister.EntityPersister;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.SQLException;
import java.util.*;

public class SessionImpl implements Session {

    // TODO dirty testing
    @Getter
    private final Map<Class<?>, EntityPersister> entityPersisters;

    private final EntitySet<Object> cachedEntities;
    private final Set<Object> newEntities = new LinkedHashSet<>();
    private final Set<Object> dirtyEntities = new HashSet<>();
    private final Set<Object> removedEntities = new HashSet<>();

    private final JdbcExecutor jdbcExecutor;
    private boolean isOpen = false;

    public SessionImpl(JdbcExecutor jdbcExecutor,  Map<Class<?>, EntityPersister> entityPersisters) {
        this.entityPersisters = entityPersisters;
        this.jdbcExecutor = jdbcExecutor;
        this.cachedEntities = new EntitySet<>(entityPersisters);
    }

    @Override
    public <T> void save(T entity) {
        // for relationships, we need to separate each entity
        EntityPersister entityPersister = entityPersisters.get(entity.getClass());
        if (entityPersister == null) {
            throw new IntegrityException(
                    "Could not found mapper for class: " + entity.getClass().getName()
            );
        }
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
                        System.out.println("Setting: " + am.getMappedBy()+ " in " + value + " to " + entity);
                        ReflectionUtils.setFieldValue(value, am.getMappedBy(), entity);
                        this.save(value);
                        newEntities.add(entity);
                    } else {
                        // set opposing relationship
                        System.out.println("Setting: " + am.getMappedBy()+ " in " + value + " to " + entity);
                        ReflectionUtils.setFieldValue(value, am.getMappedBy(), entity);
                        newEntities.add(entity);
                        this.save(value);
                    }
                } else if (am.getType() == AssociationMetadata.Type.ONE_TO_MANY) {
                    System.out.println("Inserting 1 to *");
                    newEntities.add(entity);
                    assert value instanceof Collection;
                    for (Object relationshipEntity : (Collection<?>)value) { // relationship must be some Collection
                        System.out.println("Setting: " + am.getMappedBy()+ " in " + relationshipEntity + " to " + entity);
                        ReflectionUtils.setFieldValue(relationshipEntity, am.getMappedBy(), entity);
                        this.save(relationshipEntity);
                    }
                } else if (am.getType() == AssociationMetadata.Type.MANY_TO_ONE) {
                    System.out.println("Inserting * to 1");
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
                    for (Object relationshipEntity : (Collection<?>)field) {
                        if (relationshipEntity == entity) {
                            isBackrefered = true;
                            break;
                        }
                    }
                    if (!isBackrefered) {
                        System.out.println("Adding: " + value + " to field " + am.getMappedBy() + " to " + entity);
                        ((Collection<T>) field).add(entity);
                    }
                    this.save(value);
                    newEntities.add(entity);
                } else if (am.getType() == AssociationMetadata.Type.MANY_TO_MANY) {
                    System.out.println("Inserting * to *");
                    // fill all the data
                    assert value instanceof Collection;
                    for (Object relationshipEntity : (Collection<?>)value) {
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
                        for (Object reverseEntity : (Collection<?>)field) {
                            if (reverseEntity == entity) {
                                isBackrefered = true;
                                break;
                            }
                        }
                        if (!isBackrefered) {
                            System.out.println("Adding: " + relationshipEntity + " to field " + am.getMappedBy() + " to " + entity);
                            ((Collection<T>) field).add(entity);
                        }
                    }
                    // actual insert
                    if (am.getHasForeignKey()) {
                        for (Object relationshipEntity : (Collection<?>)value) { // relationship must be some Collection
                            this.save(relationshipEntity);
                        }
                        newEntities.add(entity);
                    } else {
                        newEntities.add(entity);
                        for (Object relationshipEntity : (Collection<?>)value) { // relationship must be some Collection
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
        Object entity = cachedEntities.findById(entityClass, id);
        if (entity == null) {
            EntityPersister persister = entityPersisters.get(entityClass);
            entity = persister.findById(id, this);
            // fill the relationship data
            EntityMetadata metadata = persister.getEntityMetadata();
            for (AssociationMetadata associationMetadata : metadata.getAssociationMetadata().values()) {
                if (associationMetadata.getCollectionType() == AssociationMetadata.CollectionType.NONE) {
                    continue;
                }
                ReflectionUtils.setFieldValue(entity, associationMetadata.getField(), associationMetadata.createLazyCollection(this, entity));
            }
            cachedEntities.add(entity);
        }
        return entityClass.cast(entity);
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        EntityPersister persister = entityPersisters.get(entityClass);
        List<T> entities = persister.findAll(entityClass, this);
        for (T entity : entities) {
            // fill the relationship data
            EntityMetadata metadata = persister.getEntityMetadata();
            for (AssociationMetadata associationMetadata : metadata.getAssociationMetadata().values()) {
                if (associationMetadata.getCollectionType() == AssociationMetadata.CollectionType.NONE) {
                    continue;
                }
                ReflectionUtils.setFieldValue(entity, associationMetadata.getField(), associationMetadata.createLazyCollection(this, entity));
            }
        }
        entities.replaceAll(t -> (T) cachedEntities.replaceIfExistsAndAdd(t));
        return entities;
    }

    @Override
    public <T> void delete(T entity) {
        if (newEntities.contains(entity)) {
            throw new IntegrityException(
                    "Attempted to remove entity scheduled for saving.\n" +
                    "Only one operation is permitted per commit.");
        } else if (dirtyEntities.contains(entity)) {
            throw new IntegrityException(
                    "Attempted to remove entity scheduled for update.\n" +
                    "Only one operation is permitted per commit.");
        }
        removedEntities.add(entity);
    }

    @Override
    public <T> void update(T entity) {
        if (newEntities.contains(entity)) {
            throw new IntegrityException(
                    "Attempted to remove entity scheduled for saving.\n" +
                    "Only one operation is permitted per commit.");
        } else if (removedEntities.contains(entity)) {
            throw new IntegrityException(
                    "Attempted to remove entity scheduled for deletion.\n" +
                    "Only one operation is permitted per commit.");
        }
        dirtyEntities.add(entity);
    }

    @Override
    public <T> void load(T entity, String relationshipName) {
        if (!ReflectionUtils.doesClassContainField(entity.getClass(), relationshipName)) {
            throw new IntegrityException("Failed to load relationship: " + relationshipName + " in class: " + entity.getClass().getName());
        }
        System.out.println("Trying to load: " + entity.getClass().getName() + "." + relationshipName);
        Object field = ReflectionUtils.getFieldValue(entity, relationshipName);
        if (field != null) {
            if (!(field instanceof Lazy) || ((Lazy) field).isInitialized()) {
                System.out.println(relationshipName + " already loaded.");
                return;
            }
        }
        EntityMetadata metadata = entityPersisters.get(entity.getClass()).getEntityMetadata();
        AssociationMetadata associationMetadata = metadata.getAssociationMetadata().get(relationshipName);
        assert associationMetadata != null;
        Class<?> relationshipClass = associationMetadata.getTargetEntity();
        EntityMetadata relationshipMetadata = entityPersisters.get(relationshipClass).getEntityMetadata();

        String joinStmt = associationMetadata.getJoinStatement();

        String whereStmt = metadata.getSelectByIdStatement(entity);

        List<Object> entities = (List<Object>) entityPersisters.get(relationshipClass).findAll(relationshipClass, this, joinStmt, whereStmt);
        // singular entity loaded
        if (associationMetadata.getCollectionType() == AssociationMetadata.CollectionType.NONE) {
            if (entities.isEmpty()) {
                return;
            } else if (entities.size() == 1) {
                ReflectionUtils.setFieldValue(entity, relationshipName, entities.get(0));
                return;
            } else {
                throw new IntegrityException("Something unexpected happened");
            }
        }
        // plural entities loaded
        Collection<Object> loaded = (Collection<Object>) associationMetadata.createCollection();
        for (Object value : entities) {
            // fill the relationship data
            for (AssociationMetadata relAssMetadata : relationshipMetadata.getAssociationMetadata().values()) {
                if (relAssMetadata.getCollectionType() == AssociationMetadata.CollectionType.NONE) {
                    if (Objects.equals(relationshipName, relAssMetadata.getMappedBy())) {
                        // this relationship called the load, so we backreference it
                        // this only is true if Collection type is NONE, cause it's not a collection and we are sure
                        // all the objects are already loaded (cause it's been called by it)
                        ReflectionUtils.setFieldValue(value, relAssMetadata.getField(), entity);
                    }
                    continue;
                }
                ReflectionUtils.setFieldValue(value, relAssMetadata.getField(), relAssMetadata.createLazyCollection(this, value));
            }
            value = cachedEntities.replaceIfExistsAndAdd(value);
            loaded.add(value);
        }
        ReflectionUtils.setFieldValue(entity, relationshipName, loaded);
    }

    @Override
    public void commit() {
        flush();
        try {
            jdbcExecutor.commit();  // commit
            cachedEntities.addAll(newEntities);
            newEntities.clear(); // clear after successful rollback
            dirtyEntities.clear();
            cachedEntities.removeAll(removedEntities);
            removedEntities.clear();
            jdbcExecutor.setAutoCommit(true); // end transaction
            jdbcExecutor.setAutoCommit(false); // begin transaction
        } catch (SQLException e) {
            throw new IntegrityException(e.getMessage());
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
            throw new IntegrityException(e.getMessage());
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
            isOpen = true;
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
            isOpen = false;
        }catch(Exception e){
            System.err.println("Error closing connection");
        }

    }

    @Override
    public boolean isOpen() {
        return this.isOpen;
    }

    @Override
    public JdbcExecutor getJdbcExecutor() {
        return jdbcExecutor;
    }
}
