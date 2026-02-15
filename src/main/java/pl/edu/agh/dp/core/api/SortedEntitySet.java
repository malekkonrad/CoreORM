package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.core.mapping.AssociationMetadata;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.persister.EntityPersister;

import java.util.*;

public class SortedEntitySet<T> implements Set<T> {
    private final Map<Class<?>, Set<T>> entitySets;
    private final List<Class<?>> entityOrder;

    public SortedEntitySet(Map<Class<?>, EntityPersister> entityPersisters) {
        entityOrder = new ArrayList<>();
        entitySets = new HashMap<>();
        createEntityOrder(entityPersisters);
    }

    private void createEntityOrder(Map<Class<?>, EntityPersister> entityPersisters) {
        Deque<EntityMetadata> stack = new ArrayDeque<>();
        for (EntityPersister entityPersister : entityPersisters.values()) {
            stack.addLast(entityPersister.getEntityMetadata());
        }
        while (!stack.isEmpty()) {
            EntityMetadata entityMetadata = stack.poll();
            List<AssociationMetadata> associationMetadata = entityMetadata.getAssociationMetadata()
                    .values()
                    .stream()
                    .filter((am) -> {
                        boolean isItself = am.getTargetEntity() == entityMetadata.getEntityClass();
                        boolean isBefore = true;
                        if (am.getHasForeignKey()) {
                            Class<?> clazz = am.getTargetEntity();
                            EntityMetadata targetMeta = entityPersisters.get(clazz).getEntityMetadata();
                            List<EntityMetadata> c = targetMeta.getInheritanceMetadata().getAllChildren();
                            Set<Class<?>> classSet = new HashSet<>(){{add(clazz);}};
                            for (var e : c) {
                                classSet.add(e.getEntityClass());
                            }
                            if (!new HashSet<>(entityOrder).containsAll(classSet)) {
                                isBefore = false;
                            }
                        }
                        return !(isItself || isBefore);
                    }) // filter itself and added relationships
                    .toList();
            // no Associations
            if (associationMetadata.isEmpty()) {
                entityOrder.add(entityMetadata.getEntityClass());
                entitySets.put(entityMetadata.getEntityClass(), new LinkedHashSet<>());
            } else {
                // add back
                stack.add(entityMetadata);
            }
        }
    }

    @Override
    public int size() {
        return entitySets.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public boolean isEmpty() {
        return entitySets.values().stream().mapToInt(Set::size).sum() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return entitySets.containsKey(o.getClass()) && entitySets.get(o.getClass()).contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {

            private int classIndex = 0;
            private Iterator<T> currentIterator = getNextIterator();

            private Iterator<T> getNextIterator() {
                while (classIndex < entityOrder.size()) {
                    Class<?> clazz = entityOrder.get(classIndex++);
                    Set<T> set = entitySets.get(clazz);
                    if (set != null && !set.isEmpty()) {
                        return set.iterator();
                    }
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                if (currentIterator == null) {
                    return false;
                }

                if (currentIterator.hasNext()) {
                    return true;
                }

                currentIterator = getNextIterator();
                return currentIterator != null && currentIterator.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return currentIterator.next();
            }
        };
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[size()];
        int i = 0;
        for (T element : this) {
            result[i++] = element;
        }
        return result;
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        List<T> list = new ArrayList<>();
        for (T element : this) {
            list.add(element);
        }
        return list.toArray(a);
    }


    @Override
    public boolean add(T t) {
        return entitySets.get(t.getClass()).add(t);
    }

    @Override
    public boolean remove(Object o) {
        return entitySets.containsKey(o.getClass()) && entitySets.get(o.getClass()).remove(o);
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
            entitySets.get(t.getClass()).add(t);
        }
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        entitySets.values().forEach(entitySet -> entitySet.retainAll(c));
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        entitySets.values().forEach(entitySet -> entitySet.removeAll(c));
        return entitySets.values().stream().allMatch(Set::isEmpty);
    }

    @Override
    public void clear() {
        entitySets.values().forEach(Set::clear);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append(" {\n");

        for (Class<?> clazz : entityOrder) {
            sb.append("  ")
                    .append(clazz.getSimpleName())
                    .append(":");

            Set<T> set = entitySets.get(clazz);

            if (set == null || set.isEmpty()) {
                sb.append(" []\n");
            } else {
                sb.append(" [\n");
                for (T entity : set) {
                    sb.append("    ")
                            .append(entity)
                            .append("\n");
                }
                sb.append("  ]\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }
}
