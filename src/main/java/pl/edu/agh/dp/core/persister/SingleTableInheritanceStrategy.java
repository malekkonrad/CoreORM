package pl.edu.agh.dp.core.persister;

import javafx.util.Pair;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class SingleTableInheritanceStrategy extends AbstractInheritanceStrategy {

    public SingleTableInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
    }

    @Override
    public Pair<String, String> create() {
        assert this.entityMetadata != null;
        if (!this.entityMetadata.getInheritanceMetadata().isRoot()){
            return null;
        }
        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE ").append(this.entityMetadata.getTableName()).append(" (\n");

        List<String> columnDefs = new ArrayList<>();

        for (PropertyMetadata col : this.entityMetadata.getColumnsForSingleTable()) {
            columnDefs.add(col.toSqlColumn());
        }

        sb.append(String.join(",\n", columnDefs));

        // PRIMARY KEYS
        List<String> idColumns = new ArrayList<>();
        Collection<PropertyMetadata> rootIds = entityMetadata.getIdColumns().values();
        for (PropertyMetadata idProp : rootIds) {
            idColumns.add(idProp.getColumnName());
        }

        sb.append(",\n PRIMARY KEY (")
                .append(String.join(", ", idColumns))
                .append(")");

        sb.append("\n);");

        return new Pair<>(sb.toString(), "");
    }

    @Override
    public Object insert(Object entity, Session session) {
        assert this.entityMetadata != null;
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

            if (prop.isId() && prop.isAutoIncrement()) {
                continue; // Nie dodawaj tej kolumny do SQL, baza sama ją wypełni
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
            int numOfIds = rootMetadata.getIdColumns().size();
            if (numOfIds == 1) {        // we have one key if there's more then for sure it's not autoincrement
                PropertyMetadata idProp = rootMetadata.getIdColumns().values().iterator().next();
                if (idProp.isAutoIncrement()) {
                    System.out.println("seting id in " + entity.toString()+ " value: " + generatedId);
                    ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);
                }
            }

            return generatedId;
        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity " + entity, e);
        }
    }

    @Override
    public void update(Object entity, Session session) {
        assert this.entityMetadata != null;
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();

        List<String> setColumns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Zbierz wszystkie pola z hierarchii (pomiń ID i discriminator)
        List<PropertyMetadata> allProperties = rootMetadata.getColumnsForSingleTable();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();

        for (PropertyMetadata prop : allProperties) {
            // Pomiń ID i discriminator
            if (prop.isId() || prop.getColumnName().equals(discriminatorColumn)) {
                continue;
            }

            // Tylko pola należące do tej klasy lub jej przodków
            if (fieldBelongsToClass(prop, entity.getClass())) {
                setColumns.add(prop.getColumnName() + " = ?");
                Object value = pl.edu.agh.dp.core.util.ReflectionUtils.getFieldValue(entity, prop.getName());
                values.add(value);
            }
        }

        // WHERE clause
        Object idValue = getIdValue(entity);
        String whereClause = buildWhereClause(rootMetadata);
        Object[] idParams = prepareIdParams(idValue);

        // Połącz wartości SET + WHERE
        List<Object> allParams = new ArrayList<>(values);
        allParams.addAll(Arrays.asList(idParams));

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(tableName)
                .append(" SET ").append(String.join(", ", setColumns))
                .append(" WHERE ").append(whereClause);

        System.out.println("SingleTable UPDATE SQL: " + sql);
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
        assert this.entityMetadata != null;
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();

        Object idValue = getIdValue(entity);
        String whereClause = buildWhereClause(rootMetadata);
        Object[] idParams = prepareIdParams(idValue);

        String sql = "DELETE FROM " + tableName + " WHERE " + whereClause;

        System.out.println("SingleTable DELETE SQL: " + sql);
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
        assert this.entityMetadata != null;
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();

        PropertyMetadata idColumn = rootMetadata.getIdColumns().values().iterator().next();

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
        assert this.entityMetadata != null;
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();

        String discName = rootMetadata.getInheritanceMetadata().getClassToDiscriminator().get(type);

        // Polymorphic logic
        List<String> discNames = new ArrayList<>();
        discNames.add(discName);
        if (!this.entityMetadata.getInheritanceMetadata().getChildren().isEmpty()) {
            List<EntityMetadata> childrenToVisit = this.entityMetadata.getInheritanceMetadata().getChildren();
            while (!childrenToVisit.isEmpty()) {
                var child =  childrenToVisit.get(0);
                var childType = child.getEntityClass();
                discNames.add(rootMetadata.getInheritanceMetadata().getClassToDiscriminator().get(childType));
                if (!child.getInheritanceMetadata().getChildren().isEmpty()) {
                    childrenToVisit.addAll(child.getInheritanceMetadata().getChildren());
                }
                childrenToVisit.remove(child);
            }
        }
        StringBuilder discIn = new StringBuilder();
        for (String name : discNames) {
            discIn.append("'").append(name).append("',");
        }
        discIn = new StringBuilder(discIn.substring(0, discIn.length() - 1));

        String sql = "SELECT * FROM " + tableName + " WHERE " + discriminatorColumn + " IN (" + discIn +")";
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
            assert this.entityMetadata != null;
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

        // Use the inherited method from AbstractInheritanceStrategy
        return getValueFromResultSet(rs, columnName, type);
    }

    protected boolean fieldBelongsToClass(PropertyMetadata prop, Class<?> targetClass) {
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
