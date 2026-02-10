package pl.edu.agh.dp.core.schema;


import javafx.util.Pair;
import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.jdbc.JdbcExecutorImpl;
import pl.edu.agh.dp.core.mapping.*;
import pl.edu.agh.dp.core.persister.EntityPersister;

import java.util.ArrayList;
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
        this.jdbcExecutor = new JdbcExecutorImpl(this.connectionProvider.getConnection());
        this.entityPersisters = entityPersisters;
    }

    public void generate() {
        List<EntityMetadata> sortedEntities = new ArrayList<>(registry.getEntities().values());

        sortedEntities.sort((m1, m2) -> {
            Class<?> c1 = m1.getEntityClass();
            Class<?> c2 = m2.getEntityClass();
            return Integer.compare(getInheritanceDepth(c1), getInheritanceDepth(c2));
        });

        List<String> constraints = new ArrayList<>();

        for (EntityMetadata metadata : sortedEntities) {
            EntityPersister entityPersister = entityPersisters.get(metadata.getEntityClass());

            if (entityPersister != null) {
                // Pair<Table sql, Alter table sql>
                Pair<String, String> sqlConstraint = entityPersister.getInheritanceStrategy().create();
                if (sqlConstraint == null) {
                    continue;
                }
                String sql = sqlConstraint.getKey();
                constraints.add(sqlConstraint.getValue());

                System.out.println("Executing SQL for " + metadata.getEntityClass().getSimpleName() + ": " + sql);
                jdbcExecutor.executeStatement(sql);
            }
        }

        for (String constraint : constraints) {
            System.out.println("Executing SQL for " + constraint);
            jdbcExecutor.executeStatement(constraint);
        }
    }

    private int getInheritanceDepth(Class<?> clazz) {
        int depth = 0;
        while (clazz != null && clazz != Object.class) {
            depth++;
            clazz = clazz.getSuperclass();
        }
        return depth;
    }

}
