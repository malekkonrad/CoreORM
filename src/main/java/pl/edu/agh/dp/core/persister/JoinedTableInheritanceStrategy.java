package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.InheritanceMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class JoinedTableInheritanceStrategy extends AbstractInheritanceStrategy {

    public JoinedTableInheritanceStrategy(EntityMetadata entityMetadata) {
        super(entityMetadata);
    }

    @Override
    public String create(JdbcExecutor jdbcExecutor) {
        StringBuilder sb = new StringBuilder();

        // Create table for this entity
        sb.append("CREATE TABLE ").append(entityMetadata.getTableName()).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();

        // Add columns defined in this class only (not inherited ones)
        for (PropertyMetadata prop : entityMetadata.getProperties().values()) {
            columnDefs.add("    " + prop.getColumnName() + " " + prop.getSqlType());

            if (prop.isId()) {
                primaryKeys.add(prop.getColumnName());
            }
        }

        sb.append(String.join(",\n", columnDefs));

        // Add primary key constraint
        if (!primaryKeys.isEmpty()) {
            sb.append(",\n    PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        }

        // Add foreign key to parent table if this is a child entity
        InheritanceMetadata inhMeta = entityMetadata.getInheritanceMetadata();
        // change not parent but root - root must have id, but this id will point to parent
        if (inhMeta.getRootClass() != null && !inhMeta.isRoot()) {
            EntityMetadata root = inhMeta.getRootClass();

            if (inhMeta.getParent() != null) {
                EntityMetadata parent = inhMeta.getParent();

                // Foreign key references parent's primary key
                Collection<PropertyMetadata> rootIds = root.getIdColumns().values();
                List<String> fkColumns = new ArrayList<>();
                List<String> refColumns = new ArrayList<>();

                for (PropertyMetadata idProp : rootIds) {
                    fkColumns.add(idProp.getColumnName());
                    refColumns.add(idProp.getColumnName());
                }

                // adding ids to parent and root table
                for (String fkColumn : fkColumns) {
                    String sqlType = root.getIdColumns().get(fkColumn).getSqlType();
                    sb.append(",\n    ").append(fkColumn).append(" ").append(sqlType);
                }

                sb.append(",\n    PRIMARY KEY (")
                        .append(String.join(", ", fkColumns))
                        .append(")");


                sb.append(",\n    FOREIGN KEY (")
                        .append(String.join(", ", fkColumns))
                        .append(") REFERENCES ")
                        .append(parent.getTableName())
                        .append(" (")
                        .append(String.join(", ", refColumns))
                        .append(")");
            }

        }

        sb.append("\n);");

        return sb.toString();
    }

    private List<EntityMetadata> buildInheritanceChain() {
        List<EntityMetadata> visitingChain =  new ArrayList<>();
        visitingChain.add(entityMetadata);

        EntityMetadata current = entityMetadata;
        while(current.getInheritanceMetadata().getParent() != null) {
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
            List<EntityMetadata> visitingChain =  buildInheritanceChain();

            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

            for (EntityMetadata meta : visitingChain) {
                List<String> columns = new ArrayList<>();
                List<Object> values = new ArrayList<>();

                boolean isRoot = meta.getInheritanceMetadata().isRoot();
                Collection<PropertyMetadata> idProps = meta.getIdColumns().values();

                for (PropertyMetadata prop : meta.getProperties().values()) {
                    // Check if field belongs to this specific class (not inherited)
                    if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                        continue;
                    }

                    columns.add(prop.getColumnName());

                    if (prop.isId() && !isRoot && generatedId != null) {
                        // Use ID from parent insert
                        values.add(generatedId);
                    } else {
                        Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                        values.add(value);
                    }
                }

                if (!isRoot) {
                    columns.add(root.getIdColumns().values().iterator().next().getColumnName());
                    values.add(generatedId);
                }


                if (columns.isEmpty()) {
                    continue; // Skip if no columns to insert
                }

                StringBuilder sql = new StringBuilder();
                sql.append("INSERT INTO ")
                        .append(meta.getTableName())
                        .append(" (")
                        .append(String.join(", ", columns))
                        .append(") VALUES (")
                        .append("?,".repeat(values.size()));
                sql.deleteCharAt(sql.length() - 1);
                sql.append(")");

                System.out.println("Joined Insert SQL: " + sql);
                System.out.println("Values: " + values);

                Long currentId = jdbc.insert(sql.toString(), values.toArray());

                // Store generated ID from root insert
                if (isRoot) {
                    generatedId = currentId;

                    // Set ID on entity if auto-increment
                    for (PropertyMetadata idProp : idProps) {
                        if (idProp.isAutoIncrement()) {
                            ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);
                        }
                    }
                }

            }
            return generatedId;
        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity with joined table strategy: " + entity, e);
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
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
//
            // Build JOIN query across all tables in hierarchy
            String sql = buildJoinQuery();

            // Get ID column name from root
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
            PropertyMetadata idColumn = root.getIdColumns().values().iterator().next();

            sql += " WHERE " + root.getTableName() + "." + idColumn.getColumnName() + " = ?";

            System.out.println("Joined FindById SQL: " + sql);

            return jdbc.queryOne(sql, this::mapEntity, id).orElse(null);
//            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session) {
        return List.of();
    }


    private boolean fieldBelongsToClass(PropertyMetadata prop, Class<?> targetClass) {
        try {
            targetClass.getDeclaredField(prop.getName());
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private String buildJoinQuery() {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        List<EntityMetadata> chain = buildInheritanceChain();

        // SELECT columns from all tables
        List<String> selectColumns = new ArrayList<>();

        for (EntityMetadata meta : chain) {
            for (PropertyMetadata prop : meta.getProperties().values()) {
                if (fieldBelongsToClass(prop, meta.getEntityClass())) {
                    selectColumns.add(meta.getTableName() + "." + prop.getColumnName() +
                            " AS " + meta.getTableName() + "_" + prop.getColumnName());
                }
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", selectColumns));
        sql.append("\nFROM ").append(root.getTableName());

        // Build JOINs from root down to current entity
        for (int i = 1; i < chain.size(); i++) {
            EntityMetadata child = chain.get(i);
            EntityMetadata parent = child.getInheritanceMetadata().getParent();

            PropertyMetadata idProp = parent.getIdColumns().values().iterator().next();

            sql.append("\nLEFT JOIN ").append(child.getTableName())
                    .append(" ON ").append(parent.getTableName()).append(".").append(idProp.getColumnName())
                    .append(" = ").append(child.getTableName()).append(".").append(idProp.getColumnName());
        }
        return sql.toString();
    }

    private Object mapEntity(ResultSet rs) throws SQLException {
        try {
            // Determine actual class from available data
            Class<?> actualClass = determineActualClass(rs);

            Object entity = actualClass.getDeclaredConstructor().newInstance();

            // Map fields from all tables in hierarchy
            List<EntityMetadata> chain = buildInheritanceChainForClass(actualClass);

            for (EntityMetadata meta : chain) {
                for (PropertyMetadata prop : meta.getProperties().values()) {
                    if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                        continue;
                    }

                    try {
                        String columnAlias = meta.getTableName() + "_" + prop.getColumnName();
                        Object value = getValueFromResultSet(rs, columnAlias, prop.getType());

                        if (value != null) {
                            ReflectionUtils.setFieldValue(entity, prop.getName(), value);
                        }
                    } catch (SQLException e) {
                        // Column might not exist if LEFT JOIN returned NULL
                    }
                }
            }

            return entity;

        } catch (Exception e) {
            throw new SQLException("Failed to map entity: " + entityMetadata.getEntityClass().getName(), e);
        }
    }

    private Class<?> determineActualClass(ResultSet rs) throws SQLException {
        // Check which tables have data by checking non-null child columns
        List<EntityMetadata> chain = buildInheritanceChain();

        // Start from most specific (leaf) and work up
        for (int i = chain.size() - 1; i >= 0; i--) {
            EntityMetadata meta = chain.get(i);

            // Check if any non-ID column from this table has data
            for (PropertyMetadata prop : meta.getProperties().values()) {
                if (!prop.isId() && fieldBelongsToClass(prop, meta.getEntityClass())) {
                    try {
                        String columnAlias = meta.getTableName() + "_" + prop.getColumnName();
                        Object value = rs.getObject(columnAlias);
                        if (value != null) {
                            return meta.getEntityClass();
                        }
                    } catch (SQLException e) {
                        // Continue checking
                    }
                }
            }
        }

        // Default to root class
        return entityMetadata.getInheritanceMetadata().getRootClass().getEntityClass();
    }



    private Object getValueFromResultSet(ResultSet rs, String columnName, Class<?> type) throws SQLException {
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

        return rs.getObject(columnName);
    }

    private List<EntityMetadata> buildInheritanceChainForClass(Class<?> clazz) {
        // Find metadata for this class and build its chain
        EntityMetadata meta = findMetadataForClass(clazz);
        if (meta == null) {
            return Collections.emptyList();
        }

        List<EntityMetadata> chain = new ArrayList<>();
        EntityMetadata current = meta;

        while (current != null) {
            chain.add(0, current);
            current = current.getInheritanceMetadata().getParent();
        }

        return chain;
    }

    private EntityMetadata findMetadataForClass(Class<?> clazz) {
        // Search through hierarchy
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        if (root.getEntityClass().equals(clazz)) {
            return root;
        }

        Deque<EntityMetadata> queue = new ArrayDeque<>(root.getInheritanceMetadata().getChildren());
        while (!queue.isEmpty()) {
            EntityMetadata meta = queue.poll();
            if (meta.getEntityClass().equals(clazz)) {
                return meta;
            }
            queue.addAll(meta.getInheritanceMetadata().getChildren());
        }

        return null;
    }


}
