package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class LazyList<T> implements List<T>, Lazy {
    private List<T> delegate;
    private Session session;
    private Object owner;
    private String relationshipName;

    public LazyList(Session session, Object owner, String relationshipName) {
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
            delegate = (List<T>) ReflectionUtils.getFieldValue(owner, relationshipName);
        }
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        initialize();
        delegate.forEach(action);
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
    public boolean addAll(int index, Collection<? extends T> c) {
        initialize();
        return delegate.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        initialize();
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        initialize();
        return delegate.retainAll(c);
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        initialize();
        delegate.replaceAll(operator);
    }

    @Override
    public void sort(Comparator<? super T> c) {
        initialize();
        delegate.sort(c);
    }

    @Override
    public void clear() {
        initialize();
        delegate.clear();
    }

    @Override
    public T get(int index) {
        initialize();
        return delegate.get(index);
    }

    @Override
    public T set(int index, T element) {
        initialize();
        return delegate.set(index, element);
    }

    @Override
    public void add(int index, T element) {
        initialize();
        delegate.add(index, element);
    }

    @Override
    public T remove(int index) {
        initialize();
        return delegate.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        initialize();
        return delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        initialize();
        return delegate.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        initialize();
        return delegate.listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        initialize();
        return delegate.listIterator(index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        initialize();
        return delegate.subList(fromIndex, toIndex);
    }

    @Override
    public Spliterator<T> spliterator() {
        initialize();
        return delegate.spliterator();
    }

//    @Override
//    public void addFirst(T t) {
//        initialize();
//        delegate.addFirst(t);
//    }
//
//    @Override
//    public void addLast(T t) {
//        initialize();
//        delegate.addLast(t);
//    }

//    @Override
//    public T getFirst() {
//        initialize();
//        return delegate.getFirst();
//    }
//
//    @Override
//    public T getLast() {
//        initialize();
//        return delegate.getLast();
//    }
//
//    @Override
//    public T removeFirst() {
//        initialize();
//        return delegate.removeFirst();
//    }
//
//    @Override
//    public T removeLast() {
//        initialize();
//        return delegate.removeLast();
//    }
//
//    @Override
//    public List<T> reversed() {
//        initialize();
//        return delegate.reversed();
//    }
}
