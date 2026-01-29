package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.jdbc.JdbcExecutorImpl;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.persister.EntityPersister;

import java.util.Map;
import java.util.Properties;

public class SessionFactoryImpl  implements SessionFactory {

    MetadataRegistry metadataRegistry;
    ConnectionProvider connectionProvider;
    Properties properties;

    Map< Class<?>, EntityPersister> entityPersisters;

    public SessionFactoryImpl(
            MetadataRegistry registry,
            Map<Class<?>, EntityPersister> entityPersisters,
            ConnectionProvider connectionProvider,
            Properties properties
    ) {
        this.metadataRegistry = registry;
        this.entityPersisters = entityPersisters;
        this.connectionProvider = connectionProvider;
        this.properties = properties;
    }

    public Session openSession() {
        JdbcExecutor jdbcExecutor = new JdbcExecutorImpl(connectionProvider.getConnection());
        Session session = new SessionImpl(jdbcExecutor, entityPersisters);
        session.begin();
        return session;
    }
}
