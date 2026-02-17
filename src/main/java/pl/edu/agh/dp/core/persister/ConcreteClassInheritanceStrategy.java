package pl.edu.agh.dp.core.persister;

import javafx.util.Pair;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.finder.Condition;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.finder.Sort;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.*;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Hybrid inheritance strategy:
 * - Abstract parent -> concrete child: parent fields are merged into child
 * table (like TABLE_PER_CLASS)
 * - Concrete parent -> concrete child: child has FK to parent (like JOINED)
 *
 * Example: Person (abstract) -> Student (concrete) -> Graduate (concrete)
 * - Student table contains Person fields + Student fields (merged)
 * - Graduate table contains only Graduate fields + FK to Student (joined)
 */
public class ConcreteClassInheritanceStrategy extends AbstractInheritanceStrategy {

    public ConcreteClassInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
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
        assert entityMetadata != null;

        StringBuilder sb = new StringBuilder();
        String constraints = "";

        EntityMetadata rootMetadata = entityMetadata.getInheritanceMetadata().getRootClass();

        boolean behaveLikeSingleTable = rootMetadata.isAbstract();

        // check if root is abstract then behave differently
        if (behaveLikeSingleTable) {

            if (entityMetadata.getIdColumns().isEmpty()) {
                throw new IntegrityException("No id"); // sanity check
            } else if (entityMetadata.getIdColumns().size() == 1) {
                PropertyMetadata idProperty = entityMetadata.getIdColumns().values().iterator().next();
                // must be autoincrement
                if (!idProperty.isAutoIncrement()) {
                    throw new IntegrityException("Id column must be auto-increment");
                }
            } else if (entityMetadata != rootMetadata) {
                throw new IntegrityException("Complex id not supported for concrete class inheritance with abstract class");
            }

            // create sequence for all the subclasses to have the same id
            String rootTableName = rootMetadata.getTableName();
            String sequenceName = rootTableName + "_id_seq";
            if (rootMetadata == entityMetadata) {
                // create sequence only once
                String seq = "CREATE SEQUENCE " + sequenceName + " START 1 INCREMENT 1;\n";
                sb.append(seq);
            }
            // change default in id to the sequence
            PropertyMetadata rootId = entityMetadata.getIdColumns().values().iterator().next();
            rootId.setSqlType("BIGINT");
            rootId.setDefaultValue("nextval('" + sequenceName + "')");
        }
        // Abstract classes have no table and no constraints
        if (!entityMetadata.isAbstract()) {
            sb.append(entityMetadata.getSqlTable());
            if (!behaveLikeSingleTable) {
            constraints = entityMetadata.getSqlConstraints();
            }
        }
        // return table and but skip the constraints if abstract
        return new Pair<>(sb.toString(), constraints);
    }

    /**
     * Build chain of only NON-ABSTRACT classes from root to this entity.
     * Abstract classes don't have tables, their fields are merged in during
     * metadata setup.
     */
    private List<EntityMetadata> buildConcreteChain() {
        return buildConcreteChain(entityMetadata);
    }

    private List<EntityMetadata> buildConcreteChain(EntityMetadata entityMetadata) {
        List<EntityMetadata> fullChain = new ArrayList<>();
        fullChain.add(entityMetadata);

        EntityMetadata current = entityMetadata;
        while (current.getInheritanceMetadata().getParent() != null) {
            current = current.getInheritanceMetadata().getParent();
            fullChain.add(current);
        }
        Collections.reverse(fullChain);

        // Filter to only non-abstract classes
        List<EntityMetadata> concreteChain = new ArrayList<>();
        for (EntityMetadata meta : fullChain) {
            if (!meta.isAbstract()) {
                concreteChain.add(meta);
            }
        }
        return concreteChain;
    }

    @Override
    public Object insert(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            Long generatedId = null;
            // Only insert into non-abstract tables
            List<EntityMetadata> concreteChain = buildConcreteChain();

            assert entityMetadata != null;
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

            List<PropertyMetadata> idColumns = new ArrayList<>(root.getIdColumns().values());
            Set<String> idProvided = getProvidedIds(entity);
            String idProp = getIdNameAndCheckCompositeKey(idProvided, idColumns);

            for (int i = 0; i < concreteChain.size(); i++) {
                EntityMetadata meta = concreteChain.get(i);
                List<String> columns = new ArrayList<>();
                List<Object> values = new ArrayList<>();

                boolean isFirstConcreteInChain = (i == 0);

                for (PropertyMetadata prop : meta.getProperties().values()) {
                    // For the first concrete class, properties include merged abstract
                    // parent fields; use hierarchy check instead of DeclaredField check
                    if (isFirstConcreteInChain) {
                        if (!fieldExistsInHierarchy(prop, meta.getEntityClass())) {
                            continue;
                        }
                    } else {
                        if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                            continue;
                        }
                    }

                    // ID auto-increment support for first concrete table
                    if (isFirstConcreteInChain && prop.isId() && prop.isAutoIncrement()) {
                        continue;
                    }

                    Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                    if (value != null) {
                        columns.add(prop.getColumnName());
                        values.add(value);
                    }
                }

                // DTYPE only in first concrete table
                if (isFirstConcreteInChain) {
                    columns.add("DTYPE");
                    values.add(root.getInheritanceMetadata().getClassToDiscriminator()
                            .get(entityMetadata.getEntityClass()));
                }

                // FK for non-first concrete tables
                if (!isFirstConcreteInChain) {
                    if (generatedId == null) {
                        throw new RuntimeException("Generated ID is null but trying to insert into child table!");
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
                sql.deleteCharAt(sql.length() - 1);
                sql.append(")");

                System.out.println(
                        "ConcreteClass Insert SQL (" + (isFirstConcreteInChain ? "FIRST" : "CHILD") + "): " + sql);
                System.out.println("Values: " + values);

                Long currentResult = jdbc.insert(sql.toString(), isFirstConcreteInChain ? idProp : "",
                        values.toArray());

                if (isFirstConcreteInChain) {
                    int numOfIds = meta.getIdColumns().size();
                    if (numOfIds == 1) {
                        generatedId = currentResult;
                        PropertyMetadata idPropName = meta.getIdColumns().values().iterator().next();
                        if (idPropName.isAutoIncrement()) {
                            System.out.println("Setting id in " + entity + " value: " + generatedId);
                            ReflectionUtils.setFieldValue(entity, idPropName.getName(), generatedId);
                        }
                    }
                }
            }

            // association tables
            insertAssociationTables(jdbc, entity);

            return generatedId;

        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity with concrete class strategy: " + entity, e);
        }
    }

    @Override
    public void update(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<EntityMetadata> concreteChain = buildConcreteChain();

            for (int i = 0; i < concreteChain.size(); i++) {
                EntityMetadata meta = concreteChain.get(i);
                boolean isFirstConcreteInChain = (i == 0);
                List<String> setColumns = new ArrayList<>();
                List<Object> values = new ArrayList<>();

                for (PropertyMetadata prop : meta.getProperties().values()) {
                    if (isFirstConcreteInChain) {
                        if (!fieldExistsInHierarchy(prop, meta.getEntityClass())) {
                            continue;
                        }
                    } else {
                        if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                            continue;
                        }
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
                // Use the first concrete class as the reference for ID lookup
                EntityMetadata firstConcrete = concreteChain.get(0);
                Object idValue = getIdValue(entity);
                String whereClause = buildWhereClause(firstConcrete);
                Object[] idParams = prepareIdParams(idValue);

                List<Object> allParams = new ArrayList<>(values);
                allParams.addAll(Arrays.asList(idParams));

                StringBuilder sql = new StringBuilder();
                sql.append("UPDATE ").append(meta.getTableName())
                        .append(" SET ").append(String.join(" = ?, ", setColumns))
                        .append(setColumns.isEmpty() ? "" : " = ?")
                        .append(" WHERE ").append(whereClause);

                System.out.println("ConcreteClass UPDATE SQL (" + meta.getTableName() + "): " + sql);
                System.out.println("Values: " + allParams);

                jdbc.update(sql.toString(), allParams.toArray());
            }

            // association tables
            updateAssociationTables(jdbc, entity);
        } catch (Exception e) {
            throw new RuntimeException("Error updating entity with concrete class strategy: " + entity, e);
        }
    }

    @Override
    public void delete(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<EntityMetadata> chain = buildConcreteChain();

            // DELETE from child to root to respect FK constraints
            Collections.reverse(chain);

            assert entityMetadata != null;
            EntityMetadata firstConcrete = chain.get(chain.size() - 1); // after reverse, first concrete is last
            Object idValue = getIdValue(entity);
            Object[] idParams = prepareIdParams(idValue);

            for (EntityMetadata meta : chain) {
                String whereClause = buildWhereClause(firstConcrete);

                StringBuilder sql = new StringBuilder();
                sql.append("DELETE FROM ").append(meta.getTableName())
                        .append(" WHERE ").append(whereClause);

                System.out.println("ConcreteClass DELETE SQL (" + meta.getTableName() + "): " + sql);
                System.out.println("ID: " + idValue);

                jdbc.update(sql.toString(), idParams);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error deleting entity with concrete class strategy: " + entity, e);
        }
    }

    @Override
    public Object findById(Object id, Session session) {
        try {
            assert entityMetadata != null;
            if (entityMetadata.isAbstract()) {
                List<EntityMetadata> concreteSubclasses = buildAbstractChain();
                for (EntityMetadata meta : concreteSubclasses) {
                    SqlAndParams query = buildPolymorphicQuery(meta, id);

                    System.out.println("ConcreteClass findById SQL: " + query.sql);

                    JdbcExecutor jdbc = session.getJdbcExecutor();
                    Object result = jdbc.queryOne(query.sql, this::mapRow, query.params.toArray()).orElse(null);

                    if (result != null) {
                        return result;
                    }
                }
                return null;
            } else {
                SqlAndParams query = buildPolymorphicQuery(entityMetadata, id);

                System.out.println("ConcreteClass findById SQL: " + query.sql);

                JdbcExecutor jdbc = session.getJdbcExecutor();
                return jdbc.queryOne(query.sql, this::mapRow, query.params.toArray()).orElse(null);
            }

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
            if (entityMetadata.isAbstract()) {
                List<T> result = new ArrayList<>();
                List<EntityMetadata> concreteSubclasses = buildAbstractChain();
                for (EntityMetadata meta : concreteSubclasses) {
                    result.addAll(findAllSub(type, session, joinStmt, whereStmt, meta));
                }
                return result;
            } else {
                return findAllSub(type, session, joinStmt, whereStmt, entityMetadata);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error finding entities in ConcreteClass Strategy", e);
        }
    }

    private <T> List<T> findAllSub(Class<T> type, Session session, TargetStatement joinStmt, TargetStatement whereStmt, EntityMetadata entityMetadata) {
        SqlAndParams query = buildPolymorphicQuery(entityMetadata, null);

        query.sql += " " + joinStmt.getStatement();

        if (!whereStmt.isBlank()) {
            query.sql += " WHERE " + whereStmt.getStatement();
        }

        System.out.println("ConcreteClass findAll SQL: " + query.sql);

        JdbcExecutor jdbc = session.getJdbcExecutor();
        List<Object> results = jdbc.query(query.sql, this::mapRow);

        List<T> filtered = new ArrayList<>();
        for (Object obj : results) {
            if (type.isInstance(obj)) {
                filtered.add(type.cast(obj));
            }
        }

        return filtered;
    }

    @Override
    public <T> List<T> findBy(Class<T> type, Session session, QuerySpec<T> querySpec) {
        assert entityMetadata != null;
        try {
            if (entityMetadata.isAbstract()) {
                List<T> result = new ArrayList<>();
                List<EntityMetadata> concreteSubclasses = buildAbstractChain();
                for (EntityMetadata meta : concreteSubclasses) {
                    result.addAll(findBySub(type, session, querySpec, meta));
                }
                return result;
            } else {
                return findBySub(type, session, querySpec, entityMetadata);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error finding entities with QuerySpec in ConcreteClass Strategy", e);
        }
    }

    private <T> List<T> findBySub(Class<T> type, Session session, QuerySpec<T> querySpec, EntityMetadata entityMetadata) {
        SqlAndParams query = buildPolymorphicQuery(entityMetadata, null);

        List<Object> params = new ArrayList<>(query.params);

        String querySpecWhere = buildConcreteQuerySpecWhereClause(querySpec, params, entityMetadata);
        if (!querySpecWhere.isEmpty()) {
            if (query.sql.contains(" WHERE ")) {
                query.sql += " AND " + querySpecWhere;
            } else {
                query.sql += " WHERE " + querySpecWhere;
            }
        }

        String orderBy = buildConcreteQuerySpecOrderByClause(querySpec);
        if (!orderBy.isEmpty()) {
            query.sql += " ORDER BY " + orderBy;
        }

        query.sql += buildQuerySpecLimitOffsetClause(querySpec);

        System.out.println("ConcreteClass Finder SQL: " + query.sql);
        System.out.println("ConcreteClass Finder params: " + params);

        JdbcExecutor jdbc = session.getJdbcExecutor();
        List<Object> results = jdbc.query(query.sql, this::mapRow, params.toArray());

        List<T> filtered = new ArrayList<>();
        for (Object obj : results) {
            if (type.isInstance(obj)) {
                filtered.add(type.cast(obj));
            }
        }
        return filtered;
    }

    // ==================== Helper methods ====================

    private String findTableForField(String fieldName) {
        List<EntityMetadata> allConcrete = getAllConcreteSubclassesIncludingRoot(
                entityMetadata.getInheritanceMetadata().getRootClass());

        for (EntityMetadata meta : allConcrete) {
            if (meta.getProperties().containsKey(fieldName)) {
                return meta.getTableName();
            }
            if (meta.getIdColumns().containsKey(fieldName)) {
                return meta.getTableName();
            }
        }
        // Default to first concrete class
        return allConcrete.get(0).getTableName();
    }

    private String resolveColumnName(String fieldName) {
        List<EntityMetadata> allConcrete = getAllConcreteSubclassesIncludingRoot(
                entityMetadata.getInheritanceMetadata().getRootClass());

        for (EntityMetadata meta : allConcrete) {
            PropertyMetadata pm = meta.getProperties().get(fieldName);
            if (pm != null) {
                return pm.getColumnName();
            }
            pm = meta.getIdColumns().get(fieldName);
            if (pm != null) {
                return pm.getColumnName();
            }
        }
        return fieldName;
    }

    private <T> String buildConcreteQuerySpecWhereClause(QuerySpec<T> querySpec, List<Object> params, EntityMetadata entityMetadata) {
        if (!querySpec.hasConditions()) {
            return "";
        }

        List<String> sqlConditions = new ArrayList<>();
        for (Condition condition : querySpec.getConditions()) {
            String fieldName = condition.getField();
//            String tableName = findTableForField(fieldName);
            String tableName = entityMetadata.getTableName();
            String columnName = resolveColumnName(fieldName);

            String sql = condition.toSql(tableName)
                    .replace(tableName + "." + fieldName, tableName + "." + columnName);
            sqlConditions.add(sql);
            params.addAll(condition.getParams());
        }

        return String.join(" AND ", sqlConditions);
    }

    private <T> String buildConcreteQuerySpecOrderByClause(QuerySpec<T> querySpec) {
        if (!querySpec.hasSorting()) {
            return "";
        }

        List<String> sortClauses = new ArrayList<>();
        for (Sort sort : querySpec.getSortings()) {
            String fieldName = sort.getField();
            String tableName = findTableForField(fieldName);
            String columnName = resolveColumnName(fieldName);
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

    private List<EntityMetadata> buildAbstractChain() {
        List<EntityMetadata> fullChain = new ArrayList<>();
        Deque<EntityMetadata> stack = new ArrayDeque<>();

        assert entityMetadata != null;
        if (!entityMetadata.isAbstract()) {
            fullChain.add(entityMetadata);
            return fullChain;
        }

        stack.push(entityMetadata);

        while (!stack.isEmpty()) {
            EntityMetadata meta = stack.pop();
            List<EntityMetadata> children = meta.getInheritanceMetadata().getChildren();
            for (EntityMetadata child : children) {
                if (child.isAbstract()) {
                    stack.push(child);
                } else {
                    fullChain.add(child);
                }
            }
        }
        return fullChain;
    }

    private List<EntityMetadata> buildConcreteLeaf(EntityMetadata entityMetadata) {
        List<EntityMetadata> fullChain = new ArrayList<>();
        Deque<EntityMetadata> stack = new ArrayDeque<>();

        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        EntityMetadata nonAbstractRoot = entityMetadata;
        while (nonAbstractRoot != root && !nonAbstractRoot.getInheritanceMetadata().getParent().isAbstract()) {
            nonAbstractRoot = nonAbstractRoot.getInheritanceMetadata().getParent();
        }
        fullChain.add(nonAbstractRoot);
        stack.push(nonAbstractRoot);

        while (!stack.isEmpty()) {
            EntityMetadata meta = stack.pop();
            List<EntityMetadata> children = meta.getInheritanceMetadata().getChildren();
            stack.addAll(children);
            for (EntityMetadata child : children) {
                if (!child.isAbstract()) {
                    fullChain.add(child);
                }
            }
        }
        return fullChain;
    }

    /**
     * Build a polymorphic query that joins only non-abstract tables.
     * Abstract class fields are already present in the first concrete class's
     * table.
     */
    private SqlAndParams buildPolymorphicQuery(EntityMetadata entityMetadata, Object id) {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        List<EntityMetadata> allConcrete = getAllConcreteSubclassesIncludingRoot(root);
        List<EntityMetadata> concreteChain = buildConcreteLeaf(entityMetadata);
        if (!concreteChain.isEmpty()) {
            allConcrete = concreteChain;
        }

        // The "base" table is the first concrete class (which has merged abstract
        // fields)
        EntityMetadata baseTable = allConcrete.get(0);

        StringBuilder selectPart = new StringBuilder("SELECT ");
        StringBuilder joinPart = new StringBuilder(" FROM " + baseTable.getTableName());

        List<String> columns = new ArrayList<>();

        // Column list with aliases from each concrete table
        for (EntityMetadata meta : allConcrete) {
            String discriminatorColumnName = meta.getInheritanceMetadata().getDiscriminatorColumnName();
            for (PropertyMetadata prop : meta.getProperties().values()) {
                if (prop.getSqlType() != null) {
                    String columnName = prop.getColumnName();
                    if (discriminatorColumnName == columnName) {
                        columnName = "DTYPE";
                    } else {
                        columnName = meta.getTableName() + "_" + columnName;
                    }
                    columns.add(meta.getTableName() + "." + prop.getColumnName() +
                            " AS " + columnName);
                }
            }
        }

        selectPart.append(String.join(", ", columns));

        // LEFT JOINs between concrete tables only
        for (EntityMetadata sub : allConcrete) {
            if (sub == baseTable)
                continue;

            // Find the nearest concrete parent for this sub
            EntityMetadata concreteParent = sub.findNearestConcreteParent();
            if (concreteParent == null) {
                concreteParent = baseTable;
            }

            String pkName = baseTable.getIdColumns().values().iterator().next().getColumnName();

            joinPart.append(" LEFT JOIN ").append(sub.getTableName())
                    .append(" ON ")
                    .append(concreteParent.getTableName()).append(".").append(pkName)
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
                joinPart.append(baseTable.getTableName()).append(".").append(pm.getColumnName()).append(" = ?");
                params.add(((Class<?>)pm.getType()).cast(id));
            } else {
                int count = 0;
                for (PropertyMetadata pm : idColumns) {
                    if (count > 0)
                        joinPart.append(" AND ");
                    joinPart.append(baseTable.getTableName()).append(".").append(pm.getColumnName()).append(" = ?");
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
            List<EntityMetadata> allConcrete = getAllConcreteSubclassesIncludingRoot(root);
//            List<EntityMetadata> concreteChain = buildConcreteChain();
//            if (!concreteChain.isEmpty()) {
//                allConcrete = concreteChain;
//            }
            EntityMetadata baseTable = allConcrete.get(0);

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
            throw new RuntimeException("Error mapping row in ConcreteClass strategy", e);
        }
    }

    private void populateFieldsWithAliases(Object instance, ResultSet rs, Class<?> realClass) throws SQLException {
        EntityMetadata currentMeta = findMetadataForClass(realClass);

        // Walk upward through the hierarchy, but only read from non-abstract tables
        while (currentMeta != null) {
            if (!currentMeta.isAbstract()) {
                for (PropertyMetadata prop : currentMeta.getProperties().values()) {
                    if (prop.getSqlType() == null)
                        continue;
                    if (prop.getName() == null)
                        continue;
                    if (Objects.equals(prop.getColumnName(), "DTYPE"))
                        continue;

                    String columnAlias = currentMeta.getTableName() + "_" + prop.getColumnName();

                    try {
                        String fieldName = prop.getName();
                        Object value = getValueFromResultSet(rs, columnAlias, prop.getType());

                        if (value != null) {
                            Object castedValue = castSqlValueToJava((Class<?>) prop.getType(), value);
                            ReflectionUtils.setFieldValue(instance, fieldName, castedValue);
                        }
                    } catch (SQLException e) {
                        // ignore - column may not exist in this result set
                    }
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

    /**
     * Get all non-abstract subclasses (including root if not abstract) via BFS.
     */
    private List<EntityMetadata> getAllConcreteSubclassesIncludingRoot(EntityMetadata root) {
        List<EntityMetadata> list = new ArrayList<>();
        if (!root.isAbstract()) {
            list.add(root);
        }

        List<EntityMetadata> toProcess = new ArrayList<>(root.getInheritanceMetadata().getChildren());
        while (!toProcess.isEmpty()) {
            EntityMetadata current = toProcess.remove(0);
            if (!current.isAbstract()) {
                list.add(current);
            }
            toProcess.addAll(current.getInheritanceMetadata().getChildren());
        }
        return list;
    }

    private List<EntityMetadata> getConcreteSubclassesIncludingRootInOneLeaf(EntityMetadata root) {
        List<EntityMetadata> list = new ArrayList<>();
        if (!root.isAbstract()) {
            list.add(root);
        }

        List<EntityMetadata> toProcess = new ArrayList<>(root.getInheritanceMetadata().getChildren());
        while (!toProcess.isEmpty()) {
            EntityMetadata current = toProcess.remove(0);
            if (!current.isAbstract()) {
                list.add(current);
            }
            toProcess.addAll(current.getInheritanceMetadata().getChildren());
        }
        return list;
    }

    private EntityMetadata findMetadataForClass(Class<?> clazz) {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        if (root.getEntityClass().equals(clazz))
            return root;

        // BFS search
        List<EntityMetadata> queue = new ArrayList<>(root.getInheritanceMetadata().getChildren());
        while (!queue.isEmpty()) {
            EntityMetadata current = queue.remove(0);
            if (current.getEntityClass().equals(clazz))
                return current;
            queue.addAll(current.getInheritanceMetadata().getChildren());
        }
        return null;
    }

    /**
     * Check if a field exists anywhere in the class hierarchy (including
     * superclasses).
     * Unlike fieldBelongsToClass which uses getDeclaredField (only direct fields),
     * this walks up the hierarchy to find inherited fields from abstract parents.
     */
    private boolean fieldExistsInHierarchy(PropertyMetadata prop, Class<?> clazz) {
        if (prop.getColumnName().equals("DTYPE")) {
            return false;
        }
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                current.getDeclaredField(prop.getName());
                return true;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return false;
    }
}
