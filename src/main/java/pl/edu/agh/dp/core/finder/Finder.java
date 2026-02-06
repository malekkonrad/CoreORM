package pl.edu.agh.dp.core.finder;

import pl.edu.agh.dp.api.Session;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Fluent API for building and executing queries.
 * Allows searching entities by various conditions without writing SQL.
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * List<Employee> employees = session.finder(Employee.class)
 *     .eq("department", "IT")
 *     .gt("salary", 50000)
 *     .like("name", "John%")
 *     .orderAsc("name")
 *     .limit(10)
 *     .list();
 * }</pre>
 * 
 * @param <T> the entity type
 */
public final class Finder<T> {
    
    private final Session session;
    private final QuerySpec<T> spec;
    
    /**
     * Creates a new Finder for the given entity type.
     * 
     * @param session the session to execute queries on
     * @param type the entity class to query
     */
    public Finder(Session session, Class<T> type) {
        this.session = session;
        this.spec = QuerySpec.of(type);
    }
    
    // ==================== Condition Methods ====================
    
    /**
     * Adds an equality condition: field = value
     * 
     * @param field the field name
     * @param value the value to match
     * @return this Finder for method chaining
     */
    public Finder<T> eq(String field, Object value) {
        spec.where(new Eq(field, value));
        return this;
    }
    
    /**
     * Adds a not-equal condition: field != value
     * 
     * @param field the field name
     * @param value the value to exclude
     * @return this Finder for method chaining
     */
    public Finder<T> notEq(String field, Object value) {
        spec.where(new NotEq(field, value));
        return this;
    }
    
    /**
     * Adds a LIKE condition for pattern matching.
     * Use % for wildcard (any characters) and _ for single character.
     * 
     * @param field the field name
     * @param pattern the pattern to match (e.g., "John%", "%son", "%doe%")
     * @return this Finder for method chaining
     */
    public Finder<T> like(String field, String pattern) {
        spec.where(new Like(field, pattern));
        return this;
    }
    
    /**
     * Adds a greater-than condition: field > value
     * 
     * @param field the field name
     * @param value the value to compare against
     * @return this Finder for method chaining
     */
    public Finder<T> gt(String field, Object value) {
        spec.where(new Gt(field, value));
        return this;
    }
    
    /**
     * Adds a greater-than-or-equal condition: field >= value
     * 
     * @param field the field name
     * @param value the value to compare against
     * @return this Finder for method chaining
     */
    public Finder<T> gte(String field, Object value) {
        spec.where(new Gte(field, value));
        return this;
    }
    
    /**
     * Adds a less-than condition: field < value
     * 
     * @param field the field name
     * @param value the value to compare against
     * @return this Finder for method chaining
     */
    public Finder<T> lt(String field, Object value) {
        spec.where(new Lt(field, value));
        return this;
    }
    
    /**
     * Adds a less-than-or-equal condition: field <= value
     * 
     * @param field the field name
     * @param value the value to compare against
     * @return this Finder for method chaining
     */
    public Finder<T> lte(String field, Object value) {
        spec.where(new Lte(field, value));
        return this;
    }
    
    /**
     * Adds an IN condition: field IN (values)
     * 
     * @param field the field name
     * @param values the collection of values to match
     * @return this Finder for method chaining
     */
    public Finder<T> in(String field, Collection<?> values) {
        spec.where(new In(field, values));
        return this;
    }
    
    /**
     * Adds an IS NULL condition
     * 
     * @param field the field name
     * @return this Finder for method chaining
     */
    public Finder<T> isNull(String field) {
        spec.where(new IsNull(field, true));
        return this;
    }
    
    /**
     * Adds an IS NOT NULL condition
     * 
     * @param field the field name
     * @return this Finder for method chaining
     */
    public Finder<T> isNotNull(String field) {
        spec.where(new IsNull(field, false));
        return this;
    }
    
    /**
     * Adds a BETWEEN condition: field BETWEEN low AND high
     * 
     * @param field the field name
     * @param low the lower bound (inclusive)
     * @param high the upper bound (inclusive)
     * @return this Finder for method chaining
     */
    public Finder<T> between(String field, Object low, Object high) {
        spec.where(new Between(field, low, high));
        return this;
    }
    
    /**
     * Adds a custom condition.
     * 
     * @param condition the condition to add
     * @return this Finder for method chaining
     */
    public Finder<T> where(Condition condition) {
        spec.where(condition);
        return this;
    }
    
    // ==================== Sorting Methods ====================
    
    /**
     * Adds ascending sort order for the given field.
     * 
     * @param field the field to sort by
     * @return this Finder for method chaining
     */
    public Finder<T> orderAsc(String field) {
        spec.orderBy(Sort.asc(field));
        return this;
    }
    
    /**
     * Adds descending sort order for the given field.
     * 
     * @param field the field to sort by
     * @return this Finder for method chaining
     */
    public Finder<T> orderDesc(String field) {
        spec.orderBy(Sort.desc(field));
        return this;
    }
    
    /**
     * Adds a sort directive.
     * 
     * @param sort the sort to add
     * @return this Finder for method chaining
     */
    public Finder<T> orderBy(Sort sort) {
        spec.orderBy(sort);
        return this;
    }
    
    // ==================== Fetch Methods ====================
    
    /**
     * Specifies a relationship to eagerly fetch.
     * 
     * @param path the relationship path (e.g., "department")
     * @return this Finder for method chaining
     */
    public Finder<T> fetch(String path) {
        spec.fetch(path);
        return this;
    }
    
    // ==================== Pagination Methods ====================
    
    /**
     * Limits the maximum number of results.
     * 
     * @param n maximum number of results to return
     * @return this Finder for method chaining
     */
    public Finder<T> limit(int n) {
        spec.limit(n);
        return this;
    }
    
    /**
     * Skips the first n results.
     * 
     * @param n number of results to skip
     * @return this Finder for method chaining
     */
    public Finder<T> offset(int n) {
        spec.offset(n);
        return this;
    }
    
    // ==================== Execution Methods ====================
    
    /**
     * Executes the query and returns all matching entities.
     * 
     * @return list of matching entities
     */
    public List<T> list() {
        return session.findBy(spec);
    }
    
    /**
     * Executes the query and returns the first matching entity, if any.
     * 
     * @return Optional containing the first result, or empty if none found
     */
    public Optional<T> first() {
        spec.limit(1);
        List<T> results = session.findBy(spec);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    /**
     * Executes the query and returns exactly one result.
     * 
     * @return the single matching entity
     * @throws IllegalStateException if no result or more than one result found
     */
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
    
    /**
     * Executes the query and returns the single result if present.
     * 
     * @return Optional containing the result, or empty if none found
     * @throws IllegalStateException if more than one result found
     */
    public Optional<T> singleOptional() {
        spec.limit(2);
        List<T> results = session.findBy(spec);
        if (results.size() > 1) {
            throw new IllegalStateException("Expected at most one result, but found multiple");
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    /**
     * Returns the QuerySpec being built.
     * Can be used for advanced scenarios or debugging.
     * 
     * @return the QuerySpec
     */
    public QuerySpec<T> getSpec() {
        return spec;
    }
}
