package pl.edu.agh.dp.core.persister;

import javafx.util.Pair;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.finder.Condition;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.finder.Sort;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.*;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class JoinedTableInheritanceStrategy extends AbstractInheritanceStrategy {

    public JoinedTableInheritanceStrategy(EntityMetadata entityMetadata) {
        super(entityMetadata);
    }

    @Override
    public PairTargetStatements getPairStatement(Object entity, String relationshipName) {
        assert entityMetadata != null;
        AssociationMetadata associationMetadata = entityMetadata.getAssociationMetadata().get(relationshipName);

        TargetStatement joinStmt = associationMetadata.getJoinStatement();
        TargetStatement whereStmt = entityMetadata.getSelectByIdStatement(entity);

        whereStmt.setTargetTableName(entityMetadata.getInheritanceMetadata().getRootClass().getTableName());
        whereStmt.setTargetTableName(joinStmt.getRootTableName());

        return new PairTargetStatements(whereStmt, joinStmt);
    }

    @Override
    public Pair<String, String> create() {
        StringBuilder sb = new StringBuilder();

        assert entityMetadata != null;

        sb.append(entityMetadata.getSqlTable());

        return new Pair<>(sb.toString(), entityMetadata.getSqlConstraints());
    }

    private List<EntityMetadata> buildInheritanceChain() {
        List<EntityMetadata> visitingChain =  new ArrayList<>();
        visitingChain.add(entityMetadata);

        EntityMetadata current = entityMetadata;
        while(true) {
            assert current != null;
            if (current.getInheritanceMetadata().getParent() == null) break;
            visitingChain.add(current.getInheritanceMetadata().getParent());
            current = current.getInheritanceMetadata().getParent();
        }
        Collections.reverse(visitingChain);

        return visitingChain;
    }

    @Override
    public Object insert(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            Long generatedId = null;
            // Sorted visiting chain from root to leaf
            List<EntityMetadata> visitingChain = buildInheritanceChain();

            assert entityMetadata != null;
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

            List<PropertyMetadata> idColumns = new ArrayList<>(root.getIdColumns().values());
            // get provided ids
            Set<String> idProvided = getProvidedIds(entity);
            // composite keys error handling
            String idProp = getIdNameAndCheckCompositeKey(idProvided, idColumns);

            for (EntityMetadata meta : visitingChain) {
                List<String> columns = new ArrayList<>();
                List<Object> values = new ArrayList<>();

                boolean isRoot = meta.getInheritanceMetadata().isRoot();

                for (PropertyMetadata prop : meta.getProperties().values()) {

                    if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                        continue;
                    }

                    //ID support in ROOT table (AutoIncrement)
                    if (isRoot && prop.isId() && prop.isAutoIncrement()) {
                        continue;
                    }

                    Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                    if (value != null) {
                        columns.add(prop.getColumnName());
                        values.add(value);
                    }
                }

                if (isRoot) {
                    columns.add("DTYPE");
                    values.add(root.getInheritanceMetadata().getClassToDiscriminator().get(entityMetadata.getEntityClass()));
                }

                // ID not in root
                if (!isRoot) {
                    if (generatedId == null) {
                        throw new RuntimeException("Generated ID is null but trying to insert into child table! Root insert failed?");
                    }

                    String childIdColumnName = root.getIdColumns().keySet().iterator().next();
                    columns.add(childIdColumnName);
                    values.add(generatedId);
                }

                // relationships
                fillRelationshipData(entity, meta, columns, values);

                StringBuilder sql = new StringBuilder();
                sql.append("INSERT INTO ")
                        .append(meta.getTableName())
                        .append(" (")
                        .append(String.join(", ", columns))
                        .append(") VALUES (")
                        .append("?,".repeat(values.size()));

                sql.deleteCharAt(sql.length() - 1); // delete last comma
                sql.append(")");

                System.out.println("Joined Insert SQL (" + (isRoot ? "ROOT" : "CHILD") + "): " + sql);
                System.out.println("Values: " + values);

                Long currentResult = jdbc.insert(sql.toString(), isRoot ? idProp : "", values.toArray());

                if (isRoot) {
                    // set generated ID
                    int numOfIds = meta.getIdColumns().size();
                    if (numOfIds == 1) {        // we have one key if there's more then for sure it's not autoincrement
                        generatedId = currentResult;
                        PropertyMetadata idPropName = meta.getIdColumns().values().iterator().next();
                        if (idPropName.isAutoIncrement()) {
                            System.out.println("seting id in " + entity.toString()+ " value: " + generatedId);
                            ReflectionUtils.setFieldValue(entity, idPropName.getName(), generatedId);
                        }
                    }
                }
            }

            // association tables
            insertAssociationTables(jdbc, entity);

            return generatedId;

        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity with joined table strategy: " + entity, e);
        }
    }

    @Override
    public void update(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<EntityMetadata> chain = buildInheritanceChain();

            for (EntityMetadata meta : chain) {
                List<String> setColumns = new ArrayList<>();
                List<Object> values = new ArrayList<>();

                // Only fields defined in this particular class
                for (PropertyMetadata prop : meta.getProperties().values()) {
                    if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                        continue;
                    }

                    if (prop.isId()) {
                        continue;
                    }

                    setColumns.add(prop.getColumnName());
                    Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                    values.add(value);
                }

                fillRelationshipData(entity, meta, setColumns, values);

                if (setColumns.isEmpty()) {
                    continue;
                }

                assert entityMetadata != null;
                EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
                Object idValue = getIdValue(entity);
                String whereClause = buildWhereClause(root);
                Object[] idParams = prepareIdParams(idValue);

                List<Object> allParams = new ArrayList<>(values);
                allParams.addAll(Arrays.asList(idParams));

                StringBuilder sql = new StringBuilder();
                sql.append("UPDATE ").append(meta.getTableName())
                        .append(" SET ").append(String.join(" = ?, ", setColumns)).append(setColumns.isEmpty() ? "" : " = ?")
                        .append(" WHERE ").append(whereClause);

                System.out.println("Joined UPDATE SQL (" + meta.getTableName() + "): " + sql);
                System.out.println("Values: " + allParams);

                jdbc.update(sql.toString(), allParams.toArray());
            }

            // association tables
            updateAssociationTables(jdbc, entity);
        } catch (Exception e) {
            throw new RuntimeException("Error updating entity with joined table strategy: " + entity, e);
        }
    }

    @Override
    public void delete(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<EntityMetadata> chain = buildInheritanceChain();

            // DELETE from the farthest child to the root (to avoid affecting foreign keys)
            Collections.reverse(chain);

            assert entityMetadata != null;
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
            Object idValue = getIdValue(entity);
            Object[] idParams = prepareIdParams(idValue);

            for (EntityMetadata meta : chain) {
                String whereClause = buildWhereClause(root);

                StringBuilder sql = new StringBuilder();
                sql.append("DELETE FROM ").append(meta.getTableName())
                        .append(" WHERE ").append(whereClause);

                System.out.println("Joined DELETE SQL (" + meta.getTableName() + "): " + sql);
                System.out.println("ID: " + idValue);

                jdbc.update(sql.toString(), idParams);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error deleting entity with joined table strategy: " + entity, e);
        }
    }

    @Override
    public Object findById(Object id, Session session) {
        try {
            assert entityMetadata != null;
            // query with LEFT JOINS
            SqlAndParams query = buildPolymorphicQuery(id); // id != null -> generuje WHERE

            System.out.println("Joined findById SQL: " + query.sql);

            JdbcExecutor jdbc = session.getJdbcExecutor();
            return jdbc.queryOne(query.sql, this::mapRow, query.params.toArray()).orElse(null);

        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session, PairTargetStatements pairTargetStatements) {
        TargetStatement joinStmt = pairTargetStatements.getJoinStatements().get(0);
        TargetStatement whereStmt = pairTargetStatements.getWhereStatements().get(0);
        assert entityMetadata != null;
        try {
            // without where id = ?
            SqlAndParams query = buildPolymorphicQuery(null);

            query.sql += " " + joinStmt.getStatement();

            // additional where
            if (!whereStmt.isBlank()) {

                query.sql += " WHERE " + whereStmt.getStatement();
            }

            System.out.println("Joined findAll SQL: " + query.sql);

            JdbcExecutor jdbc = session.getJdbcExecutor();

            List<Object> results = jdbc.query(query.sql, this::mapRow);

            List<T> filtered = new ArrayList<>();
            for (Object obj : results) {
                if (type.isInstance(obj)) {
                    filtered.add(type.cast(obj));
                }
            }
            return filtered;

        } catch (Exception e) {
            throw new RuntimeException("Error finding entities in Joined Strategy", e);
        }
    }

    @Override
    public <T> List<T> findBy(Class<T> type, Session session, QuerySpec<T> querySpec) {
        assert entityMetadata != null;
        try {
            // Build the polymorphic query
            SqlAndParams query = buildPolymorphicQuery(null);
            
            List<Object> params = new ArrayList<>(query.params);
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

            // QuerySpec conditions - need to resolve field to proper table in inheritance hierarchy
            String querySpecWhere = buildJoinedQuerySpecWhereClause(querySpec, params);
            if (!querySpecWhere.isEmpty()) {
                if (query.sql.contains(" WHERE ")) {
                    query.sql += " AND " + querySpecWhere;
                } else {
                    query.sql += " WHERE " + querySpecWhere;
                }
            }

            // ORDER BY - need to resolve field to proper table
            String orderBy = buildJoinedQuerySpecOrderByClause(querySpec);
            if (!orderBy.isEmpty()) {
                query.sql += " ORDER BY " + orderBy;
            }

            // LIMIT/OFFSET
            query.sql += buildQuerySpecLimitOffsetClause(querySpec);

            System.out.println("Joined Finder SQL: " + query.sql);
            System.out.println("Joined Finder params: " + params);

            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<Object> results = jdbc.query(query.sql, this::mapRow, params.toArray());

            // Filter by type
            List<T> filtered = new ArrayList<>();
            for (Object obj : results) {
                if (type.isInstance(obj)) {
                    filtered.add(type.cast(obj));
                }
            }
            return filtered;

        } catch (Exception e) {
            throw new RuntimeException("Error finding entities with QuerySpec in Joined Strategy", e);
        }
    }

    private String findTableForField(String fieldName) {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        List<EntityMetadata> allSubclasses = getAllSubclassesIncludingRoot(root);
        
        for (EntityMetadata meta : allSubclasses) {
            if (meta.getProperties().containsKey(fieldName)) {
                return meta.getTableName();
            }
            if (meta.getIdColumns().containsKey(fieldName)) {
                return meta.getTableName();
            }
        }
        // Default to root table if not found
        return root.getTableName();
    }

    private String resolveJoinedColumnName(String fieldName) {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        List<EntityMetadata> allSubclasses = getAllSubclassesIncludingRoot(root);
        
        for (EntityMetadata meta : allSubclasses) {
            PropertyMetadata pm = meta.getProperties().get(fieldName);
            if (pm != null) {
                return pm.getColumnName();
            }
            pm = meta.getIdColumns().get(fieldName);
            if (pm != null) {
                return pm.getColumnName();
            }
        }
        // If not found, assume it's already a column name
        return fieldName;
    }

    private <T> String buildJoinedQuerySpecWhereClause(QuerySpec<T> querySpec, List<Object> params) {
        if (!querySpec.hasConditions()) {
            return "";
        }
        
        List<String> sqlConditions = new ArrayList<>();
        for (Condition condition : querySpec.getConditions()) {
            String fieldName = condition.getField();
            String tableName = findTableForField(fieldName);
            String columnName = resolveJoinedColumnName(fieldName);
            
            // Generate SQL with proper table and column name
            String sql = condition.toSql(tableName)
                    .replace(tableName + "." + fieldName, tableName + "." + columnName);
            sqlConditions.add(sql);
            params.addAll(condition.getParams());
        }
        
        return String.join(" AND ", sqlConditions);
    }

    private <T> String buildJoinedQuerySpecOrderByClause(QuerySpec<T> querySpec) {
        if (!querySpec.hasSorting()) {
            return "";
        }
        
        List<String> sortClauses = new ArrayList<>();
        for (Sort sort : querySpec.getSortings()) {
            String fieldName = sort.getField();
            String tableName = findTableForField(fieldName);
            String columnName = resolveJoinedColumnName(fieldName);
            sortClauses.add(tableName + "." + columnName + " " + sort.getDirection().name());
        }
        
        return String.join(", ", sortClauses);
    }

    private static class SqlAndParams {
        String sql;
        List<Object> params;

        public SqlAndParams(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    private SqlAndParams buildPolymorphicQuery(Object id) {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        List<EntityMetadata> allSubclasses = getAllSubclassesIncludingRoot(root);

        StringBuilder selectPart = new StringBuilder("SELECT ");
        StringBuilder joinPart = new StringBuilder(" FROM " + root.getTableName());

        List<String> columns = new ArrayList<>();

        // column list with aliases
        for (EntityMetadata meta : allSubclasses) {
            for (PropertyMetadata prop : meta.getProperties().values()) {
                if (prop.getSqlType() != null) {
                    columns.add(meta.getTableName() + "." + prop.getColumnName() +
                            " AS " + meta.getTableName() + "_" + prop.getColumnName());
                }
            }
        }

        selectPart.append(String.join(", ", columns));

        // LEFT JOINs
        for (EntityMetadata sub : allSubclasses) {
            if (sub == root) continue;

            EntityMetadata parent = sub.getInheritanceMetadata().getParent();
            // JOIN po ID
            String pkName = root.getIdColumns().values().iterator().next().getColumnName();

            joinPart.append(" LEFT JOIN ").append(sub.getTableName())
                    .append(" ON ")
                    .append(parent.getTableName()).append(".").append(pkName)
                    .append(" = ")
                    .append(sub.getTableName()).append(".").append(pkName);
        }

        // WHERE
        List<Object> params = new ArrayList<>();
        if (id != null) {
            List<PropertyMetadata> idColumns = entityMetadata.getIdColumns().values().stream().toList();
            joinPart.append(" WHERE ");

            if (idColumns.size() == 1) {
                PropertyMetadata pm = idColumns.iterator().next();
                joinPart.append(root.getTableName()).append(".").append(pm.getColumnName()).append(" = ?");
                params.add(pm.getType().cast(id));
            } else {
                // composite key
                int count = 0;
                for(PropertyMetadata pm : idColumns) {
                    if(count > 0) joinPart.append(" AND ");
                    joinPart.append(root.getTableName()).append(".").append(pm.getColumnName()).append(" = ?");
                    Object val = ReflectionUtils.getFieldValue(id, pm.getName());
                    params.add(val);
                    count++;
                }
            }
        }

        return new SqlAndParams(selectPart.toString() + joinPart.toString(), params);
    }

    private Object mapRow(ResultSet rs) throws SQLException {
        try {
            assert entityMetadata != null;
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

            String discriminatorCol = "DTYPE";
            String alias = root.getTableName() + "_" + discriminatorCol;

            String className;
            try {
                className = rs.getString(alias);
            } catch (SQLException e) {
                className = rs.getString(discriminatorCol);
            }

            if (className == null) {
                className = root.getEntityClass().getName();
            }

            Class<?> realClass;
            try {
                realClass = root.getInheritanceMetadata().getDiscriminatorToClass().get(className);

                if (realClass == null) {
                    realClass = Class.forName(className);
                }
            } catch (Exception e) {
                throw new RuntimeException("Unknown entity type: " + className);
            }

            Object instance = realClass.getDeclaredConstructor().newInstance();

            populateFieldsWithAliases(instance, rs, realClass);

            return instance;

        } catch (Exception e) {
            throw new RuntimeException("Error mapping row in Joined strategy", e);
        }
    }

    private void populateFieldsWithAliases(Object instance, ResultSet rs, Class<?> realClass) throws SQLException {
        EntityMetadata currentMeta = findMetadataForClass(realClass);

        // we are going upwards
        while (currentMeta != null) {
            for (PropertyMetadata prop : currentMeta.getProperties().values()) {
                if (prop.getSqlType() == null) continue;

                if (prop.getName() == null) {
                    continue;
                }

                if (Objects.equals(prop.getColumnName(), "DTYPE")) continue;

                String columnAlias = currentMeta.getTableName() + "_" + prop.getColumnName();

                try {
                    String fieldName = prop.getName();
                    Object value = getValueFromResultSet(rs, columnAlias, prop.getType());

                    if (value != null) {
                        Object castedValue = castSqlValueToJava(prop.getType(), value);
                        ReflectionUtils.setFieldValue(instance, fieldName, castedValue);
                    }
                } catch (SQLException e) {
                    // ignore
                }
            }

            // go to parent
            if (currentMeta.getInheritanceMetadata().getParent() != null) {
                currentMeta = currentMeta.getInheritanceMetadata().getParent();
            } else {
                currentMeta = null;
            }
        }
    }

    private List<EntityMetadata> getAllSubclassesIncludingRoot(EntityMetadata root) {
        List<EntityMetadata> list = new ArrayList<>();
        list.add(root);

        List<EntityMetadata> toProcess = new ArrayList<>(root.getInheritanceMetadata().getChildren());
        while (!toProcess.isEmpty()) {
            EntityMetadata current = toProcess.remove(0);
            list.add(current);
            toProcess.addAll(current.getInheritanceMetadata().getChildren());
        }
        return list;
    }

    private EntityMetadata findMetadataForClass(Class<?> clazz) {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        if (root.getEntityClass().equals(clazz)) return root;

        // BFS search
        List<EntityMetadata> queue = new ArrayList<>(root.getInheritanceMetadata().getChildren());
        while(!queue.isEmpty()) {
            EntityMetadata current = queue.remove(0);
            if (current.getEntityClass().equals(clazz)) return current;
            queue.addAll(current.getInheritanceMetadata().getChildren());
        }
        return null;
    }

}
