package pl.edu.agh.dp.core.persister;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class EntityPersisterImpl implements EntityPersister {

    private EntityMetadata metadata;

    private InheritanceStrategy inheritanceStrategy;

    public EntityPersisterImpl(EntityMetadata metadata) {
        this.metadata = metadata;
        this.inheritanceStrategy = InheritanceStrategyFactory.build(metadata.getInheritanceMetadata().getType(), metadata);
    }

    @Override
    public Object findById(Object id, Session session) {
        return  inheritanceStrategy.findById(id, session );

//        try {
//            JdbcExecutor jdbc = session.getJdbcExecutor();
//
//            List<PropertyMetadata> idColumns = metadata.getIdColumns();
//
//            List<String> columns = new ArrayList<>();
//
//            for (PropertyMetadata pm : metadata.getProperties()) {
//                columns.add(pm.getColumnName());
//            }
//
//            StringBuilder sql = new StringBuilder();
//            sql.append("SELECT ")
//                    .append(String.join(", ", columns))
//                    .append(" FROM ")
//                    .append(metadata.getTableName())
//                    .append(" WHERE ");
//
//            Object[] params = new Object[idColumns.size()];
//            if (idColumns.size() == 1) {
//                PropertyMetadata pm = idColumns.get(0);
//                sql.append(pm.getColumnName());
//                sql.append(" = ?");
//                try {
//                    params[0] = pm.getType().cast(id);
//                } catch (ClassCastException e) {
//                    throw new IntegrityException(
//                            "Type mismatch or unable to cast. Field: '" + pm.getName() + "' is: '" + pm.getType() + "', but got: '" + id.getClass() + "'");
//                }
//            }
//            else {
//                for (int i = 0; i < params.length; i++) {
//                    PropertyMetadata pm = idColumns.get(i);
//                    sql.append(pm.getColumnName());
//                    sql.append(" = ?");
//                    if (i < params.length - 1) {
//                        sql.append(" AND ");
//                    }
//                    try {
//                        ReflectionUtils.findField(id.getClass(), pm.getName());
//                    } catch (NoSuchFieldException e) {
//                        List<String> fields = new ArrayList<>();
//                        List<String> types = new ArrayList<>();
//                        for (PropertyMetadata pmeta : idColumns) {
//                            fields.add(pmeta.getName());
//                            types.add(pmeta.getType().getName() + " " + pmeta.getName() + ";");
//                        }
//                        throw new IntegrityException(
//                                "Composite key for entity: '" + metadata.getEntityClass().getName() + "' should be provided.\n" +
//                                "Composite key: (" + String.join(", ", fields) + ")\n" +
//                                "'Id' should have the aforementioned fields to function properly.\n" +
//                                "Provided: '" + id.toString() + "'\n" +
//                                "Example:\n" +
//                                "class " + metadata.getEntityClass().getSimpleName() + "Id {\n\t" +
//                                String.join("\n\t", fields) + "\n}"
//                        );
//                    }
//                    Object val = ReflectionUtils.getFieldValue(id, pm.getName());
//                    try {
//                        params[i] = pm.getType().cast(val);
//                    } catch (ClassCastException e) {
//                        throw new IntegrityException(
//                                "Type mismatch or unable to cast. Field: '" + pm.getName() + "' is: '" + pm.getType() + "', but got: '" + id.getClass() + "'");
//                    }
//                }
//            }
//
//        return jdbc.queryOne(sql.toString(), this::mapEntity, params)
//            .orElse(null);
//
//        } catch (Exception e) {
//            throw new RuntimeException("Error finding entity with id = " + id, e);
//        }
    }

    @Override
    public void insert(Object entity, Session session) {
        System.out.println("----------------------------------------");
        inheritanceStrategy.insert(entity, session);
        System.out.println("----------------------------------------");

//
//        List<String> columns = new ArrayList<>();
//        List<Object> values = new ArrayList<>();
//
//        List<PropertyMetadata> idColumns = metadata.getIdColumns();
//        boolean isCompositeKey = idColumns.size() > 1;
//
//        Map<String, Boolean> idProvided = new HashMap<>();
//        for (PropertyMetadata pm : metadata.getProperties()) {
//            columns.add(pm.getColumnName());
//            Object value = ReflectionUtils.getFieldValue(entity, pm.getName());
//            values.add(value);
//            if (pm.isId()) {
//                idProvided.put(pm.getName(), true);
//            }
//        }
//
//        if (isCompositeKey) {
//            if (idProvided.size() != idColumns.size()) {
//                List<String> compositeKey = new ArrayList<>();
//                List<String> missingIds = new ArrayList<>();
//                for (PropertyMetadata pm : idColumns) {
//                    if (!idProvided.containsKey(pm.getName())) {
//                        missingIds.add(pm.getName());
//                    }
//                    compositeKey.add(pm.getName());
//                }
//                throw new IntegrityException(
//                        "Not all identifiers are set. You must set all ids in composite key.\n" +
//                        "Composite key: (" + String.join(", ", compositeKey) + ")\n" +
//                        "Missing/unset fields: (" + String.join(", ", missingIds) + ")"
//                );
//            }
//        } else {
//            if (!idProvided.isEmpty() && idColumns.get(0).isAutoIncrement()) {
//                throw new IntegrityException(
//                        "You cannot set an id that is auto increment.\n" +
//                        "Tried to set: '" + idColumns.get(0).getName() + "' that is an auto incremented id.\n" +
//                        "Remove the auto increment or don't set it.");
//            } else if (idProvided.isEmpty() && !idColumns.get(0).isAutoIncrement()) {
//                throw new IntegrityException(
//                        "You must set an id that is not auto increment.\n" +
//                        "Field: '" + idColumns.get(0).getName() + "' is not set.\n" +
//                        "Add auto increment or set this field.");
//            }
//        }
//
//        StringBuilder sql = new StringBuilder();
//        sql.append("INSERT INTO ")
//                .append(metadata.getTableName())
//                .append(" (")
//                .append(String.join(", ", columns))
//                .append(") VALUES (")
//                .append("?,".repeat(values.size()));
//        sql.deleteCharAt(sql.length() - 1);
//        sql.append(")");
//
//        try {
//            JdbcExecutor jdbc = session.getJdbcExecutor();
//            // FIXME does it work for composite keys?
//            Long generatedId = jdbc.insert(sql.toString(), values.toArray());
//            if (!isCompositeKey) {
//                ReflectionUtils.setFieldValue(entity, idColumns.get(0).getName(), generatedId);
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("Error inserting entity " + entity, e);
//        }
    }

    @Override
    public void update(Object entity, Session session) {

    }

    @Override
    public void delete(Object entity, Session session) {

    }

    private Object mapEntity(ResultSet rs) throws SQLException {
        try {
            Object entity = metadata.getEntityClass().getDeclaredConstructor().newInstance();

            for (PropertyMetadata prop : metadata.getProperties()) {
                Object value = rs.getObject(prop.getColumnName());

                // Konwersja Integer -> Long dla wszystkich pól Long
                if (value instanceof Integer && prop.getType() == Long.class) {
                    value = ((Integer) value).longValue();
                }

                ReflectionUtils.setFieldValue(entity, prop.getName(), value);
            }

            return entity;
        } catch (Exception e) {
            throw new SQLException("Failed to map entity: " + metadata.getEntityClass().getName(), e);
        }
    }
}
