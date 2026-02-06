package pl.edu.agh.dp.core.finder;

import java.util.List;

/**
 * Base interface for query conditions.
 * Each condition generates a SQL fragment with placeholders and provides parameter values.
 */
public interface Condition {
    
    /**
     * Generates the SQL fragment for this condition.
     * Uses the provided table alias for column references.
     * 
     * @param tableAlias the alias to prefix column names with (e.g., "t" produces "t.column_name")
     * @return SQL fragment with ? placeholders
     */
    String toSql(String tableAlias);
    
    /**
     * Returns the parameters needed for this condition's placeholders.
     * 
     * @return list of parameter values in order
     */
    List<Object> getParams();
    
    /**
     * Returns the field name this condition applies to.
     * 
     * @return field name
     */
    String getField();
}
