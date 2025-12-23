package pl.edu.agh.dp.core.persister;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import pl.edu.agh.dp.core.jdbc.Dialect;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

@NoArgsConstructor(force = true)
public abstract class AbstractInheritanceStrategy implements InheritanceStrategy {

    protected final EntityMetadata entityMetadata;

    protected AbstractInheritanceStrategy(EntityMetadata metadata) {
        this.entityMetadata = metadata;
    }

//    protected String buildInsertReturning(String tableName,
//                                          List<String> columns,
//                                          String idColumnName) {
//        String columnList = String.join(", ", columns);
//        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
//        return "INSERT INTO " + tableName +
//                " (" + columnList + ") VALUES (" + placeholders + ") RETURNING " + idColumnName;
//    }
//
//    protected String buildUpdate(String tableName,
//                                 List<String> columns,
//                                 String idColumnName) {
//        String setClause = String.join(", ",
//                columns.stream().map(c -> c + " = ?").toList());
//        return "UPDATE " + tableName +
//                " SET " + setClause +
//                " WHERE " + idColumnName + " = ?";
//    }
//
//    protected String buildDelete(String tableName, String idColumnName) {
//        return "DELETE FROM " + tableName + " WHERE " + idColumnName + " = ?";
//    }

    /**
     * Z ResultSet robi prostą mapę nazwa_kolumny -> wartość.
     */
    protected Map<String, Object> readRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        for (int i = 1; i <= cols; i++) {
            String name = meta.getColumnLabel(i);
            Object value = rs.getObject(i);
            row.put(name, value);
        }
        return row;
    }

    protected Object getValueFromResultSet(ResultSet rs, String columnName, Class<?> type) throws SQLException {
        if (type == Long.class || type == long.class) {
            long val = rs.getLong(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == Integer.class || type == int.class) {
            int val = rs.getInt(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == String.class) {
            return rs.getString(columnName);
        } else if (type == Double.class || type == double.class) {
            double val = rs.getDouble(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == Boolean.class || type == boolean.class) {
            boolean val = rs.getBoolean(columnName);
            return rs.wasNull() ? null : val;
        }

        return rs.getObject(columnName);
    }


    /**
     * Helper: Pobiera wartość ID z encji
     */
    protected Object getIdValue(Object entity) {
        Collection<PropertyMetadata> idColumns = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().values();

        if (idColumns.size() == 1) {
            PropertyMetadata idProp = idColumns.iterator().next();
            return pl.edu.agh.dp.core.util.ReflectionUtils.getFieldValue(entity, idProp.getName());
        } else {
            // Composite key - zwróć mapę
            Map<String, Object> compositeId = new HashMap<>();
            for (PropertyMetadata idProp : idColumns) {
                Object value = pl.edu.agh.dp.core.util.ReflectionUtils.getFieldValue(entity, idProp.getName());
                compositeId.put(idProp.getColumnName(), value);
            }
            return compositeId;
        }
    }

    /**
     * Helper: Buduje WHERE clause dla ID
     */
    protected String buildWhereClause(EntityMetadata meta) {
        Collection<PropertyMetadata> idColumns = meta.getIdColumns().values();
        List<String> conditions = new ArrayList<>();

        for (PropertyMetadata idProp : idColumns) {
            conditions.add(idProp.getColumnName() + " = ?");
        }

        return String.join(" AND ", conditions);
    }

    /**
     * Helper: Przygotowuje parametry ID dla prepared statement
     */
    protected Object[] prepareIdParams(Object idValue) {
        if (idValue instanceof Map) {
            Map<String, Object> compositeId = (Map<String, Object>) idValue;
            return compositeId.values().toArray();
        } else {
            return new Object[] { idValue };
        }
    }

    /**
     * Helper: Sprawdza czy pole należy do danej klasy
     */
    protected boolean fieldBelongsToClass(PropertyMetadata prop, Class<?> targetClass) {

        if (prop.getColumnName().equals("DTYPE")){
            return false;
        }
        try {
            targetClass.getDeclaredField(prop.getName());
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}