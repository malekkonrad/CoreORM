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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@NoArgsConstructor(force = true)
public abstract class AbstractInheritanceStrategy implements InheritanceStrategy {

    protected final EntityMetadata entityMetadata;

    protected AbstractInheritanceStrategy(EntityMetadata metadata) {
        this.entityMetadata = metadata;
    }

    protected Object getValueFromResultSet(ResultSet rs, String columnName, Class<?> type) throws SQLException {
        if (type == Long.class || type == long.class) {
            long val = rs.getLong(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == Integer.class || type == int.class) {
            int val = rs.getInt(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == Short.class || type == short.class) {
            short val = rs.getShort(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == Float.class || type == float.class) {
            float val = rs.getFloat(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == String.class) {
            return rs.getString(columnName);
        } else if (type == Double.class || type == double.class) {
            double val = rs.getDouble(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == Boolean.class || type == boolean.class) {
            boolean val = rs.getBoolean(columnName);
            return rs.wasNull() ? null : val;
        } else if (type == LocalTime.class) {
            java.sql.Time sqlTime = rs.getTime(columnName);
            return sqlTime != null ? sqlTime.toLocalTime() : null;
        } else if (type == LocalDate.class) {
            java.sql.Date sqlDate = rs.getDate(columnName);
            return sqlDate != null ? sqlDate.toLocalDate() : null;
        } else if (type == LocalDateTime.class) {
            java.sql.Timestamp sqlTimestamp = rs.getTimestamp(columnName);
            return sqlTimestamp != null ? sqlTimestamp.toLocalDateTime() : null;
        } else if (type == OffsetDateTime.class) {
            Object obj = rs.getObject(columnName);
            if (obj == null) return null;
            if (obj instanceof OffsetDateTime) return obj;
            if (obj instanceof java.sql.Timestamp) {
                return ((java.sql.Timestamp) obj).toLocalDateTime().atOffset(java.time.ZoneOffset.UTC);
            }
        }

        return rs.getObject(columnName);
    }

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