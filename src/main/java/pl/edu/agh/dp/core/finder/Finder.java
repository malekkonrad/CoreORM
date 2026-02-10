package pl.edu.agh.dp.core.finder;

import lombok.Getter;
import pl.edu.agh.dp.core.api.Session;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class Finder<T> {
    
    private final Session session;
    @Getter
    private final QuerySpec<T> spec;

    public Finder(Session session, Class<T> type) {
        this.session = session;
        this.spec = QuerySpec.of(type);
    }
    
    // ==================== Condition Methods ====================

    public Finder<T> eq(String field, Object value) {
        spec.where(new Eq(field, value));
        return this;
    }

    public Finder<T> notEq(String field, Object value) {
        spec.where(new NotEq(field, value));
        return this;
    }

    public Finder<T> like(String field, String pattern) {
        spec.where(new Like(field, pattern));
        return this;
    }

    public Finder<T> gt(String field, Object value) {
        spec.where(new Gt(field, value));
        return this;
    }

    public Finder<T> gte(String field, Object value) {
        spec.where(new Gte(field, value));
        return this;
    }

    public Finder<T> lt(String field, Object value) {
        spec.where(new Lt(field, value));
        return this;
    }

    public Finder<T> lte(String field, Object value) {
        spec.where(new Lte(field, value));
        return this;
    }

    public Finder<T> in(String field, Collection<?> values) {
        spec.where(new In(field, values));
        return this;
    }

    public Finder<T> isNull(String field) {
        spec.where(new IsNull(field, true));
        return this;
    }

    public Finder<T> isNotNull(String field) {
        spec.where(new IsNull(field, false));
        return this;
    }

    public Finder<T> between(String field, Object low, Object high) {
        spec.where(new Between(field, low, high));
        return this;
    }

    public Finder<T> where(Condition condition) {
        spec.where(condition);
        return this;
    }
    
    // ==================== Sorting Methods ====================

    public Finder<T> orderAsc(String field) {
        spec.orderBy(Sort.asc(field));
        return this;
    }

    public Finder<T> orderDesc(String field) {
        spec.orderBy(Sort.desc(field));
        return this;
    }

    public Finder<T> orderBy(Sort sort) {
        spec.orderBy(sort);
        return this;
    }
    
    // ==================== Fetch Methods ====================

    public Finder<T> fetch(String path) {
        spec.fetch(path);
        return this;
    }
    
    // ==================== Pagination Methods ====================

    public Finder<T> limit(int n) {
        spec.limit(n);
        return this;
    }

    public Finder<T> offset(int n) {
        spec.offset(n);
        return this;
    }
    
    // ==================== Execution Methods ====================

    public List<T> list() {
        return session.findBy(spec);
    }

    public Optional<T> first() {
        spec.limit(1);
        List<T> results = session.findBy(spec);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public T single() {
        spec.limit(2); // Get 2 to detect multiple results
        List<T> results = session.findBy(spec);
        if (results.isEmpty()) {
            throw new IllegalStateException("Expected exactly one result, but found none");
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected exactly one result, but found multiple");
        }
        return results.get(0);
    }

    public Optional<T> singleOptional() {
        spec.limit(2);
        List<T> results = session.findBy(spec);
        if (results.size() > 1) {
            throw new IllegalStateException("Expected at most one result, but found multiple");
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

}
