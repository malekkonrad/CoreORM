package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class LazySet<T> implements Set<T>, Lazy {
    private Set<T> delegate;
    private Session session;
    private Object owner;
    private String relationshipName;

    public LazySet(Session session, Object owner, String relationshipName) {
        this.session = session;
        this.owner = owner;
        this.relationshipName = relationshipName;
    }

    @Override
    public boolean isInitialized() {
        return delegate != null;
    }

    @Override
    public void initialize() {
        if (!isInitialized()) {
            if (!session.isOpen()) {
                throw new IllegalStateException("Session is not open");
            }
            session.load(owner, relationshipName);
            // this is stupid but works
            delegate = (Set<T>) ReflectionUtils.getFieldValue(owner, relationshipName);
        }
    }

    @Override
    public int size() {
        initialize();
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        initialize();
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        initialize();
        return delegate.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        initialize();
        return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        initialize();
        return delegate.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        initialize();
        return delegate.toArray(a);
    }

    @Override
    public boolean add(T t) {
        initialize();
        return delegate.add(t);
    }

    @Override
    public boolean remove(Object o) {
        initialize();
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        initialize();
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        initialize();
        return delegate.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        initialize();
        return delegate.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        initialize();
        return delegate.removeAll(c);
    }

    @Override
    public void clear() {
        initialize();
        delegate.clear();
    }

    @Override
    public Spliterator<T> spliterator() {
        initialize();
        return delegate.spliterator();
    }

    @Override
    public <T1> T1[] toArray(IntFunction<T1[]> generator) {
        initialize();
        return delegate.toArray(generator);
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        initialize();
        return delegate.removeIf(filter);
    }

    @Override
    public Stream<T> stream() {
        initialize();
        return delegate.stream();
    }

    @Override
    public Stream<T> parallelStream() {
        initialize();
        return delegate.parallelStream();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        initialize();
        delegate.forEach(action);
    }
}
