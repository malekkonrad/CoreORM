package pl.edu.agh.dp.core.api;

import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.jdbc.JdbcConnectionProvider;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.ClassPathScanner;
import pl.edu.agh.dp.core.schema.SchemaDropper;
import pl.edu.agh.dp.core.schema.SchemaGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class ConfigurationImpl implements Configuration {

    private final Properties properties = new Properties();
    private final List<Class<?>> entityClasses = new ArrayList<>();

    public Configuration setProperty(String key, String value) {
        properties.setProperty(key, value);
        return this;
    }

    public SessionFactory buildSessionFactory() {
        // 1. Scanning for entities
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Set<Class<?>> foundEntities = ClassPathScanner.scanForEntities(cl);     // FIXME przenieść gdzie indzie??? cl
        entityClasses.addAll(foundEntities);

        // 2. Building registry - inside EntityMetadata are being created
        MetadataRegistry registry = new MetadataRegistry();
        registry.build(entityClasses);

        // 3. Connection to db
        ConnectionProvider cp = new JdbcConnectionProvider(
                properties.getProperty("db.url"),
                properties.getProperty("db.user"),
                properties.getProperty("db.password")
        );

        // 4. Creating schema - TODO add option to not create new db if there is no changes
        String schemaAuto = properties.getProperty("orm.schema.auto", "none");
        if ("drop-create".equalsIgnoreCase(schemaAuto)) {
            new SchemaDropper(cp).drop();   // np. DROP TABLE / DROP SCHEMA
            new SchemaGenerator(registry, cp).generate();
        } else if ("create".equalsIgnoreCase(schemaAuto)) {
            new SchemaGenerator(registry, cp).generate();
        }

        // 5. SessionFactory -> creation of EntityPersisters inside
        return new SessionFactoryImpl(registry, cp, properties);
    }

}
