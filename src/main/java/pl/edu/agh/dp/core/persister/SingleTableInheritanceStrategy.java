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
            PropertyMetadata idProp = rootMetadata.getIdColumns().get(0);
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
        PropertyMetadata idColumn = rootMetadata.getIdColumns().get(0);

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
        return List.of();
    }

    private Object mapEntity(ResultSet rs) throws SQLException {
        try {
            String discriminatorColumn = entityMetadata.getInheritanceMetadata().getDiscriminatorColumnName();
            String discriminatorValue = rs.getString(discriminatorColumn);

            Class<?> actualClass = entityMetadata.getInheritanceMetadata()
                    .getDiscriminatorToClass()
                    .getOrDefault(discriminatorValue, entityMetadata.getEntityClass());

            Object entity = actualClass.getDeclaredConstructor().newInstance();

            List<PropertyMetadata> allColumns = entityMetadata.getColumnsForSingleTable();

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

    // NOWA METODA - pobierz wartość według typu
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














//
//    private Object mapEntity(ResultSet rs) throws SQLException {
//        try {
//            // 1. Odczytaj discriminator
//            String discriminatorColumn = entityMetadata.getInheritanceMetadata().getDiscriminatorColumnName();
//            String discriminatorValue = rs.getString(discriminatorColumn);
//
//            // 2. Znajdź właściwą klasę
//            Class<?> actualClass = entityMetadata.getInheritanceMetadata()
//                    .getDiscriminatorToClass()
//                    .get(discriminatorValue);
//
//            if (actualClass == null) {
//                actualClass = entityMetadata.getEntityClass();
//            }
//
//            // 3. Stwórz instancję
//            Object entity = actualClass.getDeclaredConstructor().newInstance();
//
//            // 4. Wypełnij pola - POPRAWKA TUTAJ
//            List<PropertyMetadata> allColumns = entityMetadata.getColumnsForSingleTable();
//
//            for (PropertyMetadata pm : allColumns) {
//                // Pomiń discriminator - to wirtualne pole
//                if (pm.getColumnName().equals(discriminatorColumn)) {
//                    continue;
//                }
//
//                try {
//                    // Odczytaj wartość z ResultSet używając COLUMN NAME
//                    Object value = rs.getObject(pm.getColumnName());
//
//                    // Ustaw wartość w obiekcie używając FIELD NAME (nie columnName!)
//                    if (value != null) {
//                        ReflectionUtils.setFieldValue(entity, pm.getName(), value);
//                    }
//                } catch (SQLException e) {
//                    // Kolumna może nie istnieć w ResultSet (NULL w SINGLE_TABLE)
//                    System.err.println("Column " + pm.getColumnName() + " not found or null");
//                }
//            }
//
//            return entity;
//
//        } catch (Exception e) {
//            throw new SQLException("Failed to map entity: " + entityMetadata.getEntityClass().getName(), e);
//        }
//    }




//    private Object mapEntity(ResultSet rs) throws SQLException {
//        try {
//            Object entity = this.entityMetadata.getEntityClass().getDeclaredConstructor().newInstance();
//
//            for (PropertyMetadata prop : this.entityMetadata.getProperties()) {
//                Object value = rs.getObject(prop.getColumnName());
//
//                // Konwersja Integer -> Long dla wszystkich pól Long
//                if (value instanceof Integer && prop.getType() == Long.class) {
//                    value = ((Integer) value).longValue();
//                }
//
//                ReflectionUtils.setFieldValue(entity, prop.getName(), value);
//            }
//
//            return entity;
//        } catch (Exception e) {
//            throw new SQLException("Failed to map entity: " + this.entityMetadata.getEntityClass().getName(), e);
//        }
//    }

//    public SingleTableInheritanceStrategy(JdbcExecutor jdbcExecutor,
//                                          Dialect dialect,
//                                          MetadataRegistry metadataRegistry) {
//        super(jdbcExecutor, dialect, metadataRegistry);
//    }
//
//    @Override
//    public Object insert(EntityMetadata rootMetadata, Object entity) {
//        InheritanceMetadata inh = rootMetadata.getInheritanceMetadata();
//        String table = rootMetadata.getTableName();
//        String idColumn = rootMetadata.getIdColumns().get(0).getName(); /// FIXME przypadek kilku kluczy
//        String discriminatorColumn = inh.getDiscriminatorColumnName();
//        String discriminatorValue = inh.getDiscriminatorValue(entity.getClass());
//
//        List<String> columns = new ArrayList<>();
//        List<Object> params = new ArrayList<>();
//
//        // Wszystkie kolumny z hierarchii, oprócz ID i discriminatora.
//        for (PropertyMetadata column : rootMetadata.getAllColumnsInHierarchy()) {
//            if (column.isId() || column.isDiscriminator()) {
//                continue;
//            }
//            columns.add(column.getColumnName());
//
//            // Kolumna należy tylko do niektórych podklas.
//            if (column.getDeclaringType().isAssignableFrom(entity.getClass())) {
//                params.add(column.readValue(entity));
//            } else {
//                params.add(null);
//            }
//        }
//
//        // Discriminator
//        columns.add(discriminatorColumn);
//        params.add(discriminatorValue);
//
//        String sql = buildInsertReturning(table, columns, idColumn);
//
//        Object id = jdbcExecutor.queryForObject(sql, params, rs -> {
//            rs.next();
//            return rs.getObject(idColumn);
//        });
//
//        rootMetadata.setIdValue(entity, id);
//        return id;
//    }
//
//    @Override
//    public void update(EntityMetadata rootMetadata, Object entity) {
//        InheritanceMetadata inh = rootMetadata.getInheritanceMetadata();
//        String table = rootMetadata.getTableName();
//        String idColumn = rootMetadata.getIdColumnName();
//        String discriminatorColumn = inh.getDiscriminatorColumnName();
//        String discriminatorValue = inh.getDiscriminatorValue(entity.getClass());
//
//        List<String> columns = new ArrayList<>();
//        List<Object> params = new ArrayList<>();
//
//        for (ColumnMetadata column : rootMetadata.getAllColumnsInHierarchy()) {
//            if (column.isId() || column.isDiscriminator()) {
//                continue;
//            }
//            columns.add(column.getColumnName());
//
//            if (column.getDeclaringType().isAssignableFrom(entity.getClass())) {
//                params.add(column.readValue(entity));
//            } else {
//                params.add(null);
//            }
//        }
//
//        columns.add(discriminatorColumn);
//        params.add(discriminatorValue);
//
//        String sql = buildUpdate(table, columns, idColumn);
//        params.add(rootMetadata.getIdValue(entity)); // WHERE id = ?
//
//        jdbcExecutor.update(sql, params);
//    }
//
//    @Override
//    public void delete(EntityMetadata rootMetadata, Object entity) {
//        String table = rootMetadata.getTableName();
//        String idColumn = rootMetadata.getIdColumnName();
//        String sql = buildDelete(table, idColumn);
//        List<Object> params = List.of(rootMetadata.getIdValue(entity));
//        jdbcExecutor.update(sql, params);
//    }
//
//    @Override
//    @SuppressWarnings("unchecked")
//    public <T> T findById(EntityMetadata rootMetadata, Class<T> type, Object id) {
//        InheritanceMetadata inh = rootMetadata.getInheritanceMetadata();
//        String table = rootMetadata.getTableName();
//        String idColumn = rootMetadata.getIdColumnName();
//        String discriminatorColumn = inh.getDiscriminatorColumnName();
//
//        String sql = "SELECT * FROM " + table + " WHERE " + idColumn + " = ?";
//
//        return jdbcExecutor.queryForObject(sql, List.of(id), rs -> {
//            if (!rs.next()) {
//                return null;
//            }
//            Map<String, Object> row = readRow(rs);
//            String discValue = (String) row.get(discriminatorColumn);
//            Class<?> actualClass = inh.getClassForDiscriminator(discValue);
//
//            EntityMetadata targetMetadata = metadataRegistry.getMetadata(actualClass);
//            return (T) targetMetadata.mapRow(row);
//        });
//    }
//
//    @Override
//    @SuppressWarnings("unchecked")
//    public <T> List<T> findAll(EntityMetadata rootMetadata, Class<T> type) {
//        InheritanceMetadata inh = rootMetadata.getInheritanceMetadata();
//        String table = rootMetadata.getTableName();
//        String discriminatorColumn = inh.getDiscriminatorColumnName();
//
//        String sql = "SELECT * FROM " + table;
//
//        return jdbcExecutor.query(sql, List.of(), rs -> {
//            Map<String, Object> row = readRow(rs);
//            String discValue = (String) row.get(discriminatorColumn);
//            Class<?> actualClass = inh.getClassForDiscriminator(discValue);
//            EntityMetadata targetMetadata = metadataRegistry.getMetadata(actualClass);
//            return (T) targetMetadata.mapRow(row);
//        });
//    }

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
