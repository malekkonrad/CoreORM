package pl.edu.agh.dp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.core.api.Configuration;
import pl.edu.agh.dp.core.api.Orm;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.api.SessionFactory;
import pl.edu.agh.dp.core.mapping.InheritanceType;
import pl.edu.agh.dp.core.mapping.annotations.*;
import pl.edu.agh.dp.core.mapping.annotations.Inheritance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for inheritance hierarchies combined with bidirectional relationships.
 * Reproduces the case where an abstract parent class (Transaction) has
 * a @ManyToOne
 * relationship and a concrete subclass (Transfer) inherits it.
 */
public class InheritanceRelationshipTest {

    // ---- Single Table strategy ----

    @Getter
    @Setter
    @NoArgsConstructor
    // @Inheritance(strategy = InheritanceType.__OLD_SINGLE)
    public static class STUser {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @OneToMany
        @JoinColumn(nullable = true)
        List<STTransaction> transactions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static abstract class STTransaction {
        @Id(autoIncrement = true)
        Long id;
        LocalDate date;

        @ManyToOne
        @JoinColumn(nullable = true)
        STUser user;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class STTransfer extends STTransaction {
        BigDecimal amount;
    }

    // ---- Concrete (SINGLE_TABLE / default) strategy ----

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CTUser {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @OneToMany
        @JoinColumn(nullable = true)
        List<CTTransaction> transactions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static abstract class CTTransaction {
        @Id(autoIncrement = true)
        Long id;
        LocalDate date;

        @ManyToOne
        @JoinColumn(nullable = true)
        CTUser user;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CTTransfer extends CTTransaction {
        BigDecimal amount;
    }

    String url = System.getenv("DB_URL") != null
            ? System.getenv("DB_URL")
            : "jdbc:h2:./testdb_inh_rel;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    String user = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "sa";
    String password = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "";

    Configuration config;
    SessionFactory sessionFactory;
    Session session;

    Connection conn;
    Statement stmt;

    @BeforeEach
    public void setUp() {
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
    void testSingleTableInheritanceWithRelationship() {
        config.register(STUser.class, STTransaction.class, STTransfer.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        STUser u = new STUser();
        u.setName("Alice");
        u.setTransactions(new ArrayList<>());

        STTransfer t = new STTransfer();
        t.setDate(LocalDate.now());
        t.setAmount(new BigDecimal("100.00"));
        t.setUser(u);

        session.save(t);
        session.commit();

        assertNotNull(t.getId());
        assertNotNull(u.getId());
    }

    @Test
    void testConcreteInheritanceWithRelationship() {
        config.register(CTUser.class, CTTransaction.class, CTTransfer.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        CTUser u = new CTUser();
        u.setName("Bob");
        u.setTransactions(new ArrayList<>());

        CTTransfer t = new CTTransfer();
        t.setDate(LocalDate.now());
        t.setAmount(new BigDecimal("200.00"));
        t.setUser(u);

        session.save(t);
        session.commit();

        assertNotNull(t.getId());
        assertNotNull(u.getId());
    }
}
