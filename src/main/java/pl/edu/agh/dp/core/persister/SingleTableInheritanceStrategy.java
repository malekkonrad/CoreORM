package pl.edu.agh.dp.core.persister;

import javafx.util.Pair;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.*;
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
    public PairTargetStatements getPairStatement(Object entity, String relationshipName) {
        assert entityMetadata != null;
        AssociationMetadata associationMetadata = entityMetadata.getAssociationMetadata().get(relationshipName);

        TargetStatement joinStmt = associationMetadata.getJoinStatement();
        TargetStatement whereStmt = entityMetadata.getSelectByIdStatement(entity);

        // single table root, could be itself
        whereStmt.setRootTableName(entityMetadata.getInheritanceMetadata().getRootClass().getTableName());
        whereStmt.setTargetTableName(joinStmt.getRootTableName());

        return new PairTargetStatements(whereStmt, joinStmt);
    }

    @Override
    public Pair<String, String> create() {
        assert this.entityMetadata != null;
        if (!this.entityMetadata.getInheritanceMetadata().isRoot()){
            return null;
        }
        StringBuilder sb = new StringBuilder();

        // add table to the creation
        sb.append(entityMetadata.getSqlTable());
        // return table and it's constraints
        return new Pair<>(sb.toString(), entityMetadata.getSqlConstraints());
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

        // add discriminator
        columns.add(discriminatorColumn);
        values.add(discriminatorValue);

        // Collect all properties from the inheritance hierarchy
        List<PropertyMetadata> allProperties = rootMetadata.getAllColumnsForSingleTable().values().stream().toList();

        for (PropertyMetadata prop : allProperties) {
            // skipp discriminator
            if (prop.getColumnName().equals(discriminatorColumn)) {
                continue;
            }

            if (prop.isId() && prop.isAutoIncrement()) {
                continue;
            }

            if (fieldBelongsToClass(prop, entity.getClass())) {
                Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                if (value != null) {
                    columns.add(prop.getColumnName());
                    values.add(value);
                }
            } else {
                columns.add(prop.getColumnName());
                values.add(null);
            }
        }
        List<PropertyMetadata> idColumns = new ArrayList<>(entityMetadata.getIdColumns().values());
        // get provided ids
        Set<String> idProvided = getProvidedIds(entity);
        // relationships
        fillRelationshipData(entity, entityMetadata, columns, values);
        // composite keys error handling
        String idProp = getIdNameAndCheckCompositeKey(idProvided, idColumns);

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
            Long generatedId = jdbc.insert(sql.toString(), idProp, values.toArray());
            System.out.println("Generated ID: " + generatedId);

            // set generated ID
            int numOfIds = rootMetadata.getIdColumns().size();
            if (numOfIds == 1) {        // we have one key if there's more then for sure it's not autoincrement
                PropertyMetadata idPropName = rootMetadata.getIdColumns().values().iterator().next();
                if (idPropName.isAutoIncrement()) {
                    System.out.println("seting id in " + entity.toString()+ " value: " + generatedId);
                    ReflectionUtils.setFieldValue(entity, idPropName.getName(), generatedId);
                }
            }

            // association tables
            insertAssociationTables(jdbc, entity);

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

        List<PropertyMetadata> allProperties = rootMetadata.getAllColumnsForSingleTable().values().stream().toList();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();

        for (PropertyMetadata prop : allProperties) {
            if (prop.isId() || prop.getColumnName().equals(discriminatorColumn)) {
                continue;
            }

            if (fieldBelongsToClass(prop, entity.getClass())) {
                setColumns.add(prop.getColumnName());
                Object value = pl.edu.agh.dp.core.util.ReflectionUtils.getFieldValue(entity, prop.getName());
                values.add(value);
            }
        }

        fillRelationshipData(entity, entityMetadata, setColumns, values);

        // WHERE clause
        Object idValue = getIdValue(entity);
        String whereClause = buildWhereClause(rootMetadata);
        Object[] idParams = prepareIdParams(idValue);

        // Połącz wartości SET + WHERE
        List<Object> allParams = new ArrayList<>(values);
        allParams.addAll(Arrays.asList(idParams));

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(tableName)
                .append(" SET ").append(String.join(" = ?, ", setColumns)).append(setColumns.isEmpty() ? "" : " = ?")
                .append(" WHERE ").append(whereClause);

        System.out.println("SingleTable UPDATE SQL: " + sql);
        System.out.println("Values: " + allParams);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            jdbc.update(sql.toString(), allParams.toArray());

            // association tables
            updateAssociationTables(jdbc, entity);
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
        assert entityMetadata != null;
        String tableName = entityMetadata.getTableName();

        PropertyMetadata idColumn = entityMetadata.getIdColumns().values().iterator().next();

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
    public <T> List<T> findAll(Class<T> type, Session session, PairTargetStatements pairTargetStatements) {
        TargetStatement joinStmt = pairTargetStatements.getJoinStatements().get(0);
        TargetStatement whereStmt = pairTargetStatements.getWhereStatements().get(0);
        assert this.entityMetadata != null;
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();

        String discName = rootMetadata.getInheritanceMetadata().getClassToDiscriminator().get(type);

        // Polymorphic logic
        List<String> discNames = new ArrayList<>();
        discNames.add(discName);
        if (!this.entityMetadata.getInheritanceMetadata().getChildren().isEmpty()) {
            List<EntityMetadata> childrenToVisit = new ArrayList<>(this.entityMetadata.getInheritanceMetadata().getChildren());
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

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT * FROM ").append(tableName);

        // join statement
        sqlBuilder.append(" ").append(joinStmt.getStatement());
        // discriminator
        sqlBuilder.append(" WHERE ").append(tableName).append(".").append(discriminatorColumn)
                .append(" IN (").append(discIn).append(")");
        // additional where
        if (!whereStmt.isBlank()) {
            sqlBuilder.append(" AND ").append(whereStmt.getStatement());
        }

        System.out.println("SQL: " + sqlBuilder.toString());

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<Object> results = jdbc.query(sqlBuilder.toString(), this::mapEntity);

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

    @Override
    public <T> List<T> findBy(Class<T> type, Session session, QuerySpec<T> querySpec) {
        assert this.entityMetadata != null;
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();
        String tableName = rootMetadata.getTableName();
        String discriminatorColumn = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();

        String discName = rootMetadata.getInheritanceMetadata().getClassToDiscriminator().get(type);

        // Polymorphic logic - include subclasses
        List<String> discNames = new ArrayList<>();
        discNames.add(discName);
        if (!this.entityMetadata.getInheritanceMetadata().getChildren().isEmpty()) {
            List<EntityMetadata> childrenToVisit = new ArrayList<>(this.entityMetadata.getInheritanceMetadata().getChildren());
            while (!childrenToVisit.isEmpty()) {
                var child = childrenToVisit.get(0);
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

        List<Object> params = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT * FROM ").append(tableName);

        // WHERE clause with discriminator
        sqlBuilder.append(" WHERE ").append(tableName).append(".").append(discriminatorColumn)
                .append(" IN (").append(discIn).append(")");

        // QuerySpec conditions
        String querySpecWhere = buildQuerySpecWhereClause(querySpec, tableName, params);
        if (!querySpecWhere.isEmpty()) {
            sqlBuilder.append(" AND ").append(querySpecWhere);
        }

        // ORDER BY
        String orderBy = buildQuerySpecOrderByClause(querySpec, tableName);
        if (!orderBy.isEmpty()) {
            sqlBuilder.append(" ORDER BY ").append(orderBy);
        }

        // LIMIT/OFFSET
        sqlBuilder.append(buildQuerySpecLimitOffsetClause(querySpec));

        System.out.println("Finder SQL: " + sqlBuilder.toString());
        System.out.println("Finder params: " + params);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<Object> results = jdbc.query(sqlBuilder.toString(), this::mapEntity, params.toArray());

            List<T> filtered = new ArrayList<>();
            for (Object obj : results) {
                if (type.isInstance(obj)) {
                    filtered.add(type.cast(obj));
                }
            }
            return filtered;
        } catch (Exception e) {
            throw new RuntimeException("Error finding entities with QuerySpec", e);
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

            String discriminatorValue = rs.getString(discriminatorColumn);

            Class<?> actualClass = rootMetadata.getInheritanceMetadata()
                    .getDiscriminatorToClass()
                    .getOrDefault(discriminatorValue, rootMetadata.getEntityClass());

            Object entity = actualClass.getDeclaredConstructor().newInstance();

            List<PropertyMetadata> allColumns = rootMetadata.getAllColumnsForSingleTable().values().stream().toList();

            for (PropertyMetadata pm : allColumns) {
                if (pm.getColumnName().equals(discriminatorColumn)) {
                    continue;
                }

                try {
                    String fieldName = pm.getName();
                    Object value = getValueFromResultSet(rs, pm);

                    if (value != null) {
                        Object castedValue = castSqlValueToJava(pm.getType(), value);
                        ReflectionUtils.setFieldValue(entity, fieldName, castedValue);
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
