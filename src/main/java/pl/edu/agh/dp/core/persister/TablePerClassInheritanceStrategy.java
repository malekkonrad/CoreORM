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
import java.util.stream.Collectors;

public class TablePerClassInheritanceStrategy extends AbstractInheritanceStrategy{

    public TablePerClassInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
    }

    @Override
    public String create(JdbcExecutor jdbcExecutor) {


        StringBuilder sb = new StringBuilder();

        // 1. Obsługa tworzenia samej sekwencji
        assert this.entityMetadata != null;
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        // FIXME why do we create a sequence and not using the SERIAL type?
//
//        if (rootMetadata == entityMetadata) {
//            String sequenceName = this.entityMetadata.getTableName() + "_id_seq";
//            // START 1 INCREMENT 1 to standard
//            String seq =  "CREATE SEQUENCE " + sequenceName + " START 1 INCREMENT 1;\n";
//            System.out.println(seq);
//            jdbcExecutor.createTable(seq);
//        }
//
//        String rootTableName = rootMetadata.getTableName();
//        String sequenceName = rootTableName + "_id_seq"; // np. animal_id_seq

        sb.append("CREATE TABLE ").append(this.entityMetadata.getTableName()).append(" (\n");

        List<String> columnDefs = new ArrayList<>();

        // Pobieramy ID, żeby wiedzieć, która kolumna to ID
        Collection<PropertyMetadata> rootIds = rootMetadata.getIdColumns().values();
        List<String> idColumnNames = new ArrayList<>();
        for (PropertyMetadata idProp : rootIds) {
            idColumnNames.add(idProp.getColumnName());
        }

        for (PropertyMetadata col : this.entityMetadata.getColumnsForConcreteTable()) {
            StringBuilder colDef = new StringBuilder();

            // Sprawdzamy, czy to ID i dodajemy sekwencję (TYLKO DLA POSTGRES)
            if (idColumnNames.contains(col.getColumnName())) {
                colDef.append(col.toSqlColumn());
//                colDef.append("    ").append(col.getColumnName()).append(" ").append("BIGINT");
//                colDef.append(" DEFAULT nextval('").append(sequenceName).append("') PRIMARY KEY ");
            }
            else{
                colDef.append(col.toSqlColumn());
//                colDef.append("    ").append(col.getColumnName()).append(" ").append(col.getSqlType());
            }

            columnDefs.add(colDef.toString());
        }

        sb.append(String.join(",\n", columnDefs));
        sb.append(",\n ");

        // create Primary Key
        sb.append(rootMetadata.toSqlPrimaryKey());

        sb.append("\n);");

        return sb.toString();
    }

    @Override
    public Object insert(Object entity, Session session) {

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        assert entityMetadata != null;
        List<PropertyMetadata> idColumns = new ArrayList<>(entityMetadata.getIdColumns().values());
        boolean isCompositeKey = idColumns.size() > 1;

        Set<String> idProvided = new HashSet<>();
        for (PropertyMetadata pm : entityMetadata.getColumnsForConcreteTable()) {
            Object value = ReflectionUtils.getFieldValue(entity, pm.getName());
            // TODO handle null in the value, cause it could be set to null explicitly
            if (value != null) {
                columns.add(pm.getColumnName());
                values.add(value);
                if (pm.isId()) {
                    idProvided.add(pm.getName());
                }
            }
        }

        // FIXME nie działa @Mateusz
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

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ")
                .append(entityMetadata.getTableName())
                .append(" (")
                .append(String.join(", ", columns))
                .append(") VALUES (")
                .append("?,".repeat(values.size()));
        if (!values.isEmpty()) sql.deleteCharAt(sql.length() - 1); // sanity check if inserting nothing
        sql.append(")");

        Long generatedId;
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            generatedId = jdbc.insert(sql.toString(), values.toArray());
            System.out.println("Generated ID: " + generatedId);

            // Ustaw wygenerowane ID
            int numOfIds = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().size();
            if (numOfIds == 1) {        // we have one key if there's more then for sure it's not autoincrement
                PropertyMetadata idProp = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().values().iterator().next();
                if (idProp.isAutoIncrement()) {
                    System.out.println("seting id in " + entity.toString()+ " value: " + generatedId);
                    ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException("Error inserting entity " + entity, e);
        }
        return generatedId;
    }


    @Override
    public void update(Object entity, Session session) {
        assert entityMetadata != null;
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
        assert entityMetadata != null;
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
        // 1. Pobieramy metadane dla klasy, o którą pytamy (np. Dog)
        EntityMetadata parentMeta = this.entityMetadata;

        // 2. Znajdujemy wszystkie podklasy (włącznie z 'type'), które mają swoje tabele
        List<EntityMetadata> concreteSubclasses = getAllConcreteSubclasses(parentMeta);

        // 3. Ustalamy wspólne kolumny (tylko te, które ma klasa 'type' / Dog)
        assert parentMeta != null;
        List<String> columnsToSelect = parentMeta.getColumnsForConcreteTable()
                .stream()
                .map(PropertyMetadata::getColumnName)
                .collect(Collectors.toList());

        String columnsSql = String.join(", ", columnsToSelect);

        // 4. Budujemy UNION ALL
        StringBuilder sqlBuilder = new StringBuilder();

        for (int i = 0; i < concreteSubclasses.size(); i++) {
            EntityMetadata subMeta = concreteSubclasses.get(i);

            sqlBuilder.append("SELECT ")
                    .append(columnsSql)
                    // WAŻNE: Wstrzykujemy nazwę klasy jako sztuczną kolumnę DTYPE
                    .append(", '").append(subMeta.getEntityClass().getName()).append("' as DTYPE")
                    .append(" FROM ")
                    .append(subMeta.getTableName());

            if (i < concreteSubclasses.size() - 1) {
                sqlBuilder.append(" UNION ALL ");
            }
        }

        String sql = sqlBuilder.toString();
        System.out.println("TPC SQL: " + sql);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            // Używamy specjalnego mappera, który czyta DTYPE
            return jdbc.query(sql, rs -> mapPolymorphicEntity(rs, type));
        } catch (Exception e) {
            throw new RuntimeException("Error finding all entities in TPC", e);
        }
    }

    private List<EntityMetadata> getAllConcreteSubclasses(EntityMetadata parent) {
        List<EntityMetadata> result = new ArrayList<>();
        List<EntityMetadata> toVisit = new ArrayList<>();

        toVisit.add(parent);
        while (!toVisit.isEmpty()) {
            var current =  toVisit.remove(0);
            for (EntityMetadata child: current.getInheritanceMetadata().getChildren()){
                result.add(child);
                toVisit.add(child);
            }
        }
        return result;
    }

    private <T> T mapPolymorphicEntity(ResultSet rs, Class<T> baseType) throws SQLException {
        try {
            // 1. Odczytujemy sztuczną kolumnę DTYPE
            String className = rs.getString("DTYPE");

            // 2. Tworzymy instancję właściwej klasy (np. Husky, mimo że szukamy Dog)
            Class<?> realClass = Class.forName(className);
            Object instance = realClass.getDeclaredConstructor().newInstance();

            // 3. Wypełniamy pola.
            assert this.entityMetadata != null;
            for (PropertyMetadata col : this.entityMetadata.getColumnsForConcreteTable()) {
                String colName = col.getColumnName();
                Object value = rs.getObject(colName);
                ReflectionUtils.setFieldValue(instance, colName, value);
            }
            return baseType.cast(instance);
        } catch (Exception e) {
            throw new RuntimeException("Error mapping polymorphic entity", e);
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
//                    if (value instanceof Integer && prop.getType() == Long.class) {
//                        value = ((Integer) value).longValue();
//                    }

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
