package pl.edu.agh.dp.core.session;

import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.jdbc.JdbcConnectionProvider;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.scanner.ClassPathScanner;

import java.util.Set;

public class ConfigurationImpl implements Configuration {

    public SessionFactory buildSessionFactory() {

        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // 1. AUTOMATYCZNE skanowanie klas użytkownika
        Set<Class<?>> autoEntities =
                ClassPathScanner.scanForEntities(cl);

        autoEntities.forEach(this::addEntity);

        // 2. Budowa metadanych
        MetadataRegistry registry = new MetadataRegistry();
        registry.build(entityClasses);

        // 3. JDBC + SessionFactory
        ConnectionProvider cp = new JdbcConnectionProvider(
                properties.getProperty("db.url"),
                properties.getProperty("db.user"),
                properties.getProperty("db.password")
        );

        return new SessionFactory(registry, cp, properties);
    }
}
