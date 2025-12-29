package pl.edu.agh.dp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.api.annotations.Table;
import pl.edu.agh.dp.core.exceptions.IntegrityException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BasicTableTest {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BasicTable {
        @Id(autoIncrement = true)
        Long id;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ComplexKeyTable {
        @Id(autoIncrement = false)
        Long id;
        @Id(autoIncrement = false)
        Long id2;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ComplexKeyErrorsTable {
        @Id(autoIncrement = true)
        Long id;
        @Id(autoIncrement = true)
        Long id2;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Table(name = "custom_table_name")
    public static class CustomNameTable {
        @Id(autoIncrement = true)
        Long id;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ColumnPropertiesTable {
        @Id(autoIncrement = true)
        Long id;

        @Column(columnName = "custom_col", nullable = true, unique = true, index = true, length = 100)
        String name;

        @Column(defaultValue = "default_val")
        String status;
    }

    String url = System.getenv("DB_URL") != null
            ? System.getenv("DB_URL")
            : "jdbc:h2:./testdb_basic;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    String user = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "sa";
    String password = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "";

    Configuration config;
    SessionFactory sessionFactory;
    Session session;

    @BeforeEach
    public void setUp() {
        // Reset database
        try (Connection conn = DriverManager.getConnection(url, user, password);
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        config = Orm.configure()
                .setProperty("db.url", url)
                .setProperty("db.user", user)
                .setProperty("db.password", password)
                .setProperty("orm.schema.auto", "create");
    }

    @AfterEach
    public void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void testBasicTablePersistence() {
        config.register(BasicTable.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        BasicTable t = new BasicTable();
        session.save(t);
        session.commit();

        assertNotNull(t.getId());

        BasicTable found = session.find(BasicTable.class, t.getId());
        assertNotNull(found);
        assertEquals(t.getId(), found.getId());
    }

    @Test
    void testCustomTablePersistence() {
        config.register(CustomNameTable.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        CustomNameTable t = new CustomNameTable();
        session.save(t);
        session.commit();

        assertNotNull(t.getId());

        CustomNameTable found = session.find(CustomNameTable.class, t.getId());
        assertNotNull(found);
        assertEquals(t.getId(), found.getId());
    }

    @Test
    void testColumnPropertiesPersistence() {
        config.register(ColumnPropertiesTable.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        ColumnPropertiesTable t = new ColumnPropertiesTable();
        t.setName("test_name");
        // status uses default value logic if not set, but here we set it to verify
        // persistence
        t.setStatus("active");

        session.save(t);
        session.commit();

        ColumnPropertiesTable found = session.find(ColumnPropertiesTable.class, t.getId());
        assertNotNull(found);
        assertEquals("test_name", found.getName());
        assertEquals("active", found.getStatus());
    }

    @Test
    void testComplexKeyTablePersistence() {
        config.register(ComplexKeyTable.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        ComplexKeyTable t = new ComplexKeyTable();
        t.setId(100L);
        t.setId2(200L);

        session.save(t);
        session.commit();

        // Since find(Class, Object) works for single ID, we use findAll for composite
        // or custom join check
        List<ComplexKeyTable> all = session.findAll(ComplexKeyTable.class);
        assertEquals(1, all.size());
        assertEquals(100L, all.get(0).getId());
        assertEquals(200L, all.get(0).getId2());
    }

    @Test
    void testComplexKeyErrors() {
        config.register(ComplexKeyErrorsTable.class);
        // Expect exception when building factory due to metadata validation
        assertThrows(IntegrityException.class, () -> {
            config.buildSessionFactory();
        });
    }
}
