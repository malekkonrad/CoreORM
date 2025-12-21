package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.InheritanceMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    @Override
    public Object insert(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            Long generatedId = null;
            List<EntityMetadata> visitingChain =  new ArrayList<>();
            visitingChain.add(entityMetadata);

            EntityMetadata current = entityMetadata;
            while(current.getInheritanceMetadata().getParent() != null) {
                visitingChain.add(current.getInheritanceMetadata().getParent());
                current = current.getInheritanceMetadata().getParent();
            }

            EntityMetadata root = current.getInheritanceMetadata().getRootClass();


            // reversed because we have to start in the root
            Collections.reverse(visitingChain);
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
        return null;
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
}
