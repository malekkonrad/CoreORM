package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.persister.EntityPersister;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class EntitySet<T> implements Set<T> {
    private final Map<String, T> map = new HashMap<>();
    private final Map<Class<?>, EntityPersister> entityPersisters;

    public EntitySet(Map<Class<?>, EntityPersister> entityPersisters) {
        this.entityPersisters = entityPersisters;
    }

    public Object findById(Class<?> entityClass, Object id) {
        String hash;
        try {
            hash = getHash(entityClass, id);
        } catch (RuntimeException e) {
            return null;
        }
        return map.get(hash);
    }

    public Object replaceIfExistsAndAdd(Object entity) {
        String hash;
        try {
            hash = getHash(entity);
        } catch (RuntimeException e) {
            throw new IntegrityException("Unable to get hash: " + e);
        }
        if (map.containsKey(hash)) {
            return map.get(hash);
        } else {
            map.put(hash, (T) entity);
            return entity;
        }
    }

    private String getHash(Class<?> entityClass, Object id) {
        EntityPersister persister = entityPersisters.get(entityClass);
        if (persister == null) {
            throw new RuntimeException("No persister found for " + entityClass);
        }

        StringBuilder hash = new StringBuilder();
        EntityMetadata metadata = persister.getEntityMetadata();
        hash.append(metadata.getEntityClass().getName());
        if (metadata.getIdColumns().values().size() == 1) {
            hash.append(id.toString());
        } else {
            for (PropertyMetadata pm : metadata.getIdColumns().values()) {
                if (ReflectionUtils.doesObjectContainField(id, pm.getName())) {
                    // might have a problem with foreign keys
                    hash.append(ReflectionUtils.getFieldValue(id, pm.getName()).toString());
                } else {
                    throw new RuntimeException("Unable to find field " + pm.getName());
                }
            }
        }
        return hash.toString();
    }

    private String getHash(Object obj) throws RuntimeException {
        EntityPersister persister = entityPersisters.get(obj.getClass());
        if (persister == null) {
            throw new RuntimeException("No persister found for " + obj.getClass());
        }

        StringBuilder hash = new StringBuilder();
        EntityMetadata metadata = persister.getEntityMetadata();
        hash.append(metadata.getEntityClass().getName());
        for (PropertyMetadata pm : metadata.getIdColumns().values()) {
            try {
                // might have a problem with foreign keys
                hash.append(ReflectionUtils.getFieldValue(obj, pm.getName()).toString());
            } catch (RuntimeException e) {
                throw new RuntimeException("Unable to find field " + pm.getName());
            }
        }
        return hash.toString();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        System.out.println("Checking: " + o.getClass().getSimpleName());
        String hash;
        try {
            hash = getHash(o);
        } catch (RuntimeException e) {
            System.out.println("Unable to get hash: " + e);
            return false;
        }
        return map.containsKey(hash);
    }

    @Override
    public Iterator<T> iterator() {
        return map.values().iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        map.values().forEach(action);
    }

    @Override
    public Object[] toArray() {
        return map.values().toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return map.values().toArray(a);
    }

    @Override
    public <T1> T1[] toArray(IntFunction<T1[]> generator) {
        return map.values().toArray(generator);
    }

    @Override
    public boolean add(T t) {
        String hash;
        try {
            hash = getHash(t);
        } catch (RuntimeException e) {
            System.out.println("Tried to add, but failed. " + e.getMessage());
            return false;
        }
        return map.put(hash, t) != null;
    }

    @Override
    public boolean remove(Object o) {
        String hash;
        try {
            hash = getHash(o);
        } catch (RuntimeException e) {
            return false;
        }
        return map.remove(hash) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T t : c) {
            add(t);
        }
        return true;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                remove(o);
            }
        }
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        for (Object o : c) {
            remove(o);
        }
        return true;
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        return map.values().removeIf(filter);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Spliterator<T> spliterator() {
        return map.values().spliterator();
    }

    @Override
    public Stream<T> stream() {
        return map.values().stream();
    }

    @Override
    public Stream<T> parallelStream() {
        return map.values().parallelStream();
    }
}
