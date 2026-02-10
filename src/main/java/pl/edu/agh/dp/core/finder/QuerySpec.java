package pl.edu.agh.dp.core.finder;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Query specification that holds all query criteria.
 * Used to define what data to retrieve and how to filter/order it.
 */
@Getter
public class QuerySpec<T> {
    
    private final Class<T> entityType;
    private final List<Condition> conditions = new ArrayList<>();
    private final List<Sort> sortings = new ArrayList<>();
    private final List<String> fetchPaths = new ArrayList<>();
    private Integer limitValue = null;
    private Integer offsetValue = null;
    
    private QuerySpec(Class<T> entityType) {
        this.entityType = entityType;
    }

    public static <T> QuerySpec<T> of(Class<T> entityType) {
        return new QuerySpec<>(entityType);
    }

    public QuerySpec<T> where(Condition condition) {
        conditions.add(condition);
        return this;
    }

    public QuerySpec<T> orderBy(Sort sort) {
        sortings.add(sort);
        return this;
    }

    public QuerySpec<T> fetch(String path) {
        fetchPaths.add(path);
        return this;
    }

    public QuerySpec<T> limit(int n) {
        this.limitValue = n;
        return this;
    }

    public QuerySpec<T> offset(int n) {
        this.offsetValue = n;
        return this;
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    public boolean hasSorting() {
        return !sortings.isEmpty();
    }

    public boolean hasLimit() {
        return limitValue != null;
    }

    public boolean hasOffset() {
        return offsetValue != null;
    }

    public boolean hasFetch() {
        return !fetchPaths.isEmpty();
    }
}
