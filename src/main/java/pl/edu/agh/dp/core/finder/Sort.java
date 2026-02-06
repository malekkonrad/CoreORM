package pl.edu.agh.dp.core.finder;

import lombok.Getter;

/**
 * Represents a sorting directive for query results.
 */
@Getter
public class Sort {
    
    /**
     * Sorting direction
     */
    public enum Direction {
        ASC, DESC
    }
    
    private final String field;
    private final Direction direction;
    
    private Sort(String field, Direction direction) {
        this.field = field;
        this.direction = direction;
    }
    
    /**
     * Creates ascending sort order for the given field.
     * 
     * @param field the field name to sort by
     * @return Sort instance for ascending order
     */
    public static Sort asc(String field) {
        return new Sort(field, Direction.ASC);
    }
    
    /**
     * Creates descending sort order for the given field.
     * 
     * @param field the field name to sort by
     * @return Sort instance for descending order
     */
    public static Sort desc(String field) {
        return new Sort(field, Direction.DESC);
    }
    
    /**
     * Generates the SQL ORDER BY clause fragment for this sort.
     * 
     * @param tableAlias the alias to prefix column names with
     * @return SQL fragment like "t.column_name ASC"
     */
    public String toSql(String tableAlias) {
        return tableAlias + "." + field + " " + direction.name();
    }
}
