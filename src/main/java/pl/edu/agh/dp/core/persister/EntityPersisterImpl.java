package pl.edu.agh.dp.core.persister;

import lombok.NoArgsConstructor;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class EntityPersisterImpl implements EntityPersister {

    private EntityMetadata metadata;

    public EntityPersisterImpl(EntityMetadata metadata) {
        this.metadata = metadata;
    }


    @Override
    public Object findById(Object id, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            PropertyMetadata idMeta = metadata.getIdProperty();
            String idColumnName = idMeta.getColumnName();

            List<String> columns = new ArrayList<>();
            // id column
            columns.add(idColumnName);

            // Zwykłe kolumny
            for (PropertyMetadata pm : metadata.getProperties()) {
                columns.add(pm.getColumnName());
            }

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append(String.join(", ", columns))
                    .append(" FROM ")
                    .append(metadata.getTableName())
                    .append(" WHERE ")
                    .append(idColumnName)
                    .append(" = ?");

        return jdbc.queryOne(sql.toString(), this::mapEntity, id)
            .orElse(null);

        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    @Override
    public void insert(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            PropertyMetadata idMeta = metadata.getIdProperty();
            Object idValue = ReflectionUtils.getFieldValue(entity, idMeta.getName());
            boolean idProvided = idValue != null;

            List<String> columns = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            // ID tylko jeśli nie jest null
            if (idProvided) {
                columns.add(idMeta.getColumnName());
                values.add(idValue);
            }

            // Zwykłe kolumny
            for (PropertyMetadata pm : metadata.getProperties()) {
                columns.add(pm.getColumnName());
                Object value = ReflectionUtils.getFieldValue(entity, pm.getName());
                values.add(value);
            }

            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO ")
                    .append(metadata.getTableName())
                    .append(" (")
                    .append(String.join(", ", columns))
                    .append(") VALUES (")
                    .append("?,".repeat(values.size()));
            sql.deleteCharAt(sql.length() - 1);
            sql.append(")");

            Long generatedId = jdbc.insert(sql.toString(), values.toArray());
            PropertyMetadata idProp = metadata.getIdProperty();
            ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);


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

    private Object mapEntity(ResultSet rs) throws SQLException {
        try {
            Object entity = metadata.getEntityClass().getDeclaredConstructor().newInstance();

            // Mapowanie ID z konwersją typu
            PropertyMetadata idProp = metadata.getIdProperty();
            Object idValue = rs.getObject(idProp.getColumnName());

            // Konwersja Integer -> Long dla SQLite
            if (idValue instanceof Integer && idProp.getType() == Long.class) {
                idValue = ((Integer) idValue).longValue();
            }

            ReflectionUtils.setFieldValue(entity, idProp.getName(), idValue);

            // Zwykłe właściwości
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
