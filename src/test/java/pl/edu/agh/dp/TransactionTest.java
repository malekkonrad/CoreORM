package pl.edu.agh.dp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.core.api.Configuration;
import pl.edu.agh.dp.core.api.Orm;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.api.SessionFactory;

import pl.edu.agh.dp.test.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransactionTest {
    String url = System.getenv("DB_URL") != null
            ? System.getenv("DB_URL")
            : "jdbc:h2:./testdb_relationships;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

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
    public void mainTest() {
        config.register(User.class, SuperUser.class, Transaction.class, Transfer.class, Withdraw.class, Support.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        try(Session session = sessionFactory.openSession()) {
            User user = new User();
            user.setName("Rafał");

            session.save(user);

            Transfer transfer = new Transfer();
            transfer.setAmount(new BigDecimal(1000));
            transfer.setDate(LocalDate.now());
            transfer.setUser(user);

            session.save(transfer);
            session.commit();

            Withdraw withdraw = new Withdraw();
            withdraw.setNumber(99);
            withdraw.setDate(LocalDate.now());
            withdraw.setUser(user);

            session.save(withdraw);
            session.commit();

            User newUser = new User();
            newUser.setName("Mateusz");

            session.save(newUser);
            session.commit();

            Transfer newTransfer = new Transfer();
            newTransfer.setAmount(new BigDecimal(200));
            newTransfer.setDate(LocalDate.now());
            newTransfer.setUser(newUser);

            session.save(newTransfer);
            session.commit();

            Withdraw withdraw2 = new Withdraw();
            withdraw2.setNumber(30);
            withdraw2.setDate(LocalDate.now());
            withdraw2.setUser(newUser);

            session.save(withdraw2);
            session.commit();
        }

        try (Session session = sessionFactory.openSession()) {

            List<Transaction> transactions = session.finder(Transaction.class)
//                    .gt("amount", 100)
                    .lte("date", LocalDate.now())
                    .list();

            for (var t : transactions) {
                session.load(t, "user");
            }

            List<User> users = transactions.stream().map(t -> t.getUser()).toList();

            System.out.println(users.size());
//            assertEquals(2, users.size());

            for (var u : users) {
                System.out.println(u.getName());
                assertNotNull(u.getName());
            }

        }
    }
}
