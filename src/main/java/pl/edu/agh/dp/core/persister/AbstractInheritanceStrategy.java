package pl.edu.agh.dp.core.persister;

import lombok.NoArgsConstructor;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.finder.Condition;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.finder.Sort;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.AssociationMetadata;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
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

    protected void appendIdWhereClause(StringBuilder sql, List<Object> params, Collection<PropertyMetadata> idColumns, Object id) {
        if (idColumns.size() == 1) {
            PropertyMetadata pm = idColumns.iterator().next();
            sql.append(pm.getColumnName()).append(" = ?");
            params.add(pm.getType().cast(id));
        } else {
            // composite key
            int count = 0;
            for(PropertyMetadata pm : idColumns) {
                if(count > 0) sql.append(" AND ");
                sql.append(pm.getColumnName()).append(" = ?");
                Object val = ReflectionUtils.getFieldValue(id, pm.getName());
                params.add(val);
                count++;
            }
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
                            if (pm.getReferences() != null && Objects.equals(am.getField(), pm.getName())) {
                                Object field = ReflectionUtils.getFieldValue(value, pm.getReferencedName());
                                if (columns.contains(pm.getColumnName())) {
                                    Object oldValue = values.get(columns.indexOf(pm.getColumnName()));
                                    if (!Objects.equals(oldValue, field)) {
                                        throw new IntegrityException(
                                                "Values are not set correctly.\n" +
                                                "Class: " + meta.getEntityClass().getName() + "\n" +
                                                "Relationship: " + am.getField() + "\n" +
                                                "Values: [" + oldValue + ", " + field + "]"
                                        );
                                    }
                                } else {
                                    columns.add(pm.getColumnName());
                                    values.add(field);
                                }
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
                 && am.getType() == AssociationMetadata.Type.MANY_TO_MANY
                 && !((Collection) value).isEmpty())
            {
                List<String> targetRef = new ArrayList<>();
                List<String> currentRef = new ArrayList<>();

                String assFieldname = am.getField();
                EntityMetadata assTable = am.getAssociationTable();

                List<String> assColumns = new ArrayList<>();
                for (PropertyMetadata pm : am.getTargetJoinColumns()) {
                    currentRef.add(pm.getReferencedName());
                    assColumns.add(pm.getColumnName());
                }
                for (PropertyMetadata pm : am.getJoinColumns()) {
                    targetRef.add(pm.getReferencedName());
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
                jdbc.insert(assStmt, "", array.toArray());
            }
        }
    }

    protected void updateAssociationTables(JdbcExecutor jdbc, Object entity) {
        assert entityMetadata != null;

        // handle relationships
        for (AssociationMetadata am : entityMetadata.getAssociationMetadata().values()) {
            Object value = ReflectionUtils.getFieldValue(entity, am.getField());
            if (value != null
                && am.getType() == AssociationMetadata.Type.MANY_TO_MANY)
            {
                List<String> targetRef = new ArrayList<>();
                List<String> currentRef = new ArrayList<>();

                String assFieldname = am.getField();
                EntityMetadata assTable = am.getAssociationTable();

                List<String> assColumns = new ArrayList<>();
                for (PropertyMetadata pm : am.getTargetJoinColumns()) {
                    currentRef.add(pm.getReferencedName());
                    assColumns.add(pm.getColumnName());
                }
                for (PropertyMetadata pm : am.getJoinColumns()) {
                    targetRef.add(pm.getReferencedName());
                    assColumns.add(pm.getColumnName());
                }
                String assStmt = "INSERT INTO " + assTable.getTableName() +
                        " (" + String.join(", ", assColumns) + " )" +
                        " VALUES ";
                String assValuesStmt = "(" + "?,".repeat(assColumns.size() - 1) + "?)";

                System.out.println(assStmt);
                List<List<Object>> assAssValues = new ArrayList<>();
                assAssValues.add(new ArrayList<>());
                for (String fieldName : currentRef) {
                    System.out.println(fieldName + " from " + entity.getClass().getSimpleName());
                    Object field = ReflectionUtils.getFieldValue(entity, fieldName);
                    assAssValues.get(0).add(field);
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

                StringBuilder deleteStmt = new StringBuilder("DELETE FROM " + assTable.getTableName() + " WHERE");
                for (int i = 0; i < currentRef.size(); i++) {
                    String colName = assColumns.get(i);
                    Object colValue = assAssValues.get(0).get(i);
                    deleteStmt.append(" ").append(assTable.getTableName()).append(".").append(colName).append(" = ").append(colValue);
                }
                System.out.println(deleteStmt.toString());
                jdbc.update(deleteStmt.toString());
                // only delete if empty
                if (((Collection) value).isEmpty()) {
                    return;
                }

                // remove first example
                assAssValues.remove(0);
                // flatten array
                assStmt += String.join(", ", java.util.Collections.nCopies(assAssValues.size(), assValuesStmt)) +";";
                List<Object> array = new ArrayList<>();
                for (List<Object> el : assAssValues) {
                    array.addAll(el);
                }
                jdbc.insert(assStmt, "", array.toArray());
            }
        }
    }

    protected Set<String> getProvidedIds(Object entity) {
        assert entityMetadata != null;
        Set<String> idProvided = new HashSet<>();
        for (PropertyMetadata pm : entityMetadata.getIdColumns().values()) {
            Object value = ReflectionUtils.getFieldValue(entity, pm.getName());
            if (value != null) {
                idProvided.add(pm.getName());
            } else {
                if (!pm.isNullable() && !pm.isAutoIncrement()) {
                    throw new IntegrityException(
                            "In entity: " + entity + "\n" +
                            "Field: '" + pm.getName() + "' is not nullable"
                    );
                }
            }
        }
        return idProvided;
    }

    protected String getIdNameAndCheckCompositeKey(Set<String> idProvided, List<PropertyMetadata> idColumns) {
        boolean isCompositeKey = idColumns.size() > 1;
        if (isCompositeKey) {
            if (idProvided.size() != idColumns.size()) {
                List<String> compositeKey = new ArrayList<>();
                List<String> missingIds = new ArrayList<>();
                for (PropertyMetadata pm : idColumns) {
                    if (!idProvided.contains(pm.getName())) {
                        missingIds.add(pm.getName());
                    }
                    compositeKey.add(pm.getName());
                }
                throw new IntegrityException(
                        "Not all identifiers are set. You must set all ids in composite key.\n" +
                                "Composite key: (" + String.join(", ", compositeKey) + ")\n" +
                                "Missing/unset fields: (" + String.join(", ", missingIds) + ")"
                );
            }
        } else {
            if (!idProvided.isEmpty() && idColumns.get(0).isAutoIncrement()) {
                throw new IntegrityException(
                        "You cannot set an id that is auto increment.\n" +
                                "Tried to set: '" + idColumns.get(0).getName() + "' that is an auto incremented id.\n" +
                                "Remove the auto increment or don't set it.");
            } else if (idProvided.isEmpty() && !idColumns.get(0).isAutoIncrement()) {
                throw new IntegrityException(
                        "You must set an id that is not auto increment.\n" +
                                "Field: '" + idColumns.get(0).getName() + "' is not set.\n" +
                                "Add auto increment or set this field.");
            }
        }
        return isCompositeKey ? "" : idColumns.iterator().next().getColumnName();
    }

    protected String buildWhereClause(EntityMetadata meta) {
        Collection<PropertyMetadata> idColumns = meta.getIdColumns().values();
        List<String> conditions = new ArrayList<>();

        for (PropertyMetadata idProp : idColumns) {
            conditions.add(idProp.getColumnName() + " = ?");
        }

        return String.join(" AND ", conditions);
    }

    protected String resolveColumnName(String fieldName, EntityMetadata meta) {
        PropertyMetadata pm = meta.getProperties().get(fieldName);
        if (pm != null) {
            return pm.getColumnName();
        }
        pm = meta.getIdColumns().get(fieldName);
        if (pm != null) {
            return pm.getColumnName();
        }
        pm = meta.getFkColumns().get(fieldName);
        if (pm != null) {
            return pm.getColumnName();
        }
        // If not found, assume it's already a column name
        return fieldName;
    }

    protected <T> String buildQuerySpecWhereClause(QuerySpec<T> querySpec, String tableName, List<Object> params) {
        if (!querySpec.hasConditions()) {
            return "";
        }
        
        List<String> sqlConditions = new ArrayList<>();
        for (Condition condition : querySpec.getConditions()) {
            String columnName = resolveColumnName(condition.getField(), entityMetadata);
            // Generate SQL with proper table alias and column name
            String sql = condition.toSql(tableName)
                    .replace(tableName + "." + condition.getField(), 
                             tableName + "." + columnName);
            sqlConditions.add(sql);
            params.addAll(condition.getParams());
        }
        
        return String.join(" AND ", sqlConditions);
    }

    protected <T> String buildQuerySpecOrderByClause(QuerySpec<T> querySpec, String tableName) {
        if (!querySpec.hasSorting()) {
            return "";
        }
        
        return querySpec.getSortings().stream()
                .map(sort -> {
                    String columnName = resolveColumnName(sort.getField(), entityMetadata);
                    return tableName + "." + columnName + " " + sort.getDirection().name();
                })
                .collect(Collectors.joining(", "));
    }

    protected <T> String buildQuerySpecLimitOffsetClause(QuerySpec<T> querySpec) {
        StringBuilder sb = new StringBuilder();
        if (querySpec.hasLimit()) {
            sb.append(" LIMIT ").append(querySpec.getLimitValue());
        }
        if (querySpec.hasOffset()) {
            sb.append(" OFFSET ").append(querySpec.getOffsetValue());
        }
        return sb.toString();
    }

    protected Object[] prepareIdParams(Object idValue) {
        if (idValue instanceof Map) {
            Map<String, Object> compositeId = (Map<String, Object>) idValue;
            return compositeId.values().toArray();
        } else {
            return new Object[] { idValue };
        }
    }

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

    protected static Object castSqlValueToJava(Class<?> targetType, Object sqlValue) {
        if (sqlValue == null) {
            return null;
        }

        // Already correct type
        if (targetType.isInstance(sqlValue)) {
            return sqlValue;
        }

        // --- Date & Time ---
        if (targetType == LocalDate.class && sqlValue instanceof java.sql.Date d) {
            return d.toLocalDate();
        }

        if (targetType == LocalTime.class && sqlValue instanceof java.sql.Time t) {
            return t.toLocalTime();
        }

        if (targetType == LocalDateTime.class && sqlValue instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }

        if (targetType == OffsetDateTime.class && sqlValue instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }

        // --- Numeric ---
        if (targetType == Integer.class && sqlValue instanceof Number n) {
            return n.intValue();
        }

        if (targetType == Long.class && sqlValue instanceof Number n) {
            return n.longValue();
        }

        if (targetType == Short.class && sqlValue instanceof Number n) {
            return n.shortValue();
        }

        if (targetType == Float.class && sqlValue instanceof Number n) {
            return n.floatValue();
        }

        if (targetType == Double.class && sqlValue instanceof Number n) {
            return n.doubleValue();
        }

        if (targetType == BigDecimal.class && sqlValue instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }

        // --- Boolean ---
        if (targetType == Boolean.class && sqlValue instanceof Boolean b) {
            return b;
        }

        // --- UUID ---
        if (targetType == UUID.class && sqlValue instanceof java.util.UUID u) {
            return u;
        }

        if (targetType == UUID.class && sqlValue instanceof String s) {
            return UUID.fromString(s);
        }

        // --- String fallback ---
        if (targetType == String.class) {
            return sqlValue.toString();
        }

        throw new IllegalArgumentException(
                "Cannot cast SQL value of type " + sqlValue.getClass().getName() +
                        " to Java type " + targetType.getName()
        );
    }

}