package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;

import java.util.ArrayList;
import java.util.List;

public class TablePerClassInheritanceStrategy extends AbstractInheritanceStrategy{

    public TablePerClassInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
    }


    @Override
    public String create(JdbcExecutor jdbcExecutor) {
        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE ").append(this.entityMetadata.getTableName()).append(" (\n");

        List<String> columnDefs = new ArrayList<>();

        for (PropertyMetadata col : this.entityMetadata.getColumnsForConcreteTable()) {
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
}
