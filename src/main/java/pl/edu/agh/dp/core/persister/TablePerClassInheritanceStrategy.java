package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.finder.QuerySpec;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.*;
import pl.edu.agh.dp.core.util.ReflectionUtils;
import javafx.util.Pair;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class TablePerClassInheritanceStrategy extends AbstractInheritanceStrategy{

    public TablePerClassInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
    }

    @Override
    public PairTargetStatements getPairStatement(Object entity, String relationshipName) {
        assert entityMetadata != null;
        AssociationMetadata associationMetadata = entityMetadata.getAssociationMetadata().get(relationshipName);

        TargetStatement joinStmt = associationMetadata.getJoinStatement();
        TargetStatement whereStmt = entityMetadata.getSelectByIdStatement(entity);
        // table per class is it's own root
        whereStmt.setTargetTableName(entityMetadata.getTableName());
        whereStmt.setTargetTableName(joinStmt.getRootTableName());

        return new PairTargetStatements(whereStmt, joinStmt);
    }

    @Override
    public Pair<String, String> create() {
        assert this.entityMetadata != null;

        StringBuilder sb = new StringBuilder();

        EntityMetadata rootMetadata = entityMetadata.getInheritanceMetadata().getRootClass();

        if (entityMetadata.getIdColumns().isEmpty()) {
            throw new IntegrityException("No id"); // sanity check
        } else if (entityMetadata.getIdColumns().size() == 1) {
            PropertyMetadata idProperty = entityMetadata.getIdColumns().values().iterator().next();
            // must be autoincrement
            if (!idProperty.isAutoIncrement()) {
                throw new IntegrityException("Id column must be auto-increment");
            }
        } else if (entityMetadata != rootMetadata) {
            throw new IntegrityException("Complex id not supported for Table per class inheritance");
        }

        // create sequence for all the subclasses to have the same id
        String rootTableName = rootMetadata.getTableName();
        String sequenceName = rootTableName + "_id_seq";
        if (rootMetadata == entityMetadata) {
            // create sequence only once
            String seq =  "CREATE SEQUENCE " + sequenceName + " START 1 INCREMENT 1;\n";
            sb.append(seq);
        }
        // change default in id to the sequence
        PropertyMetadata rootId = entityMetadata.getIdColumns().values().iterator().next();
        rootId.setSqlType("BIGINT");
        rootId.setDefaultValue("nextval('" + sequenceName + "')");
        // add table to the creation
        if (!entityMetadata.isAbstract()) {
            sb.append(entityMetadata.getSqlTable());
        }
        // return table and but skip the constraints cause it's TPC
        return new Pair<>(sb.toString(), "");
    }

    @Override
    public Object insert(Object entity, Session session) {
        System.out.println("Inserting: " + entity.getClass().getName());

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        assert entityMetadata != null;

        for (PropertyMetadata pm : entityMetadata.getProperties().values()) {
            Object value = ReflectionUtils.getFieldValue(entity, pm.getName());
            if (value != null) {
                columns.add(pm.getColumnName());
                values.add(value);
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
                .append(entityMetadata.getTableName())
                .append(" (")
                .append(String.join(", ", columns))
                .append(") VALUES (")
                .append("?,".repeat(values.size()));
        if (!values.isEmpty()) sql.deleteCharAt(sql.length() - 1); // sanity check if inserting nothing
        sql.append(")");

        System.out.println(sql.toString());
        System.out.println(values.toString());

        Long generatedId;
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            generatedId = jdbc.insert(sql.toString(), idProp, values.toArray());
            System.out.println("Generated ID: " + generatedId);

            int numOfIds = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().size();
            if (numOfIds == 1) {        // we have one key if there's more then for sure it's not autoincrement
                PropertyMetadata idPropName = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().values().iterator().next();
                if (idPropName.isAutoIncrement()) {
                    System.out.println("seting id in " + entity.toString()+ " value: " + generatedId);
                    ReflectionUtils.setFieldValue(entity, idPropName.getName(), generatedId);
                }
            }

            // association tables
            insertAssociationTables(jdbc, entity);

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
        for (PropertyMetadata prop : entityMetadata.getProperties().values()) {
            if (prop.isId()) {
                continue; // ID nie jest aktualizowane
            }

            setColumns.add(prop.getColumnName());
            Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
            values.add(value);
        }

        fillRelationshipData(entity, entityMetadata, setColumns, values);

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(tableName)
                .append(" SET ").append(String.join(" = ?, ", setColumns)).append(setColumns.isEmpty() ? "" : " = ?")
                .append(" WHERE ").append(entityMetadata.getSelectByIdStatement(entity).getStatement(tableName));

        System.out.println("TablePerClass UPDATE SQL: " + sql);
        System.out.println("Values: " + values);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            jdbc.update(sql.toString(), values.toArray());

            // association tables
            updateAssociationTables(jdbc, entity);
        } catch (Exception e) {
            throw new RuntimeException("Error updating entity " + entity, e);
        }
    }

    @Override
    public void delete(Object entity, Session session) {
        assert entityMetadata != null;
        String tableName = entityMetadata.getTableName();

        String sql = "DELETE FROM " + tableName + " WHERE " + entityMetadata.getSelectByIdStatement(entity).getStatement(tableName);

        System.out.println("TablePerClass DELETE SQL: " + sql);
        System.out.println("ID: " + getIdValue(entity));

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            jdbc.update(sql);
        } catch (Exception e) {
            throw new RuntimeException("Error deleting entity " + entity, e);
        }
    }

    @Override
    public Object findById(Object id, Session session) {
        try {
            // PHASE 1: Find which specific class (table) has this ID
            EntityMetadata concreteMetadata = findConcreteMetadata(id, session);

            // If ID not found in any table
            if (concreteMetadata == null) {
                return null;
            }

            // PHASE 2: Get the full object from the correct table with all fields
            return loadSpecificEntity(concreteMetadata, id, session);

        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    private EntityMetadata findConcreteMetadata(Object id, Session session) throws Exception {
        JdbcExecutor jdbc = session.getJdbcExecutor();
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        Collection<PropertyMetadata> idColumns = root.getIdColumns().values();

        List<EntityMetadata> concreteSubclasses = getAllConcreteSubclasses(entityMetadata);

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // We build a query that checks the presence of ID in each table
        for (int i = 0; i < concreteSubclasses.size(); i++) {
            EntityMetadata subMeta = concreteSubclasses.get(i);

            sql.append("SELECT '")
                    .append(subMeta.getEntityClass().getName())
                    .append("' as DTYPE FROM ")
                    .append(subMeta.getTableName())
                    .append(" WHERE ");

            appendIdWhereClause(sql, params, idColumns, id);

            if (i < concreteSubclasses.size() - 1) {
                sql.append(" UNION ALL ");
            }
        }

        // We execute a query that will return only the class name
        return jdbc.queryOne(sql.toString(), rs -> {
            String className = rs.getString("DTYPE");
            // We are looking for metadata corresponding to the class name
            return concreteSubclasses.stream()
                    .filter(m -> m.getEntityClass().getName().equals(className))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Metadata not found for class: " + className));
        }, params.toArray()).orElse(null);
    }

    private Object loadSpecificEntity(EntityMetadata specificMetadata, Object id, Session session) throws Exception {
        JdbcExecutor jdbc = session.getJdbcExecutor();

        List<String> columns = specificMetadata.getColumnsForConcreteTable()
                .stream()
                .map(PropertyMetadata::getColumnName)
                .collect(Collectors.toList());

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
                .append(String.join(", ", columns))
                .append(" FROM ")
                .append(specificMetadata.getTableName())
                .append(" WHERE ");

        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        Collection<PropertyMetadata> idColumns = root.getIdColumns().values();
        List<Object> params = new ArrayList<>();
        appendIdWhereClause(sql, params, idColumns, id);

        System.out.println("Loading full entity SQL: " + sql);

        return jdbc.queryOne(sql.toString(), rs -> mapSpecificEntity(rs, specificMetadata), params.toArray())
                .orElse(null);
    }

    /**
     * A mapper that can map an object based on the metadata provided,
     * not the data assigned to the strategy.
     */
    private Object mapSpecificEntity(ResultSet rs, EntityMetadata metadata) throws SQLException {
        try {
            Object entity = metadata.getEntityClass().getDeclaredConstructor().newInstance();

            for (PropertyMetadata prop : metadata.getColumnsForConcreteTable()) {
                try {
                    Object value = getValueFromResultSet(rs, prop.getColumnName(), prop.getType());

                    if (value != null) {
                        ReflectionUtils.setFieldValue(entity, prop.getName(), value);
                    }
                } catch (SQLException e) {
                    // ignore
                }
            }
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Error mapping entity " + metadata.getEntityClass().getName(), e);
        }
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session, PairTargetStatements pairTargetStatements) {
        TargetStatement joinStmt = pairTargetStatements.getJoinStatements().get(0);
        TargetStatement whereStmt = pairTargetStatements.getWhereStatements().get(0);

        List<EntityMetadata> concreteSubclasses = getAllConcreteSubclasses(this.entityMetadata);

        Map<String, PropertyMetadata> allProperties = new HashMap<>();
        for (EntityMetadata meta : concreteSubclasses) {
            allProperties.putAll(meta.getProperties());
        }

        // UNION ALL
        StringBuilder sqlBuilder = new StringBuilder();

        for (int i = 0; i < concreteSubclasses.size(); i++) {
            EntityMetadata subMeta = concreteSubclasses.get(i);

            Map<String, PropertyMetadata> subTableProperties = subMeta.getProperties();

            String tableName = subMeta.getTableName();

            sqlBuilder.append("SELECT ");

            List<String> selectionParts = new ArrayList<>();
            for (String fieldName : allProperties.keySet()) {
                PropertyMetadata pm = allProperties.get(fieldName);
                String columnName = pm.getColumnName();
                if (subTableProperties.containsKey(fieldName)) {
                    selectionParts.add(tableName + "." + columnName);
                } else {
                    // aliasing and NULL
                    selectionParts.add("NULL::" + pm.getSqlType() +" AS " + columnName);
                }
            }

            sqlBuilder.append(String.join(", ", selectionParts));

            // DTYPE
            sqlBuilder.append(", '").append(subMeta.getEntityClass().getName()).append("' as DTYPE")
                    .append(" FROM ")
                    .append(tableName);

            // JOIN stmt
            sqlBuilder.append(" ").append(joinStmt.getStatement(tableName));

            // where
            if (!whereStmt.isBlank()) {
                sqlBuilder.append(" WHERE ");
                sqlBuilder.append(whereStmt.getStatement());
            }

            if (i < concreteSubclasses.size() - 1) {
                sqlBuilder.append(" UNION ALL ");
            }
        }

        String sql = sqlBuilder.toString();
        System.out.println("TPC findAll SQL: " + sql);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            return jdbc.query(sql, rs -> mapPolymorphicEntityFull(rs, type, allProperties));
        } catch (Exception e) {
            throw new RuntimeException("Error finding all entities in TPC", e);
        }
    }

    @Override
    public <T> List<T> findBy(Class<T> type, Session session, QuerySpec<T> querySpec) {
        assert entityMetadata != null;
        
        // Get all concrete subclasses for polymorphic query
        List<EntityMetadata> allSubclasses = getAllConcreteSubclasses(entityMetadata);
        
        // Collect all properties from the hierarchy
        Map<String, PropertyMetadata> allProperties = new LinkedHashMap<>();
        for (EntityMetadata sub : allSubclasses) {
            allProperties.putAll(sub.getProperties());
        }
        
        List<Object> allParams = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder();
        boolean first = true;
        
        for (EntityMetadata sub : allSubclasses) {
            if (!first) {
                sqlBuilder.append(" UNION ALL ");
            }
            first = false;
            
            StringBuilder selectPart = new StringBuilder("SELECT ");
            // Add DTYPE column
            selectPart.append("'").append(sub.getEntityClass().getName()).append("' AS DTYPE, ");
            
            // Add columns with NULL padding for missing columns
            List<String> columnDefs = new ArrayList<>();
            for (String fieldName : allProperties.keySet()) {
                PropertyMetadata pm = sub.getProperties().get(fieldName);
                if (pm != null) {
                    columnDefs.add(sub.getTableName() + "." + pm.getColumnName() + " AS " + pm.getColumnName());
                } else {
                    columnDefs.add("NULL AS " + allProperties.get(fieldName).getColumnName());
                }
            }
            selectPart.append(String.join(", ", columnDefs));
            
            StringBuilder fromWhere = new StringBuilder(" FROM " + sub.getTableName());
            
            List<Object> subParams = new ArrayList<>();
            String querySpecWhere = buildQuerySpecWhereClause(querySpec, sub.getTableName(), subParams);
            if (!querySpecWhere.isEmpty()) {
                fromWhere.append(" WHERE ").append(querySpecWhere);
                allParams.addAll(subParams);
            }
            
            sqlBuilder.append(selectPart).append(fromWhere);
        }
        
        // ORDER BY - needs special handling for UNION
        String orderBy = buildQuerySpecOrderByClause(querySpec, entityMetadata.getTableName());
        if (!orderBy.isEmpty()) {
            // For UNION, we need to order by column name without table prefix
            String simpleOrderBy = querySpec.getSortings().stream()
                    .map(sort -> {
                        String columnName = resolveColumnName(sort.getField(), entityMetadata);
                        return columnName + " " + sort.getDirection().name();
                    })
                    .collect(Collectors.joining(", "));
            sqlBuilder.append(" ORDER BY ").append(simpleOrderBy);
        }
        
        // LIMIT/OFFSET
        sqlBuilder.append(buildQuerySpecLimitOffsetClause(querySpec));
        
        String sql = sqlBuilder.toString();
        System.out.println("TPC Finder SQL: " + sql);
        System.out.println("TPC Finder params: " + allParams);
        
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            return jdbc.query(sql, rs -> mapPolymorphicEntityFull(rs, type, allProperties), allParams.toArray());
        } catch (Exception e) {
            throw new RuntimeException("Error finding entities with QuerySpec in TPC", e);
        }
    }

    private <T> T mapPolymorphicEntityFull(ResultSet rs, Class<T> baseType, Map<String, PropertyMetadata> allProperties) throws SQLException {
        try {
            // DTYPE
            String className = rs.getString("DTYPE");
            Class<?> realClass = Class.forName(className);

            Object instance = realClass.getDeclaredConstructor().newInstance();

            for (String fieldName : allProperties.keySet()) {
                PropertyMetadata pm = allProperties.get(fieldName);
                String colName = pm.getColumnName();
                Object value = rs.getObject(colName);

                if (value != null) {
                    Object castedValue = castSqlValueToJava((Class<?>)pm.getType(), value);
                    ReflectionUtils.setFieldValue(instance, fieldName, castedValue);
                }
            }
            return baseType.cast(instance);
        } catch (Exception e) {
            throw new RuntimeException("Error mapping polymorphic entity", e);
        }
    }

    private List<EntityMetadata> getAllConcreteSubclasses(EntityMetadata parent) {
        List<EntityMetadata> result = new ArrayList<>();
        List<EntityMetadata> toVisit = new ArrayList<>();

        toVisit.add(parent);
        if (!parent.isAbstract()) {
            result.add(parent);
        }
        while (!toVisit.isEmpty()) {
            var current =  toVisit.remove(0);
            for (EntityMetadata child: current.getInheritanceMetadata().getChildren()){
                if (!child.isAbstract()) {
                    result.add(child);
                }
                toVisit.add(child);
            }
        }
        return result;
    }

}
