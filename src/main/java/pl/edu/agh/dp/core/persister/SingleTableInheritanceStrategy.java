package pl.edu.agh.dp.core.persister;

import lombok.NoArgsConstructor;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.Dialect;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.jdbc.JdbcExecutorImpl;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public class SingleTableInheritanceStrategy extends AbstractInheritanceStrategy {

    public SingleTableInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
    }


    @Override
    public String create(JdbcExecutor jdbcExecutor) {
        // FIXME nullpointer!
        if (!this.entityMetadata.getInheritanceMetadata().isRoot()){
            return null;
        }
        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE ").append(this.entityMetadata.getTableName()).append(" (\n");

        List<String> columnDefs = new ArrayList<>();

        for (PropertyMetadata col : this.entityMetadata.getColumnsForSingleTable()) {
            columnDefs.add("    " + col.getColumnName() + " " + col.getSqlType());
        }

        // TODO primary key!

        sb.append(String.join(",\n", columnDefs));
        sb.append("\n);");

        return sb.toString();
    }

    @Override
    public Object insert(Object entity, Session session) {
        ///  FIXME
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();
        String discriminatorValue = rootMetadata.getInheritanceMetadata()
                .getClassToDiscriminator().get(entity.getClass());

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Dodaj discriminator
        columns.add(discriminatorColumn);
        values.add(discriminatorValue);

        // Zbierz wszystkie właściwości z hierarchii dziedziczenia
        List<PropertyMetadata> allProperties = rootMetadata.getColumnsForSingleTable();

        for (PropertyMetadata prop : allProperties) {
            // Pomiń kolumnę discriminatora (już dodana)
            if (prop.getColumnName().equals(discriminatorColumn)) {
                continue;
            }

            columns.add(prop.getColumnName());

            // Sprawdź czy pole należy do aktualnej klasy lub jej przodków
            if (fieldBelongsToClass(prop, entity.getClass())) {
                Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                values.add(value);
            } else {
                // Pole nie należy do tej klasy - wstaw NULL
                values.add(null);
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ")
                .append(tableName)
                .append(" (")
                .append(String.join(", ", columns))
                .append(") VALUES (")
                .append("?,".repeat(values.size()));
        sql.deleteCharAt(sql.length() - 1);
        sql.append(")");

        System.out.println(sql.toString());
        System.out.println(values.toString());
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            Long generatedId = jdbc.insert(sql.toString(), values.toArray());
            System.out.println("Generated ID: " + generatedId);
            // Ustaw wygenerowane ID
            // FIXME important!!!!!!!!!!!!!!!!!!!!!!!!
            PropertyMetadata idProp = rootMetadata.getIdColumns().get("id");
            if (idProp.isAutoIncrement()) {
                ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);
            }

            return generatedId;
        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity " + entity, e);
        }
    }

    @Override
    public void update(Object entity, Session session) {

    }

    @Override
    public void delete(Object entity, Session session) {

    }

    @Override
    public Object findById(Object id, Session session) {
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();

        // FIXME
        PropertyMetadata idColumn = rootMetadata.getIdColumns().get("id");

        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumn.getColumnName() + " = ?";

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            return jdbc.queryOne(sql, this::mapEntity, id).orElse(null);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session) {
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();

        String discName = rootMetadata.getInheritanceMetadata().getClassToDiscriminator().get(type);
        String sql = "SELECT * FROM " + tableName + " WHERE " + discriminatorColumn + " = '" + discName +"'";
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

            EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
            String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();
            if (discriminatorColumn==null){
                System.out.println("discriminatorColumn is null");
            }
            String discriminatorValue = rs.getString("DTYPE");

            Class<?> actualClass = rootMetadata.getInheritanceMetadata()
                    .getDiscriminatorToClass()
                    .getOrDefault(discriminatorValue, rootMetadata.getEntityClass());

            Object entity = actualClass.getDeclaredConstructor().newInstance();

            List<PropertyMetadata> allColumns = rootMetadata.getColumnsForSingleTable();

            for (PropertyMetadata pm : allColumns) {
                if (pm.getColumnName().equals(discriminatorColumn)) {
                    continue;
                }

                try {
                    Object value = getValueFromResultSet(rs, pm);

                    if (value != null) {
                        ReflectionUtils.setFieldValue(entity, pm.getName(), value);
                    }
                } catch (SQLException e) {
                    // Ignore missing columns
                }
            }

            return entity;

        } catch (Exception e) {
            throw new SQLException("Failed to map entity: " + entityMetadata.getEntityClass().getName(), e);
        }
    }

    private Object getValueFromResultSet(ResultSet rs, PropertyMetadata pm) throws SQLException {
        String columnName = pm.getColumnName();
        Class<?> type = pm.getType();

        // Użyj typowanych getterów zamiast getObject()
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

        // Fallback
        return rs.getObject(columnName);
    }

    private boolean fieldBelongsToClass(PropertyMetadata prop, Class<?> targetClass) {
        try {
            // Sprawdź w aktualnej klasie i wszystkich nadklasach
            Class<?> current = targetClass;
            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField(prop.getName());
                    return true; // Pole znalezione
                } catch (NoSuchFieldException e) {
                    // Pole nie istnieje w tej klasie, sprawdź nadklasę
                    current = current.getSuperclass();
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
