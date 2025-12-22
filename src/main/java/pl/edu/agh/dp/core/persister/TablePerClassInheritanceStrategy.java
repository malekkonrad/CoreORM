package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class TablePerClassInheritanceStrategy extends AbstractInheritanceStrategy{

    public TablePerClassInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
    }


    @Override
    public String create(JdbcExecutor jdbcExecutor) {
        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE ").append(this.entityMetadata.getTableName()).append(" (\n");

        List<String> columnDefs = new ArrayList<>();

        for (PropertyMetadata col : this.entityMetadata.getColumnsForConcreteTable()) {
            columnDefs.add("    " + col.getColumnName() + " " + col.getSqlType());
        }

        sb.append(String.join(",\n", columnDefs));

        // PRIMARY KEYS
        List<String> idColumns = new ArrayList<>();
        Collection<PropertyMetadata> rootIds = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().values();
        for (PropertyMetadata idProp : rootIds) {
            idColumns.add(idProp.getColumnName());
        }
        // FIXME IN SQLITE it cant be added
        sb.append(",\n    PRIMARY KEY (")
                .append(String.join(", ", idColumns))
                .append(")");

        sb.append("\n);");

        return sb.toString();
    }

    @Override
    public Object insert(Object entity, Session session) {

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        assert entityMetadata != null;
        Collection<PropertyMetadata> idColumns = entityMetadata.getIdColumns().values();
        boolean isCompositeKey = idColumns.size() > 1;

        Map<String, Boolean> idProvided = new HashMap<>();
        for (PropertyMetadata pm : entityMetadata.getColumnsForConcreteTable()) {

            if (pm.isId() && pm.isAutoIncrement()) {
                continue; // Nie dodawaj tej kolumny do SQL, baza sama ją wypełni
            }

            columns.add(pm.getColumnName());
            Object value = ReflectionUtils.getFieldValue(entity, pm.getName());
            values.add(value);
            if (pm.isId()) {
                idProvided.put(pm.getName(), true);
            }
        }


        // FIXME nie działa @Mateusz
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

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ")
                .append(entityMetadata.getTableName())
                .append(" (")
                .append(String.join(", ", columns))
                .append(") VALUES (")
                .append("?,".repeat(values.size()));
        sql.deleteCharAt(sql.length() - 1);
        sql.append(")");

        Long generatedId;
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            generatedId = jdbc.insert(sql.toString(), values.toArray());
            System.out.println("Generated ID: " + generatedId);
            // Ustaw wygenerowane ID
            // FIXME important!!!!!!!!!!!!!!!!!!!!!!!!
            PropertyMetadata idProp = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().get("id");
            if (idProp.isAutoIncrement()) {
                System.out.println("seting id in " + entity.toString()+ " " + idProp.getColumnName());
                ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException("Error inserting entity " + entity, e);
        }
        return generatedId;
    }


    @Override
    public void update(Object entity, Session session) {
        String tableName = entityMetadata.getTableName();

        List<String> setColumns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Wszystkie pola z hierarchii (pomiń ID)
        for (PropertyMetadata prop : entityMetadata.getColumnsForConcreteTable()) {
            if (prop.isId()) {
                continue; // ID nie jest aktualizowane
            }

            setColumns.add(prop.getColumnName() + " = ?");
            Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
            values.add(value);
        }

        EntityMetadata rootMetadata = entityMetadata.getInheritanceMetadata().getRootClass();

        // WHERE clause
        Object idValue = getIdValue(entity);
        String whereClause = buildWhereClause(rootMetadata);
        Object[] idParams = prepareIdParams(idValue);

        // Połącz parametry
        List<Object> allParams = new ArrayList<>(values);
        allParams.addAll(Arrays.asList(idParams));

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(tableName)
                .append(" SET ").append(String.join(", ", setColumns))
                .append(" WHERE ").append(whereClause);

        System.out.println("TablePerClass UPDATE SQL: " + sql);
        System.out.println("Values: " + allParams);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            jdbc.update(sql.toString(), allParams.toArray());
        } catch (Exception e) {
            throw new RuntimeException("Error updating entity " + entity, e);
        }
    }

    @Override
    public void delete(Object entity, Session session) {
        String tableName = entityMetadata.getTableName();

        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();

        Object idValue = getIdValue(entity);
        String whereClause = buildWhereClause(rootMetadata);
        Object[] idParams = prepareIdParams(idValue);

        String sql = "DELETE FROM " + tableName + " WHERE " + whereClause;

        System.out.println("TablePerClass DELETE SQL: " + sql);
        System.out.println("ID: " + idValue);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            jdbc.update(sql, idParams);
        } catch (Exception e) {
            throw new RuntimeException("Error deleting entity " + entity, e);
        }
    }

    @Override
    public Object findById(Object id, Session session) {

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            assert entityMetadata != null;
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
            Collection<PropertyMetadata> idColumns = root.getIdColumns().values();
            System.out.println("ID columns: " + idColumns);

            List<String> columns = new ArrayList<>();
            for (PropertyMetadata pm : entityMetadata.getColumnsForConcreteTable()) {
                columns.add(pm.getColumnName());
            }

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append(String.join(", ", columns))
                    .append(" FROM ")
                    .append(entityMetadata.getTableName())
                    .append(" WHERE ");

            Object[] params = new Object[idColumns.size()];
            if (idColumns.size() == 1) {
                PropertyMetadata pm = idColumns.iterator().next();
                sql.append(pm.getColumnName());
                sql.append(" = ?");
                try {
                    params[0] = pm.getType().cast(id);
                } catch (ClassCastException e) {
                    throw new IntegrityException(
                            "Type mismatch or unable to cast. Field: '" + pm.getName() + "' is: '" + pm.getType() + "', but got: '" + id.getClass() + "'");
                }
            }
            else {
                for (int i = 0; i < params.length; i++) {
                    PropertyMetadata pm = idColumns.iterator().next();
                    sql.append(pm.getColumnName());
                    sql.append(" = ?");
                    if (i < params.length - 1) {
                        sql.append(" AND ");
                    }
                    try {
                        ReflectionUtils.findField(id.getClass(), pm.getName());
                    } catch (NoSuchFieldException e) {
                        List<String> fields = new ArrayList<>();
                        List<String> types = new ArrayList<>();
                        for (PropertyMetadata pmeta : idColumns) {
                            fields.add(pmeta.getName());
                            types.add(pmeta.getType().getName() + " " + pmeta.getName() + ";");
                        }
                        throw new IntegrityException(
                                "Composite key for entity: '" + entityMetadata.getEntityClass().getName() + "' should be provided.\n" +
                                "Composite key: (" + String.join(", ", fields) + ")\n" +
                                "'Id' should have the aforementioned fields to function properly.\n" +
                                "Provided: '" + id.toString() + "'\n" +
                                "Example:\n" +
                                "class " + entityMetadata.getEntityClass().getSimpleName() + "Id {\n\t" +
                                String.join("\n\t", fields) + "\n}"
                        );
                    }
                    Object val = ReflectionUtils.getFieldValue(id, pm.getName());
                    try {
                        params[i] = pm.getType().cast(val);
                    } catch (ClassCastException e) {
                        throw new IntegrityException(
                                "Type mismatch or unable to cast. Field: '" + pm.getName() + "' is: '" + pm.getType() + "', but got: '" + id.getClass() + "'");
                    }
                }
            }

            System.out.println("SELECT findById: ");
            System.out.println(sql.toString());

        return jdbc.queryOne(sql.toString(), this::mapEntity, params)
            .orElse(null);

        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session) {
        String tableName = entityMetadata.getTableName();
        String sql = "SELECT * FROM " + tableName;

        // FIXME - maybe separate this code to private method in abstractInheritanceStrategy
        System.out.println("SQL: " + sql);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<Object> results = jdbc.query(sql, this::mapEntity);

            List<T> filtered = new ArrayList<>();
            for (Object obj : results) {
                if (type.isInstance(obj)) {
                    filtered.add(type.cast(obj));
                }
            }
            return filtered;
        } catch (Exception e) {
            throw new RuntimeException("Error finding all entities", e);
        }
    }

    private Object mapEntity(ResultSet rs) throws SQLException {
        try {
            assert entityMetadata != null;
            Object entity = entityMetadata.getEntityClass().getDeclaredConstructor().newInstance();

            for (PropertyMetadata prop : entityMetadata.getColumnsForConcreteTable()) {
                try {
                    Object value = rs.getObject(prop.getColumnName());

                    // Konwersja Integer -> Long dla wszystkich pól Long
                    if (value instanceof Integer && prop.getType() == Long.class) {
                        value = ((Integer) value).longValue();
                    }

                    if (value != null) {
                        ReflectionUtils.setFieldValue(entity, prop.getName(), value);
                    }
                } catch (SQLException e) {
                    System.err.println("Failed to get column: " + prop.getColumnName() + " - " + e.getMessage());
                }
            }

            return entity;
        } catch (Exception e) {
            throw new SQLException("Failed to map entity: " + entityMetadata.getEntityClass().getName(), e);
        }
    }
}
