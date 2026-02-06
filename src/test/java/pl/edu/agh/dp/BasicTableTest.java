package pl.edu.agh.dp;

import lombok.AllArgsConstructor;
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
import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.api.annotations.Table;
import pl.edu.agh.dp.core.exceptions.IntegrityException;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BasicTableTest {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BasicTable {
        // list of all supported types
        @Id(autoIncrement = true)
        Long id;
        Integer anInteger;
        Long aLong;
        Short aShort;
        String aString;
        Float aFloat;
        Double aDouble;
        Boolean aBoolean;
        BigDecimal aBigDecimal;
        LocalDate aDate;
        LocalTime aTime;
        LocalDateTime aDateTime;
        OffsetDateTime aOffsetDateTime;
        UUID uuid;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BasicTableWithColumnAnnotations {
        @Id(autoIncrement = true)
        Long id;
        @Column(columnName = "anINTEGER", nullable = true, unique = true, defaultValue = "5")
        Integer anInteger;
        @Column(columnName = "aLONG", nullable = true, unique = true, defaultValue = "6")
        Long aLong;
        @Column(columnName = "aSHORT", nullable = true, unique = true, defaultValue = "7")
        Short aShort;
        @Column(columnName = "aSTRING", nullable = true, unique = true, defaultValue = "8", length = 10)
        String aString;
        @Column(columnName = "aFLOAT", nullable = true, unique = true, defaultValue = "9")
        Float aFloat;
        @Column(columnName = "aDOUBLE", nullable = true, unique = true, defaultValue = "10")
        Double aDouble;
        @Column(columnName = "aBOOLEAN", nullable = true, unique = true, defaultValue = "false")
        Boolean aBoolean;
        @Column(columnName = "aBIGDECIMAL", nullable = true, unique = true, defaultValue = "11")
        BigDecimal aBigDecimal;
        @Column(columnName = "aDATE", nullable = true, unique = true, defaultValue = "2026-01-01")
        LocalDate aDate;
        @Column(columnName = "aTIME", nullable = true, unique = true, defaultValue = "10:42:20.064703900")
        LocalTime aTime;
        @Column(columnName = "aDATETIME", nullable = true, unique = true, defaultValue = "2026-01-01T10:42:20.064703900")
        LocalDateTime aDateTime;
        @Column(columnName = "aOFFSETDATETIME", nullable = true, unique = true, defaultValue = "2026-01-01T10:44:25.571050200+01:00")
        OffsetDateTime aOffsetDateTime;
        @Column(columnName = "UUID", nullable = true, unique = true, defaultValue = "4dfda391-40c9-4782-ab3d-32c7a999775f")
        UUID uuid;
    }

    @Setter
    @Getter
    @AllArgsConstructor
    public static class ComplexKey {
        Long id;
        Long id2;
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
        @Id(autoIncrement = false)
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

    Connection conn;
    Statement stmt;

    @BeforeEach
    public void setUp() {
        // Reset database
        try {
            conn = DriverManager.getConnection(url, user, password);
            stmt = conn.createStatement();
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
        t.setAnInteger(10);
        t.setABigDecimal(new BigDecimal("10"));
        t.setADate(LocalDate.now());
        t.setABoolean(true);
        t.setADateTime(LocalDateTime.now());
        t.setADouble(10.0);
        t.setAFloat(10.0f);
        t.setALong(10L);
        t.setAShort((short) 10);
        t.setAString("test");
        t.setATime(LocalTime.now());
        t.setABoolean(true);
        t.setUuid(UUID.randomUUID());
        t.setAOffsetDateTime(OffsetDateTime.now());
        session.save(t);
        session.commit();

        assertNotNull(t.getId());

        BasicTable found = session.find(BasicTable.class, t.getId());
        assertNotNull(found);
        assertEquals(t.getId(), found.getId());
    }

    @Test
    void testBasicTableColumnValidation() {
        config.register(BasicTableWithColumnAnnotations.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        BasicTableWithColumnAnnotations t = new BasicTableWithColumnAnnotations();
        // the rest is set to defaults
//        t.setId(1L);
        session.save(t);
        session.commit();

        assertNotNull(t.getId());

        BasicTableWithColumnAnnotations found = session.find(BasicTableWithColumnAnnotations.class, t.getId());
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

        // we can select from this table
        assertDoesNotThrow(() -> {
            stmt.executeQuery("SELECT * from custom_table_name");
        });

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

        session.save(t);
        session.commit();
        // TODO update this entity on commit
        // assertEquals("default_val", t.getStatus());
        session.close();
        session = sessionFactory.openSession();

        ColumnPropertiesTable found = session.find(ColumnPropertiesTable.class, t.getId());
        assertNotNull(found);
        assertEquals("test_name", found.getName());
        // status default to the value set in the table class
        assertEquals("default_val", found.getStatus());
    }

    @Test
    void testComplexKeyTablePersistence() {
        config.register(ComplexKeyTable.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        ComplexKeyTable t = new ComplexKeyTable();
        t.setId(100L);
        // only one of the keys is set
        session.save(t);
        assertThrowsExactly(IntegrityException.class, () -> {
            session.commit();
        });
        // both keys are set
        t.setId2(200L);
        session.save(t);
        session.commit();
        // test getting the table
        ComplexKey complexKey = new ComplexKey(100L, 200L);
        ComplexKeyTable found = session.find(ComplexKeyTable.class, complexKey);
        assertNotNull(found);
        assertEquals(t.getId(), found.getId());
        assertEquals(t.getId2(), found.getId2());
    }

    @Test
    void testComplexKeyErrors() {
        config.register(ComplexKeyErrorsTable.class);
        // Complex key cannot be set to autoincrement
        assertThrows(IntegrityException.class, () -> {
            config.buildSessionFactory();
        });
    }
}
