package pl.edu.agh.dp.core.finder;

import lombok.Getter;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds SQL query from a QuerySpec.
 * Handles conversion of field names to column names using entity metadata.
 */
public class QueryBuilder {
    
    private final EntityMetadata metadata;
    private final String tableAlias;
    
    @Getter
    private final List<Object> parameters = new ArrayList<>();
    
    /**
     * Creates a QueryBuilder for the given entity metadata.
     * 
     * @param metadata the entity metadata for column mapping
     */
    public QueryBuilder(EntityMetadata metadata) {
        this.metadata = metadata;
        this.tableAlias = metadata.getTableName();
    }
    
    /**
     * Creates a QueryBuilder with custom table alias.
     * 
     * @param metadata the entity metadata for column mapping
     * @param tableAlias the alias to use in SQL
     */
    public QueryBuilder(EntityMetadata metadata, String tableAlias) {
        this.metadata = metadata;
        this.tableAlias = tableAlias;
    }
    
    /**
     * Builds WHERE clause from QuerySpec conditions.
     * 
     * @param spec the query specification
     * @return SQL WHERE clause (without WHERE keyword), or empty string if no conditions
     */
    public String buildWhereClause(QuerySpec<?> spec) {
        if (!spec.hasConditions()) {
            return "";
        }
        
        List<String> sqlConditions = new ArrayList<>();
        for (Condition condition : spec.getConditions()) {
            // Convert field name to column name
            String columnName = resolveColumnName(condition.getField());
            String sql = condition.toSql(tableAlias)
                    .replace(tableAlias + "." + condition.getField(), 
                             tableAlias + "." + columnName);
            sqlConditions.add(sql);
            parameters.addAll(condition.getParams());
        }
        
        return String.join(" AND ", sqlConditions);
    }
    
    /**
     * Builds ORDER BY clause from QuerySpec sorting.
     * 
     * @param spec the query specification
     * @return SQL ORDER BY clause (without ORDER BY keywords), or empty string if no sorting
     */
    public String buildOrderByClause(QuerySpec<?> spec) {
        if (!spec.hasSorting()) {
            return "";
        }
        
        return spec.getSortings().stream()
                .map(sort -> {
                    String columnName = resolveColumnName(sort.getField());
                    return tableAlias + "." + columnName + " " + sort.getDirection().name();
                })
                .collect(Collectors.joining(", "));
    }
    
    /**
     * Builds LIMIT clause.
     * 
     * @param spec the query specification
     * @return SQL LIMIT clause, or empty string if no limit
     */
    public String buildLimitClause(QuerySpec<?> spec) {
        if (!spec.hasLimit()) {
            return "";
        }
        return "LIMIT " + spec.getLimitValue();
    }
    
    /**
     * Builds OFFSET clause.
     * 
     * @param spec the query specification
     * @return SQL OFFSET clause, or empty string if no offset
     */
    public String buildOffsetClause(QuerySpec<?> spec) {
        if (!spec.hasOffset()) {
            return "";
        }
        return "OFFSET " + spec.getOffsetValue();
    }
    
    /**
     * Resolves field name to database column name.
     * 
     * @param fieldName the Java field name
     * @return the database column name
     */
    public String resolveColumnName(String fieldName) {
        // Check in properties
        PropertyMetadata pm = metadata.getProperties().get(fieldName);
        if (pm != null) {
            return pm.getColumnName();
        }
        
        // Check in id columns
        pm = metadata.getIdColumns().get(fieldName);
        if (pm != null) {
            return pm.getColumnName();
        }
        
        // Check in FK columns
        pm = metadata.getFkColumns().get(fieldName);
        if (pm != null) {
            return pm.getColumnName();
        }
        
        // If not found, assume it's already a column name
        return fieldName;
    }
    
    /**
     * Gets the table alias being used.
     * 
     * @return the table alias
     */
    public String getTableAlias() {
        return tableAlias;
    }
    
    /**
     * Clears the accumulated parameters.
     */
    public void clearParameters() {
        parameters.clear();
    }
}
