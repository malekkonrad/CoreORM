package pl.edu.agh.dp.core.persister;

import lombok.NoArgsConstructor;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.Dialect;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.jdbc.JdbcExecutorImpl;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class SingleTableInheritanceStrategy extends AbstractInheritanceStrategy {

    public SingleTableInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
    }


    @Override
    public String create(JdbcExecutor jdbcExecutor) {
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
    public Object insert(EntityMetadata rootMetadata, Object entity, Session session) {
        return null;
    }

    @Override
    public void update(EntityMetadata rootMetadata, Object entity, Session session) {

    }

    @Override
    public void delete(EntityMetadata rootMetadata, Object entity, Session session) {

    }

    @Override
    public <T> T findById(EntityMetadata rootMetadata, Class<T> type, Object id, Session session) {
        return null;
    }

    @Override
    public <T> List<T> findAll(EntityMetadata rootMetadata, Class<T> type, Session session) {
        return List.of();
    }

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
}
