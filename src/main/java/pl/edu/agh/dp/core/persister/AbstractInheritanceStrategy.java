package pl.edu.agh.dp.core.persister;

import lombok.NoArgsConstructor;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.AssociationMetadata;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;

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
            return ReflectionUtils.getFieldValue(entity, idProp.getName());
        } else {
            // Composite key - zwróć mapę
            Map<String, Object> compositeId = new HashMap<>();
            for (PropertyMetadata idProp : idColumns) {
                Object value = ReflectionUtils.getFieldValue(entity, idProp.getName());
                compositeId.put(idProp.getColumnName(), value);
            }
            return compositeId;
        }
    }

    protected void fillRelationshipData(Object entity, EntityMetadata meta, List<String> columns, List<Object> values) {
        assert entityMetadata != null;

        // handle relationships
        for (AssociationMetadata am : meta.getAssociationMetadata().values()) {
            Object value = ReflectionUtils.getFieldValue(entity, am.getField());
            if (value != null && meta.getFkColumns().containsKey(am.getField())) { // check if value belongs to the sql table
                // set fk id
                if (am.getHasForeignKey()) {
                    if (am.getType() == AssociationMetadata.Type.MANY_TO_MANY) {
                        // Many to many is handled separately
                        continue;
                    } else {
                        for (PropertyMetadata pm : am.getJoinColumns()) {
                            if (pm.getReferences() != null) {
                                Object field = ReflectionUtils.getFieldValue(value, pm.getReferencedName());
                                columns.add(pm.getColumnName());
                                values.add(field);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void insertAssociationTables(JdbcExecutor jdbc, Object entity) {
        assert entityMetadata != null;

        // handle relationships
        for (AssociationMetadata am : entityMetadata.getAssociationMetadata().values()) {
            Object value = ReflectionUtils.getFieldValue(entity, am.getField());
            if (value != null
                 && am.getHasForeignKey()
                 && am.getType() == AssociationMetadata.Type.MANY_TO_MANY)
            {
                List<String> targetRef = new ArrayList<>();
                List<String> currentRef = new ArrayList<>();

                String assFieldname = am.getField();
                EntityMetadata assTable = am.getAssociationTable();

                List<String> assColumns = new ArrayList<>();
                for (PropertyMetadata pm : am.getTargetJoinColumns()) {
                    targetRef.add(pm.getReferencedName());
                    assColumns.add(pm.getColumnName());
                }
                for (PropertyMetadata pm : am.getJoinColumns()) {
                    currentRef.add(pm.getReferencedName());
                    assColumns.add(pm.getColumnName());
                }
                String assStmt = "INSERT INTO " + assTable.getTableName() +
                        " (" + String.join(", ", assColumns) + " )" +
                        " VALUES ";
                String assValuesStmt = "(" + "?,".repeat(assColumns.size() - 1) + "?)";

                System.out.println(assStmt);
                List<List<Object>> assAssValues = new ArrayList<>();
                for (String fieldName : currentRef) {
                    System.out.println(fieldName + " from " + entity.getClass().getSimpleName());
                    Object field = ReflectionUtils.getFieldValue(entity, fieldName);
                    assAssValues.add(new ArrayList<>(){{add(field);}});
                }
                Collection<?> assField = (Collection<?>) ReflectionUtils.getFieldValue(entity, assFieldname);
                for (Object relationshipEntity : assField) {
                    // first is the example, copy it and fill with the other values
                    assAssValues.add(new ArrayList<>(){{addAll(assAssValues.get(0));}});
                    List<Object> assValues = assAssValues.get(assAssValues.size() - 1);
                    for (String fieldName : targetRef) {
                        System.out.println(fieldName + " from " + relationshipEntity.getClass().getSimpleName());
                        Object field = ReflectionUtils.getFieldValue(relationshipEntity, fieldName);
                        assValues.add(field);
                    }
                }
                // remove first example
                assAssValues.remove(0);
                // flatten array
                assStmt += String.join(", ", java.util.Collections.nCopies(assAssValues.size(), assValuesStmt)) +";";
                List<Object> array = new ArrayList<>();
                for (List<Object> el : assAssValues) {
                    array.addAll(el);
                }
                jdbc.insert(assStmt, array.toArray());
            }
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