package pl.edu.agh.dp.core.schema;


import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.jdbc.JdbcExecutorImpl;
import pl.edu.agh.dp.core.mapping.*;
import pl.edu.agh.dp.core.persister.EntityPersister;
import pl.edu.agh.dp.core.persister.EntityPersisterImpl;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SchemaGenerator {

    private final MetadataRegistry registry;
    private final ConnectionProvider connectionProvider;
    private final JdbcExecutor jdbcExecutor;
    private final Map<Class<?>, EntityPersister> entityPersisters;

    public SchemaGenerator(MetadataRegistry registry,
                           ConnectionProvider connectionProvider,
                           Map<Class<?>, EntityPersister> entityPersisters) {
        this.registry = registry;
        this.connectionProvider = connectionProvider;

        // FIXME - zastanowić się gdzie to przenieść
        this.jdbcExecutor = new JdbcExecutorImpl(connectionProvider.getConnection());
        this.entityPersisters = entityPersisters;
    }

    public void generate() {

        // creation of tables
        for (EntityPersister entityPersister : entityPersisters.values()) {
            String sql = entityPersister.getInheritanceStrategy().create(jdbcExecutor);
            System.out.println(sql);
            jdbcExecutor.createTable(sql);

        }

            // 2.  tabele pośrednie ManyToMany
//            for (EntityMetadata meta : entities) {
//                for (AssociationMetadata assoc : meta.getAssociationMetadata()) {
//                    if (assoc.getType() == AssociationMetadata.Type.MANY_TO_MANY) {
//                        String joinSql = createJoinTableSql(meta, assoc);
//                        st.executeUpdate(joinSql);
//                    }
//                }
//            }

//        } catch (Exception e) {
//            throw new RuntimeException("Error while generating schema", e);
//        }
    }

    private String createTableSql(EntityMetadata meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ")
                .append(meta.getTableName())
                .append(" (");

        StringBuilder primary_keys = new StringBuilder();
        for (PropertyMetadata pm : meta.getProperties()) {
            sb.append(pm.getColumnName()).append(" ")
                    .append(pm.getSqlType()).append(" ");
            if (pm.isNullable()) sb.append("NULL ");
            else sb.append("NOT NULL ");
            if (pm.isUnique()) sb.append("UNIQUE ");
            if (pm.getDefaultValue() != null) sb.append("DEFAULT ").append(pm.getDefaultValue().toString()); // TODO check if works correctly
            sb.append(", ");
            // append to primary keys separately
            if (pm.isId()) primary_keys.append(pm.getColumnName()).append(", ");
        }
        sb.append("PRIMARY KEY (").append(primary_keys);
        sb.delete(sb.length() - 2, sb.length()); // delete last ", " from primary keys
        sb.append(")");

        // TODO: set index on table
        // TODO: klucze obce dla relacji OneToMany / ManyToOne jeśli chcesz

        sb.append(");");
//        System.out.println(sb);
        return sb.toString();
    }

    ///  FIXME może się przyda jeszcze
    private String createJoinTableSql(EntityMetadata owner,
                                      AssociationMetadata assoc) {
        String joinTable = assoc.getJoinTable();
        // bardzo uproszczone – w realnym kodzie wziąłbyś typy kluczy itp.
        String ownerFk = owner.getTableName() + "_id";
        String targetFk = assoc.getTargetEntity().getSimpleName().toLowerCase() + "_id";

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ")
                .append(joinTable)
                .append(" (")
                .append(ownerFk).append(" BIGINT NOT NULL, ")
                .append(targetFk).append(" BIGINT NOT NULL")
                // można dodać PK złożony, klucze obce itd.
                .append(");");

        return sb.toString();
    }
}
