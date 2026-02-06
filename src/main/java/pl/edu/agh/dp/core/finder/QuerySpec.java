package pl.edu.agh.dp.core.finder;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Query specification that holds all query criteria.
 * Used to define what data to retrieve and how to filter/order it.
 * 
 * @param <T> the entity type this query targets
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
    
    /**
     * Creates a new QuerySpec for the given entity type.
     * 
     * @param <T> the entity type
     * @param entityType the entity class
     * @return new QuerySpec instance
     */
    public static <T> QuerySpec<T> of(Class<T> entityType) {
        return new QuerySpec<>(entityType);
    }
    
    /**
     * Adds a WHERE condition to this query.
     * Multiple conditions are combined with AND.
     * 
     * @param condition the condition to add
     * @return this QuerySpec for method chaining
     */
    public QuerySpec<T> where(Condition condition) {
        conditions.add(condition);
        return this;
    }
    
    /**
     * Adds an ORDER BY directive to this query.
     * Multiple sorts are applied in the order they are added.
     * 
     * @param sort the sort directive
     * @return this QuerySpec for method chaining
     */
    public QuerySpec<T> orderBy(Sort sort) {
        sortings.add(sort);
        return this;
    }
    
    /**
     * Specifies a relationship path to eagerly fetch.
     * This can be used to load related entities in the same query.
     * 
     * @param path the relationship path (e.g., "department" or "department.manager")
     * @return this QuerySpec for method chaining
     */
    public QuerySpec<T> fetch(String path) {
        fetchPaths.add(path);
        return this;
    }
    
    /**
     * Limits the number of results returned.
     * 
     * @param n maximum number of results
     * @return this QuerySpec for method chaining
     */
    public QuerySpec<T> limit(int n) {
        this.limitValue = n;
        return this;
    }
    
    /**
     * Skips the first n results.
     * 
     * @param n number of results to skip
     * @return this QuerySpec for method chaining
     */
    public QuerySpec<T> offset(int n) {
        this.offsetValue = n;
        return this;
    }
    
    /**
     * Checks if this query has any conditions.
     * 
     * @return true if there are conditions
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }
    
    /**
     * Checks if this query has any sorting.
     * 
     * @return true if there are sort directives
     */
    public boolean hasSorting() {
        return !sortings.isEmpty();
    }
    
    /**
     * Checks if this query has a limit.
     * 
     * @return true if limit is set
     */
    public boolean hasLimit() {
        return limitValue != null;
    }
    
    /**
     * Checks if this query has an offset.
     * 
     * @return true if offset is set
     */
    public boolean hasOffset() {
        return offsetValue != null;
    }
    
    /**
     * Checks if this query has fetch paths.
     * 
     * @return true if there are fetch paths
     */
    public boolean hasFetch() {
        return !fetchPaths.isEmpty();
    }
}
