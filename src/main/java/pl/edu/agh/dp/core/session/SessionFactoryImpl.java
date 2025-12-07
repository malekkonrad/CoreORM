package pl.edu.agh.dp.core.session;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.persister.EntityPersister;
import pl.edu.agh.dp.core.persister.impl.EntityPersisterImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionFactoryImpl {

    MetadataRegistry metadataRegistry;
    ConnectionProvider connectionProvider;
    Map< Class<?>, EntityPersister> entityPersisters = new HashMap<>();

    public SessionFactoryImpl(MetadataRegistry registry,
                              ConnectionProvider connectionProvider) {

        this.metadataRegistry = registry;
        this.connectionProvider = connectionProvider;

        this.metadataRegistry.getEntities().forEach((meta, val) -> {
            entityPersisters.put(meta, new EntityPersisterImpl(val));
        });

    }

    public Session openSession() {
        return new SessionImpl(connectionProvider.getConnection(), entityPersisters);
    }


}
