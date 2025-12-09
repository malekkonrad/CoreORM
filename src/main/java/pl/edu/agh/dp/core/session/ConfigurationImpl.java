package pl.edu.agh.dp.core.session;

import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.jdbc.JdbcConnectionProvider;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.scanner.ClassPathScanner;
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
        // 1. Skanowanie encji
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Set<Class<?>> foundEntities = ClassPathScanner.scanForEntities(cl);     // FIXME przenieść gdzie indzie??? cl
        entityClasses.addAll(foundEntities);

        // 2. Budowa metadanych
        MetadataRegistry registry = new MetadataRegistry();
        registry.build(entityClasses);

        // 3. Przygotowanie połączenia do DB
        ConnectionProvider cp = new JdbcConnectionProvider(
                properties.getProperty("db.url"),
                properties.getProperty("db.user"),
                properties.getProperty("db.password")
        );

        // 4. AUTOMATYCZNE TWORZENIE SCHEMATU – TUTAJ użyty SchemaGenerator
        String schemaAuto = properties.getProperty("orm.schema.auto", "none");
        if ("create".equalsIgnoreCase(schemaAuto)) {
            SchemaGenerator generator = new SchemaGenerator(registry, cp);
            generator.generate();
        }


        // 5. Tworzymy SessionFactory (to zbuduje EntityPersistery itd.)
        return new SessionFactoryImpl(registry, cp, properties);
    }

}
